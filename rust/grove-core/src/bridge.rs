use crate::{classify, prepare_query, render_wallpaper, score_prepared, top_indices};
use jni::objects::{JObject, JObjectArray, JString};
use jni::sys::{jint, jintArray, jobjectArray, jstring};
use jni::JNIEnv;
#[cfg(any(target_os = "linux", target_os = "android"))]
use std::os::fd::{FromRawFd, IntoRawFd};

// --- JNI -------------------------------------------------------------------

fn java_strings(env: &mut JNIEnv, array: &JObjectArray) -> jni::errors::Result<Vec<String>> {
    let count = env.get_array_length(array)?;
    if count > 50_000 {
        return Err(jni::errors::Error::NullPtr("Too many labels"));
    }
    let mut strings = Vec::with_capacity(count as usize);
    let mut bytes = 0usize;
    for i in 0..count {
        let value = env.get_object_array_element(array, i)?;
        let string = JString::from(value);
        let text: String = env.get_string(&string)?.into();
        env.delete_local_ref(string)?;
        if text.encode_utf16().count() > 4096 {
            return Err(jni::errors::Error::NullPtr("Label too long"));
        }
        bytes += text.len();
        if bytes > 16 * 1024 * 1024 {
            return Err(jni::errors::Error::NullPtr("Batch too large"));
        }
        strings.push(text);
    }
    Ok(strings)
}

/// Score every label against the prepared query and return the winning
/// indices in final order. One JNI call per search; no score array crosses.
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_searchNative(
    mut env: JNIEnv,
    _this: JObject,
    labels: JObjectArray,
    query: JString,
    limit: jint,
) -> jintArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> jni::errors::Result<_> {
            let labels = java_strings(&mut env, &labels)?;
            let query: String = env.get_string(&query)?.into();
            if query.encode_utf16().count() > 256 {
                return Err(jni::errors::Error::NullPtr("Query too long"));
            }
            let prepared = prepare_query(&query);
            let scores: Vec<i32> = labels
                .iter()
                .map(|label| score_prepared(label, &prepared))
                .collect();
            let order = top_indices(&scores, limit.max(0) as usize);
            let out = env.new_int_array(order.len() as i32)?;
            env.set_int_array_region(&out, 0, &order)?;
            Ok(out.into_raw())
        },
    ));
    match result {
        Ok(Ok(value)) => value,
        _ => std::ptr::null_mut(),
    }
}

/// Classify file extensions Grove knows about. Returns "mime|category"
/// per extension, or an empty string when unknown (Kotlin then tries
/// Android's MimeTypeMap).
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_classifyNative(
    mut env: JNIEnv,
    _this: JObject,
    extensions: JObjectArray,
) -> jobjectArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> jni::errors::Result<_> {
            let extensions = java_strings(&mut env, &extensions)?;
            let out =
                env.new_object_array(extensions.len() as i32, "java/lang/String", JObject::null())?;
            for (i, ext) in extensions.iter().enumerate() {
                let packed = match classify(ext) {
                    Some((mime, cat)) => format!("{mime}|{cat}"),
                    None => String::new(),
                };
                let value = env.new_string(packed)?;
                env.set_object_array_element(&out, i as i32, &value)?;
                env.delete_local_ref(value)?;
            }
            Ok(out.into_raw())
        },
    ));
    match result {
        Ok(Ok(value)) => value,
        _ => std::ptr::null_mut(),
    }
}

/// Render a generative wallpaper style to ARGB pixels (row-major).
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_renderWallpaperNative(
    env: JNIEnv,
    _this: JObject,
    style: jint,
    width: jint,
    height: jint,
) -> jintArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> jni::errors::Result<_> {
            if width <= 0 || height <= 0 || (width as i64) * (height as i64) > 16_000_000 {
                return Err(jni::errors::Error::NullPtr("Invalid dimensions"));
            }
            let (w, h) = (width as usize, height as usize);
            let pixels: Vec<i32> = render_wallpaper(style.max(0) as usize, w, h)
                .into_iter()
                .map(|p| p as i32)
                .collect();
            let out = env.new_int_array(pixels.len() as i32)?;
            env.set_int_array_region(&out, 0, &pixels)?;
            Ok(out.into_raw())
        },
    ));
    match result {
        Ok(Ok(value)) => value,
        _ => std::ptr::null_mut(),
    }
}

/// Versioned envelope: invalid input is data; panic/JNI failure is native unavailability.
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_policyNative(
    mut env: JNIEnv,
    _this: JObject,
    input: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> jni::errors::Result<_> {
            let input: String = env.get_string(&input)?.into();
            let result = if input.len() > 196_608 {
                Err("Input exceeds size limit")
            } else {
                serde_json::from_str::<serde_json::Value>(&input)
                    .map_err(|_| "Invalid JSON")
                    .and_then(|v| {
                        if v["op"] == "calculator" {
                            Ok(crate::calculator::calculate(
                                v["args"]["text"].as_str().unwrap_or(""),
                            ))
                        } else if v["op"] == "config" {
                            crate::config::canonical(
                                v["args"]["text"].as_str().ok_or("Missing config")?,
                            )
                        } else {
                            crate::policy::evaluate(&v)
                        }
                    })
            };
            let response = match result {
                Ok(value) => serde_json::json!({"version":1,"value":value}),
                Err(reason) => serde_json::json!({"version":1,"error":reason}),
            };
            Ok(env.new_string(response.to_string())?.into_raw())
        },
    ));
    match result {
        Ok(Ok(value)) => value,
        _ => std::ptr::null_mut(),
    }
}

/// Batch label normalization. Local references are released after each output row.
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_normalizeNative(
    mut env: JNIEnv,
    _this: JObject,
    labels: JObjectArray,
) -> jobjectArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(
        || -> jni::errors::Result<_> {
            let labels = java_strings(&mut env, &labels)?;
            let out =
                env.new_object_array(labels.len() as i32, "java/lang/String", JObject::null())?;
            for (i, label) in labels.iter().enumerate() {
                let value = env.new_string(crate::policy::normalize(label))?;
                env.set_object_array_element(&out, i as i32, &value)?;
                env.delete_local_ref(value)?;
            }
            Ok(out.into_raw())
        },
    ));
    match result {
        Ok(Ok(value)) => value,
        _ => std::ptr::null_mut(),
    }
}

#[cfg(any(target_os = "linux", target_os = "android"))]
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_openSharedNative(
    mut env: JNIEnv,
    _this: JObject,
    root: JString,
    relative: JString,
) -> jint {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let root: String = env.get_string(&root).ok()?.into();
        let relative: String = env.get_string(&relative).ok()?.into();
        crate::shared_file::open_shared(&root, &relative)
            .ok()
            .map(IntoRawFd::into_raw_fd)
    }));
    match result {
        Ok(Some(fd)) => fd,
        _ => -1,
    }
}
#[cfg(any(target_os = "linux", target_os = "android"))]
#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_closeSharedNative(
    _env: JNIEnv,
    _this: JObject,
    fd: jint,
) {
    let _ = std::panic::catch_unwind(|| {
        if fd >= 0 {
            // SAFETY: caller transfers ownership only when Java adoptFd failed.
            drop(unsafe { std::fs::File::from_raw_fd(fd) });
        }
    });
}
