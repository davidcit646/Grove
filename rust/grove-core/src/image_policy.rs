use crate::policy::n;
use serde_json::{json, Value};

pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a = &v["args"];
    match v["op"].as_str().ok_or("Missing operation")? {
        "imageSample" => {
            let (w, h, mw, mh) = (
                n(a, "width"),
                n(a, "height"),
                n(a, "maxWidth"),
                n(a, "maxHeight"),
            );
            if !(1..=8192).contains(&w) || !(1..=8192).contains(&h) || mw <= 0 || mh <= 0 {
                return Err("Image bounds");
            }
            let mut sample = 1;
            while w / sample > mw || h / sample > mh {
                sample *= 2;
            }
            Ok(json!(sample))
        }
        "imageValid" => Ok(json!(
            a["mime"]
                .as_str()
                .unwrap_or("")
                .to_lowercase()
                .starts_with("image/")
                && (1..=20 * 1024 * 1024).contains(&n(a, "bytes"))
                && (1..=8192).contains(&n(a, "width"))
                && (1..=8192).contains(&n(a, "height"))
        )),
        "crop" => {
            let (w, h, tw, th) = (
                n(a, "width"),
                n(a, "height"),
                n(a, "targetWidth"),
                n(a, "targetHeight"),
            );
            if w <= 0 || h <= 0 || tw <= 0 || th <= 0 {
                return Err("Crop bounds");
            }
            let ratio = tw as f32 / th as f32;
            let (cw, ch) = if w as f32 / h as f32 > ratio {
                ((h as f32 * ratio) as i64, h)
            } else {
                (w, (w as f32 / ratio) as i64)
            };
            Ok(json!({"width":cw.clamp(1,w),"height":ch.clamp(1,h)}))
        }
        _ => Err("Unknown operation"),
    }
}
