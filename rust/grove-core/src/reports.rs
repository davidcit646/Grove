//! Report formatting accepts only projected safe fields, never exception messages.
use serde_json::{json, Value};
fn text<'a>(v: &'a Value, key: &str) -> &'a str {
    v[key].as_str().unwrap_or("")
}
pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a = &v["args"];
    match v["op"].as_str().ok_or("Operation")? {
        "diagnostic" => {
            let mut out = format!("exception: {}\n", text(a, "type"));
            if let Some(frames) = a["frames"].as_array() {
                for f in frames.iter().take(24) {
                    out.push_str(&format!(
                        "at {}.{}({}:{})\n",
                        text(f, "class"),
                        text(f, "method"),
                        text(f, "file"),
                        f["line"].as_i64().unwrap_or(-1)
                    ));
                }
            }
            out.push_str("Exception messages, contact data, file paths, imported configuration, and sensitive values are intentionally omitted.\n");
            Ok(json!(out))
        }
        "report" => {
            let mut out = format!("Grove Launcher {} report\n", text(a, "kind"));
            for key in [
                "time", "app", "device", "android", "feature", "severity", "code",
            ] {
                out.push_str(&format!("{key}: {}\n", text(a, key)));
            }
            if let Some(gws) = a["gws"].as_str() {
                out.push_str(&format!("gws: {gws}\n"));
            }
            out.push_str(&format!("summary: {}\n", text(a, "summary")));
            if let Some(diagnostic) = a["diagnostic"].as_str() {
                out.push_str("--- safe diagnostic ---\n");
                out.push_str(diagnostic);
            }
            if out.len() > 60000 {
                return Err("Report bound");
            }
            Ok(json!(out))
        }
        _ => Err("Report operation"),
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bounded_safe_fields() {
        let result=evaluate(&json!({"op":"diagnostic","args":{"type":"Error","frames":[{"class":"C","method":"m","file":"C.kt","line":3}],"message":"secret"}})).unwrap();
        let body = result.as_str().unwrap();
        assert!(body.contains("C.m(C.kt:3)"));
        assert!(!body.contains("secret"));
    }
}
