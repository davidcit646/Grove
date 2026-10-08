use crate::policy::{distinct, strings};
use serde_json::{json, Value};

fn same_name(left: &str, right: &str) -> bool {
    fn upper(c: char) -> char {
        let mut case = c.to_uppercase();
        let first = case.next().unwrap();
        if case.next().is_none() {
            first
        } else {
            c
        }
    }
    fn lower(c: char) -> char {
        c.to_lowercase().next().unwrap()
    }
    left.encode_utf16().count() == right.encode_utf16().count()
        && left
            .chars()
            .zip(right.chars())
            .all(|(a, b)| a == b || upper(a) == upper(b) || lower(upper(a)) == lower(upper(b)))
}
pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a = &v["args"];
    match v["op"].as_str().ok_or("Missing operation")? {
        "setup" => {
            let base = &a["base"];
            let draft = &a["draft"];
            let mut current = a["current"].clone();
            for key in ["gestures", "homeScreen", "favorites"] {
                if draft[key] != base[key] {
                    if current[key] != base[key] && current[key] != draft[key] {
                        return Ok(Value::Null);
                    }
                    current[key] = draft[key].clone();
                }
            }
            for key in [
                "contacts",
                "files",
                "contactIndexing",
                "fileIndexing",
                "calculator",
                "androidSettings",
                "groveSettings",
            ] {
                if draft["search"][key] != base["search"][key] {
                    if current["search"][key] != base["search"][key]
                        && current["search"][key] != draft["search"][key]
                    {
                        return Ok(Value::Null);
                    }
                    current["search"][key] = draft["search"][key].clone();
                }
            }
            Ok(current)
        }
        "folder" => {
            let mut config = a["config"].clone();
            let name = a["name"].as_str().unwrap_or("").trim();
            let action = a["action"].as_str().ok_or("action")?;
            let keys = distinct(strings(&a["keys"]));
            let folders = config["folders"].as_array_mut().ok_or("folders")?;
            match action {
                "pin" => {
                    if keys.is_empty() {
                        return Ok(Value::Null);
                    }
                    config["favorites"] =
                        json!(distinct([strings(&config["favorites"]), keys].concat()));
                    return Ok(config);
                }
                "create" => {
                    if !(1..=40).contains(&name.encode_utf16().count())
                        || folders
                            .iter()
                            .any(|f| same_name(f["name"].as_str().unwrap_or(""), name))
                    {
                        return Ok(Value::Null);
                    }
                    for f in folders.iter_mut() {
                        f["apps"] = json!(strings(&f["apps"])
                            .into_iter()
                            .filter(|k| !keys.contains(k))
                            .collect::<Vec<_>>());
                    }
                    folders.push(json!({"name":name,"apps":keys}));
                }
                "rename" => {
                    let old = a["old"].as_str().ok_or("old")?;
                    if !(1..=40).contains(&name.encode_utf16().count())
                        || !folders.iter().any(|f| f["name"] == old)
                        || folders.iter().any(|f| {
                            f["name"] != old && same_name(f["name"].as_str().unwrap_or(""), name)
                        })
                    {
                        return Ok(Value::Null);
                    }
                    for f in folders.iter_mut() {
                        if f["name"] == old {
                            f["name"] = json!(name);
                        }
                    }
                }
                "delete" => {
                    if !folders.iter().any(|f| f["name"] == name) {
                        return Ok(Value::Null);
                    }
                    folders.retain(|f| f["name"] != name);
                }
                "move" | "remove" => {
                    if action == "move"
                        && (keys.is_empty() || !folders.iter().any(|f| f["name"] == name))
                    {
                        return Ok(Value::Null);
                    }
                    for f in folders.iter_mut() {
                        let members = strings(&f["apps"]);
                        f["apps"] = if action == "move" && f["name"] == name {
                            json!(distinct([members, keys.clone()].concat()))
                        } else {
                            json!(members
                                .into_iter()
                                .filter(|k| !keys.contains(k))
                                .collect::<Vec<_>>())
                        };
                    }
                }
                _ => return Err("Unknown folder action"),
            }
            Ok(config)
        }
        _ => Err("Unknown operation"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn folder_names_match_jvm_simple_case() {
        assert!(same_name("İ", "i"));
        assert!(same_name("ı", "I"));
        assert!(same_name("Σ", "ς"));
        assert!(!same_name("ß", "s"));
        assert!(same_name("Work", "wOrK"));
    }
}
