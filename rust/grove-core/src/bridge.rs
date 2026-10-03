use crate::{classify, is_java_space, render_wallpaper, score_label, top_indices, validate_config};
use jni::objects::{JObject, JObjectArray, JString};
use jni::sys::{jint, jintArray, jobjectArray, jstring};
use jni::JNIEnv;

// --- JNI -------------------------------------------------------------------

fn java_strings(env: &mut JNIEnv, array: &JObjectArray) -> jni::errors::Result<Vec<String>> {
    let count = env.get_array_length(array)?;
    let mut strings = Vec::with_capacity(count as usize);
    for i in 0..count {
        let value = env.get_object_array_element(array, i)?;
        let string = JString::from(value);
        let text: String = env.get_string(&string)?.into();
        env.delete_local_ref(string)?;
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
    let result = (|| -> jni::errors::Result<_> {
        let labels = java_strings(&mut env, &labels)?;
        let query: String = env.get_string(&query)?.into();
        let terms: Vec<&str> = query
            .split(is_java_space)
            .filter(|t| !t.is_empty())
            .collect();
        let scores: Vec<i32> = labels
            .iter()
            .map(|label| score_label(label, &query, &terms))
            .collect();
        let order = top_indices(&scores, limit.max(0) as usize);
        let out = env.new_int_array(order.len() as i32)?;
        env.set_int_array_region(&out, 0, &order)?;
        Ok(out.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
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
    let result = (|| -> jni::errors::Result<_> {
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
    })();
    result.unwrap_or(std::ptr::null_mut())
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
    let result = (|| -> jni::errors::Result<_> {
        let (w, h) = (width.max(1) as usize, height.max(1) as usize);
        let pixels: Vec<i32> = render_wallpaper(style.max(0) as usize, w, h)
            .into_iter()
            .map(|p| p as i32)
            .collect();
        let out = env.new_int_array(pixels.len() as i32)?;
        env.set_int_array_region(&out, 0, &pixels)?;
        Ok(out.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_tech_granet_grove_CoreBridge_configErrorNative(
    mut env: JNIEnv,
    _this: JObject,
    json: JString,
) -> jstring {
    let result = (|| -> jni::errors::Result<_> {
        let json: String = env.get_string(&json)?.into();
        let error = validate_config(&json).err().unwrap_or("");
        Ok(env.new_string(error)?.into_raw())
    })();
    result.unwrap_or(std::ptr::null_mut())
}

