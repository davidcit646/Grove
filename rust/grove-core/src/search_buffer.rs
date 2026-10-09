//! Bounded versioned transport, borrowed only during a synchronous JNI call.
use crate::search::{prepare_query, score_prepared, top_indices};

fn integer(bytes: &mut &[u8]) -> Option<usize> {
    let value = u32::from_le_bytes(bytes.get(..4)?.try_into().ok()?) as usize;
    *bytes = &bytes[4..];
    Some(value)
}

pub(crate) fn search(mut bytes: &[u8], query: &str, limit: usize) -> Option<Vec<i32>> {
    if bytes.len() > 16 * 1024 * 1024
        || query.encode_utf16().count() > 256
        || integer(&mut bytes)? != 1
    {
        return None;
    }
    let count = integer(&mut bytes)?;
    if count > 50_000 {
        return None;
    }
    let prepared = prepare_query(query);
    let mut scores = Vec::with_capacity(count);
    for _ in 0..count {
        let length = integer(&mut bytes)?;
        let label = std::str::from_utf8(bytes.get(..length)?).ok()?;
        if label.encode_utf16().count() > 4096 {
            return None;
        }
        scores.push(score_prepared(label, &prepared));
        bytes = &bytes[length..];
    }
    if !bytes.is_empty() {
        return None;
    }
    Some(top_indices(&scores, limit))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn pack(labels: &[&str]) -> Vec<u8> {
        let mut bytes = Vec::from(1u32.to_le_bytes());
        bytes.extend_from_slice(&(labels.len() as u32).to_le_bytes());
        for label in labels {
            bytes.extend_from_slice(&(label.len() as u32).to_le_bytes());
            bytes.extend_from_slice(label.as_bytes());
        }
        bytes
    }
    #[test]
    fn matches_array_ranking_and_rejects_malformed_buffers() {
        let labels = [
            "café",
            "😀 tool",
            "東京",
            "document",
            "documemt",
            "document",
        ];
        let bytes = pack(&labels);
        for query in ["café", "😀", "東京", "document", "documant", "zzz", ""] {
            let prepared = prepare_query(query);
            let scores: Vec<_> = labels
                .iter()
                .map(|label| score_prepared(label, &prepared))
                .collect();
            for limit in [0, 1, 12, usize::MAX] {
                assert_eq!(
                    search(&bytes, query, limit),
                    Some(top_indices(&scores, limit))
                );
            }
        }
        for end in 0..bytes.len() {
            assert!(search(&bytes[..end], "x", 12).is_none());
        }
        let mut bad = bytes.clone();
        bad.push(0);
        assert!(search(&bad, "x", 12).is_none());
        bad = bytes.clone();
        bad[0] = 2;
        assert!(search(&bad, "x", 12).is_none());
        bad = pack(&["a"]);
        bad[12] = 0xff;
        assert!(search(&bad, "x", 12).is_none());
        assert!(search(&pack(&[&"a".repeat(4097)]), "x", 12).is_none());
        assert!(search(&bytes, &"x".repeat(257), 12).is_none());
    }
}
