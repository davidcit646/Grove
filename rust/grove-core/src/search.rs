use std::cmp::Ordering;

/// Java's \s without UNICODE_CHARACTER_CLASS: ASCII whitespace only.
/// Grove's Kotlin side splits on this set, so Rust must match it exactly.
pub(crate) fn is_java_space(c: char) -> bool {
    matches!(c, ' ' | '\t' | '\n' | '\x0B' | '\x0C' | '\r')
}

fn cmp_index(scores: &[i32], a: usize, b: usize) -> Ordering {
    scores[b].cmp(&scores[a]).then_with(|| a.cmp(&b))
}

/// Winning indices in final order: score descending, index ascending.
/// Mirrors the PriorityQueue selection in CoreBridge's Kotlin fallback exactly,
/// including the "earliest index wins ties" rule.
pub(crate) fn top_indices(scores: &[i32], limit: usize) -> Vec<i32> {
    let mut idx: Vec<usize> = (0..scores.len()).filter(|&i| scores[i] >= 0).collect();
    let keep = limit.min(idx.len());
    if keep < idx.len() {
        idx.select_nth_unstable_by(keep, |&a, &b| cmp_index(scores, a, b));
        idx.truncate(keep);
    }
    idx.sort_by(|&a, &b| cmp_index(scores, a, b));
    idx.into_iter().map(|i| i as i32).collect()
}

pub(crate) fn score_label(label: &str, query_text: &str, terms: &[&str]) -> i32 {
    if terms.is_empty() {
        return 0;
    }
    let ok = terms.iter().all(|term| {
        // Length gates count UTF-16 code units, exactly like Kotlin's String.length.
        let term_units: Vec<u16> = term.encode_utf16().collect();
        label.contains(term)
            || (term_units.len() >= 3
                && label.split(is_java_space).any(|word| {
                    let word_units: Vec<u16> = word.encode_utf16().collect();
                    word_units.len() >= 3
                        && edit_distance_at_most(
                            &word_units,
                            &term_units,
                            if term_units.len() >= 6 { 2 } else { 1 },
                        )
                }))
    });
    if !ok {
        return -1;
    }
    if label == query_text {
        3
    } else if label.starts_with(query_text) {
        2
    } else {
        1
    }
}

/// Bounded Levenshtein over UTF-16 code units — not Unicode scalars.
/// This is what makes the native path bit-identical to Kotlin's
/// editDistanceAtMost, so the old non-ASCII Kotlin fallback is unnecessary.
pub(crate) fn edit_distance_at_most(left: &[u16], right: &[u16], max: usize) -> bool {
    // A malicious app label should not make one search allocate or compute an
    // unbounded edit-distance matrix. Exact substring matches still work.
    if left.len() > 64 || right.len() > 64 {
        return false;
    }
    if left.len().abs_diff(right.len()) > max {
        return false;
    }
    let mut previous: Vec<usize> = (0..=right.len()).collect();
    let mut current = vec![0usize; right.len() + 1];
    for (i, &unit) in left.iter().enumerate() {
        current[0] = i + 1;
        for (j, &other) in right.iter().enumerate() {
            current[j + 1] = (current[j] + 1)
                .min(previous[j + 1] + 1)
                .min(previous[j] + usize::from(unit != other));
        }
        std::mem::swap(&mut previous, &mut current);
    }
    previous[right.len()] <= max
}

