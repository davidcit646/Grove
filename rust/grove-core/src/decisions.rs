use serde_json::{json, Value};
use crate::policy::{b,n,strings};

pub(crate) fn evaluate(v: &Value) -> Result<Value, &'static str> {
    let a=&v["args"];
    match v["op"].as_str().ok_or("Missing operation")? {
        "rule" => match a["rule"].as_str().ok_or("Rule")? {
            "configCurrent" => Ok(json!(b(a,"same"))),
            "coverage" => Ok(json!(n(a,"count") >= n(a,"limit"))),
            "freshInstall" => Ok(json!(!b(a,"config") && !b(a,"initialized") && !b(a,"completed"))),
            "trustedSettings" => Ok(json!(["enabled","application","exported","system","permitted"].iter().all(|key| b(a,key)))),
            "reportPrompt" => Ok(json!(n(a,"count") > 0 && !b(a,"prompting") && (b(a,"explicit") || b(a,"automatic")))),
            "mailHandler" => Ok(json!(n(a,"count") > 0)),
            "indexRelease" => Ok(json!(a["expected"] == a["current"] && (!b(a,"present") || b(a,"finished")))),
            "repair" => Ok(json!(b(a,"manual") || !b(a,"attempted"))),
            "wallpaperColors" => Ok(json!(a["mode"] == "wallpaper" || (a["mode"] == "system" && b(a,"legacy")))),
            "sourceChanged" => Ok(json!(n(a,"which") & n(a,"home") != 0)),
            "tutorialReplay" => Ok(json!(if !b(a,"pending") || b(a,"setup") { 0 } else if !b(a,"apps") { 1 } else { 2 })),
            "seedFavorites" => Ok(json!(!b(a,"completed") && b(a,"empty"))),
            "tutorialShow" => Ok(json!(!b(a,"suppressed") && (b(a,"replay") || n(a,"saved") < 1))),
            "sourceState" => Ok(json!(if !b(a,"enabled") { 0 } else if !b(a,"access") { 1 } else if b(a,"failed") { 2 } else if b(a,"loading") { 3 } else if n(a,"skipped") > 0 { 4 } else { 5 })),
            "cacheFresh" => Ok(json!(b(a,"available") && !b(a,"invalid") && n(a,"now").checked_sub(n(a,"written")).is_some_and(|age| (0..=if a["kind"] == "files" { 86400000 } else { 900000 }).contains(&age)))),
            "settingsEffects" => Ok(json!(a["beforeSearch"] != a["afterSearch"] || a["beforeIndex"] != a["afterIndex"])),
            "tutorialPage" => Ok(json!(n(a,"page").saturating_add(n(a,"delta")).clamp(0,2))),
            "setupFavorites" => {
                let pins: std::collections::HashSet<String> = strings(&a["pins"]).into_iter().collect();
                Ok(json!(strings(&a["available"]).into_iter().filter(|key|pins.contains(key)).collect::<Vec<_>>()))
            }
            "setupStep" => {
                let pages=if b(a,"down") || b(a,"up") { vec![0,1,2,3,4,5,6,7] } else { vec![0,1,3,4,5,6,7] };
                let page=n(a,"page"); let next=if b(a,"back") { pages.iter().position(|&p|p==page).filter(|&i|i>0).map(|i|pages[i-1]).unwrap_or(page) }
                    else { pages.iter().find(|&&p|p>page).copied().unwrap_or(page) };
                Ok(json!({"page":next}))
            }
            "setupPages" => Ok(json!(if b(a,"down") || b(a,"up") { vec![0,1,2,3,4,5,6,7] } else { vec![0,1,3,4,5,6,7] })),
            "setupPin" => Ok(json!(!b(a,"checked") || b(a,"contained") || n(a,"count") < 12)),
            _ => Err("Unknown rule"),
        },
        _ => Err("Unknown operation"),
    }
}
