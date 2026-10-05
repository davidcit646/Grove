use serde_json::Value;

pub(crate) fn validate_config(json: &str) -> Result<(), &'static str> {
    if json.len() > 65_536 {
        return Err("Configuration exceeds 64 KB");
    }
    let root: Value = serde_json::from_str(json).map_err(|_| "Invalid JSON")?;
    let version = root
        .get("version")
        .and_then(Value::as_i64)
        .ok_or("Missing configuration version")?;
    if !(1..=8).contains(&version) {
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
        (
            "search",
            &[
                "contacts",
                "files",
                "contactIndexing",
                "fileIndexing",
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
