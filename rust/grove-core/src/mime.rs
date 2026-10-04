/// Grove's own extension table: cases Android's MimeTypeMap misses or gets
/// wrong, plus their search categories. Unknown extensions return None and
/// Kotlin falls back to MimeTypeMap.
pub(crate) fn classify(extension: &str) -> Option<(&'static str, &'static str)> {
    Some(match extension {
        "m4a" => ("audio/mp4", "Audio"),
        "csv" => ("text/csv", "Documents"),
        "mkv" => ("video/x-matroska", "Videos"),
        "opus" => ("audio/ogg", "Audio"),
        "weba" => ("audio/webm", "Audio"),
        _ => return None,
    })
}

