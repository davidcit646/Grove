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
    if !(1..=12).contains(&version) {
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
                "calculator",
                "androidSettings",
                "groveSettings",
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

/// Validate once, migrate defaults, discard retired fields and return portable schema v12.
pub(crate) fn canonical(text: &str) -> Result<Value, &'static str> {
    validate_config(text)?;
    let source: Value = serde_json::from_str(text).map_err(|_| "Invalid JSON")?;
    let version=source["version"].as_i64().ok_or("Missing version")?;
    let wallpaper=if version>=9 {source["wallpaper"].clone()}else{
        let index=source["wallpaper"].as_u64().ok_or("Invalid wallpaper")?;
        Value::String(match index {0=>"grove-fern".into(),1=>"grove-ember".into(),2=>"grove-dusk".into(),3..=12=>format!("commons-{}",index-3),13=>"solid-black".into(),14=>"custom-image".into(),_=>return Err("Invalid wallpaper")})
    };
    let mut output=serde_json::json!({"version":12,"wallpaper":wallpaper,"themeMode":if version>=10 {source["themeMode"].clone()}else{Value::String("system".into())},"homeGrid":if version>=11 {source["homeGrid"].clone()}else{Value::Null},"drawerGrid":if version>=11 {source["drawerGrid"].clone()}else{Value::Null}});
    for (section,defaults) in [
        ("gestures",vec![("swipeDownSearch",true),("swipeUpAppDrawer",true),("tapHomeContextMenu",false),("longPressHomeContextMenu",true)]),
        ("homeScreen",vec![("showAppsButton",true),("showSearchButton",true),("showClock",true),("tapClockOpensClock",true),("showPinnedApps",true),("useWallpaperButtonColors",false),("pinnedAppsAtBottom",true)]),
        ("search",vec![("contacts",version<7),("files",version<7),("contactIndexing",false),("fileIndexing",false),("calculator",true),("androidSettings",true),("groveSettings",true)])
    ] {
        let mut map=serde_json::Map::new();for(key,default)in defaults {map.insert(key.into(),Value::Bool(source[section][key].as_bool().unwrap_or(default)));}output[section]=Value::Object(map);
    }
    let mut seen=std::collections::HashSet::new();
    output["favorites"]=Value::Array(source["favorites"].as_array().ok_or("Invalid favorites")?.iter().filter(|x|seen.insert(x.as_str().unwrap_or("").to_owned())).cloned().collect());
    let mut folders=Vec::new();let mut names=std::collections::HashSet::new();let mut members=std::collections::HashSet::new();
    if let Some(items)=source.get("folders").and_then(Value::as_array){for f in items{
        let name=f["name"].as_str().ok_or("Invalid folder")?.trim();
        if !names.insert(name.to_lowercase()){return Err("Duplicate folder name");}
        let mut local=std::collections::HashSet::new();let mut apps=Vec::new();
        for app in f["apps"].as_array().ok_or("Invalid folder apps")?{
            let key=app.as_str().ok_or("Invalid folder app")?;
            if local.insert(key.to_owned()){if !members.insert(key.to_owned()){return Err("App in multiple folders");}apps.push(app.clone());}
        }
        folders.push(serde_json::json!({"name":name,"apps":apps}));
    }}
    output["folders"]=Value::Array(folders);Ok(output)
}

#[cfg(test)]mod canonical_tests {use super::*;
    #[test]fn migration_and_duplicate_ownership(){
        let v=canonical(r#"{"version":1,"wallpaper":0,"favorites":["x/.A","x/.A"]}"#).unwrap();
        assert_eq!(v["version"],12);assert_eq!(v["search"]["contacts"],true);assert_eq!(v["favorites"].as_array().unwrap().len(),1);
        assert!(canonical(r#"{"version":12,"wallpaper":"grove-fern","themeMode":"system","favorites":[],"folders":[{"name":"Work","apps":[]},{"name":"work","apps":[]}] }"#).is_err());
    }
}
