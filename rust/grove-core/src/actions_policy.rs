//! Portable action-list/queue decisions. Android effects stay in the action owners.
use crate::policy::{distinct, strings};
use serde_json::{json, Value};
pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a = &v["args"];
    match v["op"].as_str().ok_or("Operation")? {
        "channels" => {
            let channels = a["items"].as_array().ok_or("Channels")?;
            let mut seen = std::collections::HashSet::new();
            Ok(json!(channels
                .iter()
                .enumerate()
                .filter(|(_, c)| seen.insert((
                    c["label"].as_str().unwrap_or("").to_owned(),
                    c["mime"].as_str().unwrap_or("").contains("w4b")
                )))
                .map(|(i, _)| i)
                .collect::<Vec<_>>()))
        }
        "whatsApp" => {
            let channels = a["items"].as_array().ok_or("Channels")?;
            Ok(json!([
                channels.iter().any(|c| c["label"] == "WhatsApp"
                    && !c["mime"].as_str().unwrap_or("").contains("w4b")),
                channels
                    .iter()
                    .any(|c| c["mime"].as_str().unwrap_or("").contains("w4b"))
            ]))
        }
        "uninstall" => {
            let mut pending = strings(&a["pending"]);
            let mut current = a["current"].clone();
            let removed = pending.len();
            match a["action"].as_str().ok_or("Action")? {
                "start" => {
                    pending = distinct(
                        strings(&a["packages"])
                            .into_iter()
                            .filter(|p| !p.trim().is_empty())
                            .collect(),
                    );
                    current = Value::Null;
                }
                "cancel" => {
                    return Ok(json!({"pending":[],"current":null,"removed":removed}));
                }
                "accepted" => {
                    if current.is_null() {
                        return Ok(json!({"pending":pending,"current":null,"removed":0}));
                    }
                    current = Value::Null;
                }
                _ => return Err("Uninstall transition"),
            }
            if current.is_null() && !pending.is_empty() {
                current = json!(pending.remove(0));
            }
            Ok(json!({"pending":pending,"current":current,"removed":removed}))
        }
        _ => Err("Unknown action policy"),
    }
}
