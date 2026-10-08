mod policy;
mod calculator;
mod bridge;
mod config;
mod mime;
mod search;
mod wallpaper;

use config::validate_config;
use mime::classify;
use search::{edit_distance_at_most, is_java_space, score_label, top_indices};
use wallpaper::render_wallpaper;

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
    fn search_provider_schema_matches_kotlin_validation() {
        for mask in 0..8 {
            let json = format!(r#"{{"version":12,"wallpaper":"solid-black","favorites":[],"themeMode":"system","search":{{"calculator":{},"androidSettings":{},"groveSettings":{}}}}}"#,
                mask & 1 != 0, mask & 2 != 0, mask & 4 != 0);
            assert!(validate_config(&json).is_ok());
        }
        for field in ["calculator", "androidSettings", "groveSettings"] {
            for value in ["1", "null", "\"true\"", "{}"] {
                let json = format!(r#"{{"version":12,"wallpaper":"solid-black","favorites":[],"themeMode":"system","search":{{"{}":{}}}}}"#, field, value);
                assert!(validate_config(&json).is_err());
            }
        }
        assert!(validate_config(r#"{"version":11,"wallpaper":"solid-black","favorites":[],"themeMode":"system"}"#).is_ok());
        assert!(validate_config(r#"{"version":13,"wallpaper":"solid-black","favorites":[],"themeMode":"system"}"#).is_err());
    }

    #[test]
    fn theme_schema_accepts_all_modes_and_rejects_invalid_values() {
        for mode in ["system", "light", "dark", "wallpaper"] {
            let json = format!(r#"{{"version":10,"wallpaper":"solid-black","favorites":[],"themeMode":"{}"}}"#, mode);
            assert!(validate_config(&json).is_ok());
        }
        for value in ["null", "42", "\"unknown\""] {
            let json = format!(r#"{{"version":10,"wallpaper":"solid-black","favorites":[],"themeMode":{}}}"#, value);
            assert!(validate_config(&json).is_err());
        }
        assert!(validate_config(r#"{"version":10,"wallpaper":"solid-black","favorites":[]}"#).is_err());
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
    fn grids_validate_types_bounds_and_legacy() {
        for columns in 1..=10 {
            for rows in 1..=10 {
                let json = format!(r#"{{"version":11,"wallpaper":"solid-black","favorites":[],"themeMode":"system","homeGrid":{{"columns":{},"rows":{}}}}}"#, columns, rows);
                assert!(validate_config(&json).is_ok());
            }
        }
        for value in ["0", "11", "1.5", "\"4\"", "true", "null"] {
            let json = format!(r#"{{"version":11,"wallpaper":"solid-black","favorites":[],"themeMode":"system","drawerGrid":{{"columns":{},"rows":2}}}}"#, value);
            assert!(validate_config(&json).is_err());
        }
    }

    #[test]
    fn legacy_configuration_ignores_retired_fields() {
        // Historical input must remain readable; this field has no active meaning.
        for version in 1..=12 {
            for value in ["true", "false", "\"false\"", "null"] {
                let wallpaper = if version >= 9 { "\"grove-fern\"" } else { "0" };
                let json = format!(r#"{{"version":{},"wallpaper":{},"themeMode":"dark","favorites":["example.app/.Main"],"homeScreen":{{"showPinnedAppsHint":{},"showPinnedApps":false,"showClock":false}}}}"#, version, wallpaper, value);
                assert!(validate_config(&json).is_ok());
            }
        }
    }

    #[test]
    fn categories_and_configs() {
        assert_eq!(classify("m4a"), Some(("audio/mp4", "Audio")));
        assert_eq!(classify("mkv"), Some(("video/x-matroska", "Videos")));
        assert_eq!(classify("csv"), Some(("text/csv", "Documents")));
        assert_eq!(classify("jpg"), None);
        assert!(validate_config(r#"{"version":4,"wallpaper":0,"favorites":[],"homeScreen":{"showPinnedApps":false}}"#).is_ok());
        assert!(validate_config(r#"{"version":4,"wallpaper":0,"favorites":[],"homeScreen":{"showPinnedApps":"false"}}"#).is_err());
        assert!(validate_config(r#"{"version":6,"wallpaper":12,"favorites":[],"homeScreen":{"useWallpaperButtonColors":true},"folders":[{"name":"Work","apps":["example/.Main"]}]}"#).is_ok());
        assert!(validate_config(r#"{"version":6,"wallpaper":0,"favorites":[],"folders":[{"name":"Bad","apps":["invalid"]}]}"#).is_err());
        assert!(validate_config(r#"{"version":7,"wallpaper":0,"favorites":[],"search":{"contacts":false,"files":true}}"#).is_ok());
        assert!(validate_config(r#"{"version":7,"wallpaper":0,"favorites":[],"search":{"files":"true"}}"#).is_err());
        assert!(validate_config(r#"{"version":8,"wallpaper":0,"favorites":[],"search":{"contacts":true,"files":true,"contactIndexing":true,"fileIndexing":false}}"#).is_ok());
        assert!(validate_config(r#"{"version":8,"wallpaper":0,"favorites":[],"search":{"contactIndexing":"true"}}"#).is_err());
        assert!(validate_config(r#"{"version":8,"wallpaper":0,"favorites":[],"search":{"fileIndexing":1}}"#).is_err());
        assert!(validate_config(r#"{"version":8,"wallpaper":13,"favorites":[]}"#).is_ok());
        assert!(validate_config(r#"{"version":8,"wallpaper":14,"favorites":[]}"#).is_ok());
        assert!(validate_config(r#"{"version":8,"wallpaper":15,"favorites":[]}"#).is_err());
        assert!(validate_config(r#"{"version":9,"wallpaper":"grove-fern","favorites":[]}"#).is_ok());
        assert!(validate_config(r#"{"version":9,"wallpaper":"solid-black","favorites":[]}"#).is_ok());
        assert!(validate_config(r#"{"version":9,"wallpaper":"custom-image","favorites":[]}"#).is_ok());
        assert!(validate_config(r#"{"version":9,"wallpaper":"missing","favorites":[]}"#).is_err());
        assert!(validate_config(r#"{"version":9,"wallpaper":0,"favorites":[]}"#).is_err());
        assert!(validate_config(r#"{"version":11,"wallpaper":"grove-fern","favorites":[]}"#).is_err());
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

mod image_policy;
mod decisions;
mod config_edits;

mod gesture_session;
