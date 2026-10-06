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
    if !(1..=11).contains(&version) {
        return Err("Unsupported configuration version");
    }
    if version >= 11 {
        for key in ["homeGrid", "drawerGrid"] {
            if let Some(grid) = root.get(key).filter(|v| !v.is_null()) {
                let map = grid.as_object().ok_or("Grid must be an object")?;
                for dimension in ["columns", "rows"] {
                    let size = map.get(dimension).and_then(Value::as_i64).ok_or("Grid must use integers")?;
                    if !(1..=10).contains(&size) { return Err("Grid must be 1–10"); }
                }
            }
        }
    }
    if version >= 10 {
        match root.get("themeMode").and_then(Value::as_str) {
            Some("system" | "light" | "dark" | "wallpaper") => {},
            _ => return Err("Unknown theme mode"),
        }
    }
    if version >= 9 {
        let wallpaper = root
            .get("wallpaper")
            .and_then(Value::as_str)
            .ok_or("Missing wallpaper selection")?;
        if !matches!(
            wallpaper,
            "grove-fern"
                | "grove-ember"
                | "grove-dusk"
                | "commons-0"
                | "commons-1"
                | "commons-2"
                | "commons-3"
                | "commons-4"
                | "commons-5"
                | "commons-6"
                | "commons-7"
                | "commons-8"
                | "commons-9"
                | "solid-black"
                | "custom-image"
        ) {
            return Err("Wallpaper selection is invalid");
        }
    } else {
        let wallpaper = root
            .get("wallpaper")
            .and_then(Value::as_i64)
            .ok_or("Missing wallpaper selection")?;
        if !(0..=14).contains(&wallpaper) {
            return Err("Wallpaper selection is invalid");
        }
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
                "tapClockOpensClock",
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
