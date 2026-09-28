use jni::objects::{JObject, JObjectArray, JString};
use jni::sys::{jint, jintArray, jobjectArray, jstring};
use jni::JNIEnv;
use serde_json::Value;
use std::cmp::Ordering;

/// Java's \s without UNICODE_CHARACTER_CLASS: ASCII whitespace only.
/// Grove's Kotlin side splits on this set, so Rust must match it exactly.
fn is_java_space(c: char) -> bool {
    matches!(c, ' ' | '\t' | '\n' | '\x0B' | '\x0C' | '\r')
}

fn cmp_index(scores: &[i32], a: usize, b: usize) -> Ordering {
    scores[b].cmp(&scores[a]).then_with(|| a.cmp(&b))
}

/// Winning indices in final order: score descending, index ascending.
/// Mirrors the PriorityQueue selection in CoreBridge's Kotlin fallback exactly,
/// including the "earliest index wins ties" rule.
fn top_indices(scores: &[i32], limit: usize) -> Vec<i32> {
    let mut idx: Vec<usize> = (0..scores.len()).filter(|&i| scores[i] >= 0).collect();
    let keep = limit.min(idx.len());
    if keep < idx.len() {
        idx.select_nth_unstable_by(keep, |&a, &b| cmp_index(scores, a, b));
        idx.truncate(keep);
    }
    idx.sort_by(|&a, &b| cmp_index(scores, a, b));
    idx.into_iter().map(|i| i as i32).collect()
}

fn score_label(label: &str, query_text: &str, terms: &[&str]) -> i32 {
    if terms.is_empty() {
        return 0;
    }
    let ok = terms.iter().all(|term| {
        // Length gates count UTF-16 code units, exactly like Kotlin's String.length.
        let term_units: Vec<u16> = term.encode_utf16().collect();
        label.contains(term)
            || (term_units.len() >= 3
                && label.split(is_java_space).any(|word| {
                    let word_units: Vec<u16> = word.encode_utf16().collect();
                    word_units.len() >= 3
                        && edit_distance_at_most(
                            &word_units,
                            &term_units,
                            if term_units.len() >= 6 { 2 } else { 1 },
                        )
                }))
    });
    if !ok {
        return -1;
    }
    if label == query_text {
        3
    } else if label.starts_with(query_text) {
        2
    } else {
        1
    }
}

/// Bounded Levenshtein over UTF-16 code units — not Unicode scalars.
/// This is what makes the native path bit-identical to Kotlin's
/// editDistanceAtMost, so the old non-ASCII Kotlin fallback is unnecessary.
fn edit_distance_at_most(left: &[u16], right: &[u16], max: usize) -> bool {
    // A malicious app label should not make one search allocate or compute an
    // unbounded edit-distance matrix. Exact substring matches still work.
    if left.len() > 64 || right.len() > 64 {
        return false;
    }
    if left.len().abs_diff(right.len()) > max {
        return false;
    }
    let mut previous: Vec<usize> = (0..=right.len()).collect();
    let mut current = vec![0usize; right.len() + 1];
    for (i, &unit) in left.iter().enumerate() {
        current[0] = i + 1;
        for (j, &other) in right.iter().enumerate() {
            current[j + 1] = (current[j] + 1)
                .min(previous[j + 1] + 1)
                .min(previous[j] + usize::from(unit != other));
        }
        std::mem::swap(&mut previous, &mut current);
    }
    previous[right.len()] <= max
}

/// Grove's own extension table: cases Android's MimeTypeMap misses or gets
/// wrong, plus their search categories. Unknown extensions return None and
/// Kotlin falls back to MimeTypeMap.
fn classify(extension: &str) -> Option<(&'static str, &'static str)> {
    Some(match extension {
        "m4a" => ("audio/mp4", "Audio"),
        "csv" => ("text/csv", "Documents"),
        "mkv" => ("video/x-matroska", "Videos"),
        "opus" => ("audio/ogg", "Audio"),
        "weba" => ("audio/webm", "Audio"),
        _ => return None,
    })
}

fn validate_config(json: &str) -> Result<(), &'static str> {
    if json.len() > 65_536 {
        return Err("Configuration exceeds 64 KB");
    }
    let root: Value = serde_json::from_str(json).map_err(|_| "Invalid JSON")?;
    let version = root
        .get("version")
        .and_then(Value::as_i64)
        .ok_or("Missing configuration version")?;
    if !(1..=6).contains(&version) {
        return Err("Unsupported configuration version");
    }
    let wallpaper = root
        .get("wallpaper")
        .and_then(Value::as_i64)
        .ok_or("Missing wallpaper selection")?;
    if !(0..=12).contains(&wallpaper) {
        return Err("Wallpaper selection is invalid");
    }
    let favorites = root
        .get("favorites")
        .and_then(Value::as_array)
        .ok_or("Invalid favorites")?;
    if favorites.len() > 100 {
        return Err("Too many favorites");
    }
    for entry in favorites {
        let name = entry.as_str().ok_or("Invalid app identifier")?;
        if !(3..=512).contains(&name.encode_utf16().count()) || !name.contains('/') {
            return Err("Invalid app identifier");
        }
    }
    for (section, keys) in [
        (
            "gestures",
            &[
                "swipeDownSearch",
                "swipeUpAppDrawer",
                "tapHomeContextMenu",
                "longPressHomeContextMenu",
            ][..],
        ),
        (
            "homeScreen",
            &[
                "showAppsButton",
                "showSearchButton",
                "showClock",
                "showPinnedApps",
                "showPinnedAppsHint",
                "pinnedAppsAtBottom",
                "useWallpaperButtonColors",
            ][..],
        ),
    ] {
        if let Some(value) = root.get(section) {
            let map = value
                .as_object()
                .ok_or("Configuration section must be an object")?;
            for key in keys {
                if let Some(flag) = map.get(*key) {
                    if !flag.is_boolean() {
                        return Err("Configuration setting must be true or false");
                    }
                }
            }
        }
    }
    if let Some(folders) = root.get("folders") {
        let folders = folders.as_array().ok_or("Invalid folders")?;
        if folders.len() > 100 {
            return Err("Too many folders");
        }
        for folder in folders {
            let name = folder
                .get("name")
                .and_then(Value::as_str)
                .ok_or("Invalid folder name")?;
            if !(1..=40).contains(&name.trim().encode_utf16().count()) {
                return Err("Invalid folder name");
            }
            let members = folder
                .get("apps")
                .and_then(Value::as_array)
                .ok_or("Invalid folder apps")?;
            if members.len() > 500 {
                return Err("Too many folder apps");
            }
            for member in members {
                let key = member.as_str().ok_or("Invalid folder app")?;
                if !(3..=512).contains(&key.encode_utf16().count()) || !key.contains('/') {
                    return Err("Invalid folder app");
                }
            }
        }
    }
    Ok(())
}

// --- Procedural wallpaper renderer ----------------------------------------

const WALLPAPER_COLORS: [[u32; 3]; 3] = [
    // Style 0 -> green, style 1 -> orange, style 2 -> purple
    // (mirrors WallpaperArt.createCanvas palette selection)
    [0xff9ab095, 0xff416e60, 0xff142f30],
    [0xffe5b886, 0xffa25440, 0xff263c37],
    [0xff666788, 0xffc19a98, 0xff18343b],
];

fn argb_to_f(c: u32) -> [f32; 4] {
    [
        ((c >> 24) & 0xff) as f32 / 255.0,
        ((c >> 16) & 0xff) as f32 / 255.0,
        ((c >> 8) & 0xff) as f32 / 255.0,
        (c & 0xff) as f32 / 255.0,
    ]
}

fn f_to_argb(px: [f32; 4]) -> u32 {
    let q = |v: f32| (v.clamp(0.0, 1.0) * 255.0).round() as u32;
    (q(px[0]) << 24) | (q(px[1]) << 16) | (q(px[2]) << 8) | q(px[3])
}

/// SRC_OVER blend; the destination is always opaque here, so alpha stays 1.
fn blend_over(src: u32, dst: [f32; 4]) -> [f32; 4] {
    let s = argb_to_f(src);
    let inv = 1.0 - s[0];
    [
        1.0,
        s[1] * s[0] + dst[1] * inv,
        s[2] * s[0] + dst[2] * inv,
        s[3] * s[0] + dst[3] * inv,
    ]
}

fn lerp_color(a: u32, b: u32, t: f32) -> [f32; 4] {
    let fa = argb_to_f(a);
    let fb = argb_to_f(b);
    [
        1.0,
        fa[1] + (fb[1] - fa[1]) * t,
        fa[2] + (fb[2] - fa[2]) * t,
        fa[3] + (fb[3] - fa[3]) * t,
    ]
}

/// Diagonal gradient matching Android's LinearGradient(0,0 -> w,h)
/// with three evenly distributed color stops.
fn gradient_at(colors: &[u32; 3], x: f32, y: f32, w: f32, h: f32) -> [f32; 4] {
    let t = ((x * w + y * h) / (w * w + h * h)).clamp(0.0, 1.0);
    if t < 0.5 {
        lerp_color(colors[0], colors[1], t * 2.0)
    } else {
        lerp_color(colors[1], colors[2], (t - 0.5) * 2.0)
    }
}

/// Renders one of Grove's three generative wallpaper styles to ARGB pixels.
/// Pure software rasterizer: no Android graphics APIs involved.
fn render_wallpaper(style: usize, width: usize, height: usize) -> Vec<u32> {
    // Mirrors WallpaperArt.createCanvas: style 1 and 2 pick their palettes,
    // everything else falls back to style 0's; mountains draw for any nonzero.
    let colors = &WALLPAPER_COLORS[match style {
        1 => 1,
        2 => 2,
        _ => 0,
    }];
    let w = width as f32;
    let h = height as f32;
    let mut out = Vec::with_capacity(width * height);

    // Precompute the mountain polygons for styles 1 and 2.
    let mountains: Vec<Vec<(f32, f32)>> = if style == 0 {
        Vec::new()
    } else {
        (0..4)
            .map(|i| {
                let i = i as f32;
                let y = 1150.0 + i * 270.0;
                vec![
                    (-100.0, y + 400.0),
                    (260.0, y + 160.0),
                    (570.0, y - 180.0),
                    (840.0, y + 140.0),
                    (1180.0, y + 50.0),
                    (1180.0, 2400.0),
                    (-100.0, 2400.0),
                ]
            })
            .collect()
    };
    let mountain_colors: Vec<u32> = (0..4)
        .map(|i| {
            let (r, g, b) = (55 - i * 10, 78 - i * 12, 76 - i * 10);
            0xff000000 | ((r as u32) << 16) | ((g as u32) << 8) | (b as u32)
        })
        .collect();

    // Scanline spans for the mountains, computed once per row.
    let spans: Vec<Vec<(i32, i32, u32)>> = (0..height)
        .map(|row| {
            let y = row as f32 + 0.5;
            let mut row_spans = Vec::new();
            for (poly, &color) in mountains.iter().zip(mountain_colors.iter()) {
                let mut xs = Vec::new();
                for e in 0..poly.len() {
                    let (x1, y1) = poly[e];
                    let (x2, y2) = poly[(e + 1) % poly.len()];
                    if (y1 <= y && y < y2) || (y2 <= y && y < y1) {
                        xs.push(x1 + (y - y1) / (y2 - y1) * (x2 - x1));
                    }
                }
                xs.sort_by(|a, b| a.partial_cmp(b).unwrap_or(Ordering::Equal));
                for pair in xs.chunks_exact(2) {
                    row_spans.push((pair[0].floor() as i32, pair[1].ceil() as i32, color));
                }
            }
            row_spans
        })
        .collect();

    for row in 0..height {
        let y = row as f32 + 0.5;
        for col in 0..width {
            let x = col as f32 + 0.5;
            let mut px = gradient_at(colors, x, y, w, h);

            // Soft accent circle.
            let dx = x - 800.0;
            let dy = y - 740.0;
            if dx * dx + dy * dy <= 180.0 * 180.0 {
                px = blend_over(0x44fff1cc, px);
            }

            if style == 0 {
                // Six translucent ellipses, back to front.
                for i in 0..6 {
                    let i = i as f32;
                    let left = -500.0 + i * 100.0;
                    let top = 1100.0 + i * 150.0;
                    let right = 1600.0;
                    let bottom = 2900.0 + i * 130.0;
                    let cx = (left + right) / 2.0;
                    let cy = (top + bottom) / 2.0;
                    let rx = (right - left) / 2.0;
                    let ry = (bottom - top) / 2.0;
                    let ex = (x - cx) / rx;
                    let ey = (y - cy) / ry;
                    if ex * ex + ey * ey <= 1.0 {
                        let a = 45 + i as u32 * 20;
                        px = blend_over((a << 24) | (14 << 16) | (50 << 8) | 40, px);
                    }
                }
            } else {
                // Later mountains draw on top of earlier ones: the last
                // matching span wins.
                for &(x0, x1, color) in spans[row].iter().rev() {
                    if (col as i32) >= x0 && (col as i32) < x1 {
                        px = blend_over(color, px);
                        break;
                    }
                }
            }

            out.push(f_to_argb(px));
        }
    }
    out
}

// --- JNI -------------------------------------------------------------------

fn java_strings(env: &mut JNIEnv, array: &JObjectArray) -> jni::errors::Result<Vec<String>> {
    let count = env.get_array_length(array)?;
    let mut strings = Vec::with_capacity(count as usize);
    for i in 0..count {
        let value = env.get_object_array_element(array, i)?;
        let string = JString::from(value);
        let text: String = env.get_string(&string)?.into();
        env.delete_local_ref(string)?;
        strings.push(text);
    }
    Ok(strings)
}

/// Score every label against the prepared query and return the winning
/// indices in final order. One JNI call per search; no score array crosses.
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_searchNative(
    mut env: JNIEnv,
    _this: JObject,
    labels: JObjectArray,
    query: JString,
    limit: jint,
) -> jintArray {
    let result = (|| -> jni::errors::Result<_> {
        let labels = java_strings(&mut env, &labels)?;
        let query: String = env.get_string(&query)?.into();
        let terms: Vec<&str> = query
            .split(is_java_space)
            .filter(|t| !t.is_empty())
            .collect();
        let scores: Vec<i32> = labels
            .iter()
            .map(|label| score_label(label, &query, &terms))
            .collect();
        let order = top_indices(&scores, limit.max(0) as usize);
        let out = env.new_int_array(order.len() as i32)?;
        env.set_int_array_region(&out, 0, &order)?;
        Ok(out.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

/// Classify file extensions Grove knows about. Returns "mime|category"
/// per extension, or an empty string when unknown (Kotlin then tries
/// Android's MimeTypeMap).
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_classifyNative(
    mut env: JNIEnv,
    _this: JObject,
    extensions: JObjectArray,
) -> jobjectArray {
    let result = (|| -> jni::errors::Result<_> {
        let extensions = java_strings(&mut env, &extensions)?;
        let out =
            env.new_object_array(extensions.len() as i32, "java/lang/String", JObject::null())?;
        for (i, ext) in extensions.iter().enumerate() {
            let packed = match classify(ext) {
                Some((mime, cat)) => format!("{mime}|{cat}"),
                None => String::new(),
            };
            let value = env.new_string(packed)?;
            env.set_object_array_element(&out, i as i32, &value)?;
            env.delete_local_ref(value)?;
        }
        Ok(out.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

/// Render a generative wallpaper style to ARGB pixels (row-major).
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_renderWallpaperNative(
    env: JNIEnv,
    _this: JObject,
    style: jint,
    width: jint,
    height: jint,
) -> jintArray {
    let result = (|| -> jni::errors::Result<_> {
        let (w, h) = (width.max(1) as usize, height.max(1) as usize);
        let pixels: Vec<i32> = render_wallpaper(style.max(0) as usize, w, h)
            .into_iter()
            .map(|p| p as i32)
            .collect();
        let out = env.new_int_array(pixels.len() as i32)?;
        env.set_int_array_region(&out, 0, &pixels)?;
        Ok(out.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_configErrorNative(
    mut env: JNIEnv,
    _this: JObject,
    json: JString,
) -> jstring {
    let result = (|| -> jni::errors::Result<_> {
        let json: String = env.get_string(&json)?.into();
        let error = validate_config(&json).err().unwrap_or("");
        Ok(env.new_string(error)?.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn score(label: &str, query: &str) -> i32 {
        let terms: Vec<&str> = query
            .split(is_java_space)
            .filter(|t| !t.is_empty())
            .collect();
        score_label(label, query, &terms)
    }

    #[test]
    fn ranking_preserves_existing_search_order() {
        assert_eq!(score("maps", "maps"), 3);
        assert_eq!(score("maps go", "maps"), 2);
        assert_eq!(score("google maps", "maps"), 1);
        assert_eq!(score("calculator", "calculatr"), 1);
        assert_eq!(score("calculator", "calendar"), -1);
    }

    #[test]
    fn top_k_matches_kotlin_priority_queue_selection() {
        // Ties keep the earliest index; exact > prefix > fuzzy.
        let labels = ["google maps", "maps go", "maps", "maps lite", "calendar"];
        let scores: Vec<i32> = labels.iter().map(|l| score(l, "maps")).collect();
        assert_eq!(scores, vec![1, 2, 3, 2, -1]);
        assert_eq!(top_indices(&scores, 2), vec![2, 1]);
        assert_eq!(top_indices(&scores, 10), vec![2, 1, 3, 0]);
        assert_eq!(top_indices(&scores, 0), Vec::<i32>::new());
    }

    #[test]
    fn top_k_large_scale_keeps_earliest_equal_scores() {
        let scores: Vec<i32> = vec![1; 20_000];
        let order = top_indices(&scores, 12);
        assert_eq!(order, (0..12).map(|i| i as i32).collect::<Vec<_>>());
    }

    #[test]
    fn utf16_edit_distance_matches_kotlin_for_non_ascii() {
        // "café" vs "cafe": é is one UTF-16 unit; distance 1, like Kotlin.
        assert!(edit_distance_at_most(
            &"café".encode_utf16().collect::<Vec<_>>(),
            &"cafe".encode_utf16().collect::<Vec<_>>(),
            1
        ));
        // Appending an emoji costs 2 (a surrogate pair), exactly as Kotlin's
        // UTF-16-unit DP — scalar counting would say 1.
        assert!(!edit_distance_at_most(
            &"a".encode_utf16().collect::<Vec<_>>(),
            &"a\u{1F600}".encode_utf16().collect::<Vec<_>>(),
            1
        ));
        assert!(edit_distance_at_most(
            &"a".encode_utf16().collect::<Vec<_>>(),
            &"a\u{1F600}".encode_utf16().collect::<Vec<_>>(),
            2
        ));
        // Replacing one emoji with another costs 1: the high surrogates match.
        assert!(edit_distance_at_most(
            &"a\u{1F600}".encode_utf16().collect::<Vec<_>>(),
            &"a\u{1F603}".encode_utf16().collect::<Vec<_>>(),
            1
        ));
    }

    #[test]
    fn categories_and_configs() {
        assert_eq!(classify("m4a"), Some(("audio/mp4", "Audio")));
        assert_eq!(classify("mkv"), Some(("video/x-matroska", "Videos")));
        assert_eq!(classify("csv"), Some(("text/csv", "Documents")));
        assert_eq!(classify("jpg"), None);
        assert!(validate_config(r#"{"version":4,"wallpaper":0,"favorites":[],"homeScreen":{"showPinnedAppsHint":false}}"#).is_ok());
        assert!(validate_config(r#"{"version":4,"wallpaper":0,"favorites":[],"homeScreen":{"showPinnedAppsHint":"false"}}"#).is_err());
        assert!(validate_config(r#"{"version":6,"wallpaper":12,"favorites":[],"homeScreen":{"useWallpaperButtonColors":true},"folders":[{"name":"Work","apps":["example/.Main"]}]}"#).is_ok());
        assert!(validate_config(r#"{"version":6,"wallpaper":0,"favorites":[],"folders":[{"name":"Bad","apps":["invalid"]}]}"#).is_err());
    }

    #[test]
    fn wallpaper_renders_opaque_pixels_at_expected_size() {
        for style in 0..3 {
            let px = render_wallpaper(style, 64, 48);
            assert_eq!(px.len(), 64 * 48);
            // Every pixel fully opaque: the gradient base is opaque and every
            // shape blends over it.
            assert!(px.iter().all(|p| p & 0xff000000 == 0xff000000));
        }
        // Styles differ from each other.
        let a = render_wallpaper(0, 64, 48);
        let b = render_wallpaper(1, 64, 48);
        assert_ne!(a, b);
    }
}
