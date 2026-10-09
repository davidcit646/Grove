//! Complete portable transition for one touch event; Kotlin owns Android dispatch.
use crate::policy::{b, n};
use serde_json::{json, Value};
fn f(v: &Value, k: &str) -> f32 {
    v[k].as_f64().unwrap_or(0.0) as f32
}
fn gesture(dx: f32, dy: f32, elapsed: i64, min: f32, a: &Value) -> i32 {
    if !(0..=700).contains(&elapsed) || dy.abs() < min || dy.abs() <= dx.abs() * 1.2 {
        0
    } else if dy > 0.0 && b(a, "down") {
        1
    } else if dy < 0.0 && b(a, "up") {
        2
    } else {
        0
    }
}
pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a = &v["args"];
    let mut s = a["state"].clone();
    let elapsed = n(a, "time").wrapping_sub(n(&s, "startedAt"));
    let dx = f(a, "x") - f(&s, "startX");
    let dy = f(a, "y") - f(&s, "startY");
    let mut r = json!({"consume":false,"cancelChildren":false,"settle":false,"closeDrawer":false,"gesture":0,"contextMenu":false,"moved":false,"offset":null});
    match a["action"].as_str().ok_or("Action")? {
        "begin" => {
            s["startX"] = a["x"].clone();
            s["startY"] = a["y"].clone();
            s["startedAt"] = a["time"].clone();
            s["home"] = json!(!b(a, "inDrawer") && b(a, "hasHome"));
            s["drawerAtTop"] = json!(b(a, "inDrawer") && b(a, "atTop"));
            s["drawer"] = s["drawerAtTop"].clone();
            s["blocked"] = json!(b(&s, "home") && b(a, "widget"));
            s["interactive"] = json!(b(&s, "home") && b(a, "interactiveTarget"));
            for k in ["moved", "captured", "drawerCaptured", "longPressed"] {
                s[k] = json!(false);
            }
            r["value"] = json!(b(&s, "home") && !b(&s, "interactive") && !b(&s, "blocked"));
        }
        "longPress" => {
            let accepted = b(&s, "home") && !b(&s, "interactive") && !b(&s, "blocked");
            if accepted {
                s["longPressed"] = json!(true);
                s["home"] = json!(false);
            }
            r["value"] = json!(accepted);
        }
        "cancel" => {
            r["value"] =
                json!(b(&s, "captured") || b(&s, "drawerCaptured") || b(&s, "longPressed"));
            for k in [
                "home",
                "drawer",
                "captured",
                "drawerCaptured",
                "longPressed",
            ] {
                s[k] = json!(false);
            }
        }
        "move" => {
            let newly = b(&s, "drawer")
                && !b(&s, "drawerCaptured")
                && dy > f(a, "slop")
                && dy.abs() > dx.abs() * 1.1;
            if newly {
                s["drawerCaptured"] = json!(true);
            }
            if b(&s, "drawer") && b(&s, "drawerCaptured") {
                r["cancelChildren"] = json!(newly);
                r["offset"] = json!(dy.max(0.0) * 0.72);
                r["consume"] = json!(true);
            } else if b(&s, "longPressed") {
                r["consume"] = json!(true);
            } else if b(&s, "home") {
                let crossed = dx * dx + dy * dy > f(a, "slop") * f(a, "slop");
                if crossed {
                    s["moved"] = json!(true);
                    if !b(&s, "captured") && b(a, "scroll") {
                        s["blocked"] = json!(true);
                    }
                }
                let capture = !b(&s, "blocked")
                    && !b(&s, "captured")
                    && gesture(dx, dy, elapsed, f(a, "minimum"), a) != 0;
                if capture {
                    s["captured"] = json!(true);
                }
                r["moved"] = json!(crossed);
                r["cancelChildren"] = json!(capture);
                r["consume"] = s["captured"].clone();
                if !b(&s, "blocked") && !b(&s, "interactive") {
                    let limit = f(a, "preview");
                    if limit < 0.0 {
                        return Err("Preview bounds");
                    }
                    r["offset"] = json!((dy * 0.10).clamp(-limit, limit));
                }
            }
        }
        "release" => {
            if b(&s, "drawer") || b(&s, "drawerCaptured") {
                let close = b(&s, "drawerCaptured")
                    && b(&s, "drawerAtTop")
                    && (0..=700).contains(&elapsed)
                    && dy >= f(a, "drawerMinimum")
                    && dy.abs() > dx.abs() * 1.2;
                let swallow = b(&s, "drawerAtTop") && dy > f(a, "slop");
                s["drawer"] = json!(false);
                s["drawerCaptured"] = json!(false);
                r["consume"] = json!(close || swallow);
                r["closeDrawer"] = json!(close);
                r["settle"] = json!(!close);
            } else if b(&s, "longPressed") {
                s["longPressed"] = json!(false);
                s["home"] = json!(false);
                r["consume"] = json!(true);
                r["settle"] = json!(true);
            } else if b(&s, "home") {
                s["home"] = json!(false);
                let g = if !b(&s, "blocked") {
                    gesture(dx, dy, elapsed, f(a, "minimum"), a)
                } else {
                    0
                };
                let captured = b(&s, "captured");
                s["captured"] = json!(false);
                if g != 0 {
                    r["consume"] = json!(true);
                    r["cancelChildren"] = json!(!captured);
                    r["gesture"] = json!(g);
                } else {
                    let tap = !b(&s, "moved")
                        && dx * dx + dy * dy <= f(a, "slop") * f(a, "slop")
                        && elapsed <= 350;
                    let menu = !b(&s, "interactive") && tap && b(a, "tap");
                    r["consume"] = json!(captured || menu);
                    r["cancelChildren"] = json!(menu);
                    r["settle"] = json!(true);
                    r["contextMenu"] = json!(menu);
                }
            }
        }
        _ => return Err("Unknown touch transition"),
    }
    Ok(json!({"state":s,"result":r}))
}
