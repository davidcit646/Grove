//! Portable decisions. Android supplies current authority; this module has no platform effects.
use serde_json::{json, Value};
use unicode_normalization::{UnicodeNormalization, char::is_combining_mark};

fn b(v: &Value, key: &str) -> bool { v[key].as_bool().unwrap_or(false) }
fn n(v: &Value, key: &str) -> i64 { v[key].as_i64().unwrap_or(0) }
fn strings(v: &Value) -> Vec<String> { v.as_array().map(|a| a.iter().filter_map(|x| x.as_str().map(str::to_owned)).collect()).unwrap_or_default() }
fn distinct(a: Vec<String>) -> Vec<String> { let mut seen = std::collections::HashSet::new(); a.into_iter().filter(|x| seen.insert(x.clone())).collect() }
pub(crate) fn normalize(s: &str) -> String {
    s.nfd().filter(|c| !is_combining_mark(*c)).collect::<String>().to_lowercase().trim_matches(|c: char| (c as u32) <= 32).to_owned()
}
pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a = &v["args"];
    match v["op"].as_str().ok_or("Missing operation")? {
        "normalize" => Ok(json!(normalize(a["text"].as_str().ok_or("Missing text")?))),

        "gesture" => {
            let dx = a["dx"].as_f64().ok_or("dx")? as f32;
            let dy = a["dy"].as_f64().ok_or("dy")? as f32;
            let min = a["minimum"].as_f64().ok_or("minimum")? as f32;
            let valid = (0..=700).contains(&n(a,"duration")) && dy.abs() >= min && dy.abs() > dx.abs() * 1.2;
            Ok(json!(if valid && dy > 0.0 && b(a,"down") { 1 } else if valid && dy < 0.0 && b(a,"up") { 2 } else { 0 }))
        }
        "drawerClose" => {
            let dx = a["dx"].as_f64().ok_or("dx")? as f32;
            let dy = a["dy"].as_f64().ok_or("dy")? as f32;
            let minimum = a["minimum"].as_f64().ok_or("minimum")? as f32;
            Ok(json!(b(a,"top") && (0..=700).contains(&n(a,"duration")) && dy >= minimum && dy.abs() > dx.abs() * 1.2))
        }
        "columns" => Ok(json!(if n(a,"custom") > 0 { n(a,"custom") } else if n(a,"width") >= 600 { 6 } else { 4 })),
        "grid" => {
            let capacity = n(a,"capacity"); let count = n(a,"count").max(0);
            let pages = if capacity <= 0 { 1 } else { ((count + capacity - 1) / capacity).max(1) };
            let page = n(a,"page").clamp(0,pages-1);
            Ok(json!({"pages":pages,"page":page,"start":page*capacity.max(0)}))
        }
        "indexState" => Ok(json!(if !b(a,"enabled") { if b(a,"exists") { 2 } else { 7 } }
            else if !b(a,"permitted") || b(a,"corrupt") { 6 }
            else if b(a,"failed") { 3 }
            else if b(a,"ready") { if b(a,"partial") { 5 } else { 0 } }
            else if b(a,"exists") { 5 } else if b(a,"working") { 4 } else { 1 })),
        "publication" => Ok(json!(n(a,"generation") == n(a,"current") && b(a,"active") && b(a,"enabled") && b(a,"access") && !b(a,"superseded"))),
        "access" => Ok(json!(b(a,"search") && b(a,"index") && b(a,"permitted"))),
        "delay" => Ok(json!(if !b(a,"provider") || n(a,"last") <= 0 { 0 } else { 30_000i64.saturating_sub(n(a,"now").saturating_sub(n(a,"last")).max(0)).clamp(0,30_000) })),
        "request" => Ok(json!(if a["active"].is_null() { 0 } else if a["active"] != a["started"] || a["pending"] == a["active"] { 1 } else { 2 })),
        "pin" => {
            let mut items = strings(&a["items"]); let item = a["item"].as_str().ok_or("item")?;
            if let Some(from) = items.iter().position(|x| x == item) {
                let target = if let Some(t) = a["target"].as_str() { items.iter().position(|x| x == t) } else {
                    Some((from as i64).saturating_add(n(a,"delta")).clamp(0,items.len().saturating_sub(1) as i64) as usize)
                };
                if let Some(to) = target { let moved=items.remove(from); items.insert(to,moved); }
            }
            Ok(json!(items))
        }
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

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn normalization_and_bounds() {
        assert_eq!(normalize("  CAFÉ  "),"cafe");
        assert_eq!(evaluate(&json!({"op":"grid","args":{"capacity":1,"count":2147483647,"page":2147483647}})).unwrap()["page"],2147483646i64);
    }
    #[test] fn protected_publication() {
        for denied in ["active","enabled","access"] {
            let mut a=json!({"active":true,"enabled":true,"access":true,"generation":2,"current":2,"superseded":false}); a[denied]=json!(false);
            assert_eq!(evaluate(&json!({"op":"publication","args":a})).unwrap(),false);
        }
    }
    #[test] fn pin_and_refresh() {
        assert_eq!(evaluate(&json!({"op":"pin","args":{"items":["a","b","c"],"item":"a","delta":2147483647}})).unwrap(),json!(["b","c","a"]));
        assert_eq!(evaluate(&json!({"op":"delay","args":{"provider":true,"last":100,"now":0}})).unwrap(),30000);
    }
}
