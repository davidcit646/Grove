use serde_json::{json, Value};
use crate::policy::{strings,distinct};

pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a=&v["args"];
    match v["op"].as_str().ok_or("Missing operation")? {
        "setup" => {
            let base=&a["base"]; let draft=&a["draft"]; let mut current=a["current"].clone();
            for key in ["gestures","homeScreen","favorites"] {
                if draft[key] != base[key] {
                    if current[key] != base[key] && current[key] != draft[key] { return Ok(Value::Null); }
                    current[key]=draft[key].clone();
                }
            }
            for key in ["contacts","files","contactIndexing","fileIndexing","calculator","androidSettings","groveSettings"] {
                if draft["search"][key] != base["search"][key] {
                    if current["search"][key] != base["search"][key] && current["search"][key] != draft["search"][key] { return Ok(Value::Null); }
                    current["search"][key]=draft["search"][key].clone();
                }
            }
            Ok(current)
        }
        "folder" => {
            let mut config=a["config"].clone(); let name=a["name"].as_str().unwrap_or("").trim();
            let action=a["action"].as_str().ok_or("action")?; let keys=distinct(strings(&a["keys"]));
            let folders=config["folders"].as_array_mut().ok_or("folders")?;
            match action {
                "pin" => {
                    if keys.is_empty() { return Ok(Value::Null); }
                    config["favorites"] = json!(distinct([strings(&config["favorites"]),keys].concat()));
                    return Ok(config);
                }
                "create" => {
                    if !(1..=40).contains(&name.encode_utf16().count()) || folders.iter().any(|f|f["name"].as_str().unwrap_or("").to_lowercase()==name.to_lowercase()) { return Ok(Value::Null); }
                    for f in folders.iter_mut() { f["apps"]=json!(strings(&f["apps"]).into_iter().filter(|k|!keys.contains(k)).collect::<Vec<_>>()); }
                    folders.push(json!({"name":name,"apps":keys}));
                }
                "rename" => {
                    let old=a["old"].as_str().ok_or("old")?;
                    if !(1..=40).contains(&name.encode_utf16().count()) || !folders.iter().any(|f|f["name"]==old) || folders.iter().any(|f|f["name"]!=old && f["name"].as_str().unwrap_or("").to_lowercase()==name.to_lowercase()) { return Ok(Value::Null); }
                    for f in folders.iter_mut() { if f["name"]==old { f["name"]=json!(name); } }
                }
                "delete" => { if !folders.iter().any(|f|f["name"]==name) {return Ok(Value::Null);} folders.retain(|f|f["name"]!=name); }
                "move" | "remove" => {
                    if action=="move" && (keys.is_empty() || !folders.iter().any(|f|f["name"]==name)) {return Ok(Value::Null);}
                    for f in folders.iter_mut() { let members=strings(&f["apps"]); f["apps"]=if action=="move" && f["name"]==name { json!(distinct([members,keys.clone()].concat())) } else { json!(members.into_iter().filter(|k|!keys.contains(k)).collect::<Vec<_>>()) }; }
                }
                _ => return Err("Unknown folder action"),
            }
            Ok(config)
        }
        _ => Err("Unknown operation"),
    }
}
