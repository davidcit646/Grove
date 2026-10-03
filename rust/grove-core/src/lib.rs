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
        assert!(validate_config(r#"{"version":7,"wallpaper":0,"favorites":[],"search":{"contacts":false,"files":true}}"#).is_ok());
        assert!(validate_config(r#"{"version":7,"wallpaper":0,"favorites":[],"search":{"files":"true"}}"#).is_err());
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
