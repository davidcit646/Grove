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

pub(crate) struct PreparedQuery<'a> {
    text: &'a str,
    terms: Vec<(&'a str, Vec<u16>)>,
}
pub(crate) fn prepare_query(text: &str) -> PreparedQuery<'_> {
    PreparedQuery { text, terms: text.split(is_java_space).filter(|t|!t.is_empty())
        .map(|term|(term,term.encode_utf16().collect())).collect() }
}
#[cfg(test)]
pub(crate) fn score_label(label: &str, query_text: &str, terms: &[&str]) -> i32 {
    let query=PreparedQuery {text:query_text,terms:terms.iter().map(|&term|(term,term.encode_utf16().collect())).collect()};
    score_prepared(label,&query)
}
pub(crate) fn score_prepared(label: &str, query: &PreparedQuery<'_>) -> i32 {
    if query.terms.is_empty() { return 0; }
    let ok=query.terms.iter().all(|(term, units)| {
        if label.contains(term) { return true; }
        units.len() >= 3 && units.len() <= 64 && label.split(is_java_space).any(|word| {
            let mut word_units = [0u16;64];
            let mut count=0;
            for unit in word.encode_utf16() {
                if count==64 { return false; }
                word_units[count]=unit;count+=1;
            }
            count>=3 && edit_distance_at_most(&word_units[..count],units,if units.len()>=6 {2}else{1})
        })
    });
    if !ok {-1} else if label==query.text {3} else if label.starts_with(query.text) {2} else {1}
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
    // Only the diagonal band can reach the edit budget. Stack arrays avoid two
    // heap allocations per candidate word, and a dead row stops immediately.
    let max = max.min(64);
    let ceiling = (max + 1) as u8;
    let mut previous = [ceiling; 65];
    let mut current = [ceiling; 65];
    for (j, cell) in previous.iter_mut().enumerate().take(right.len() + 1) { *cell = j as u8; }
    for (i, &unit) in left.iter().enumerate() {
        current[..=right.len()].fill(ceiling);
        current[0] = (i + 1) as u8;
        let start = (i + 1).saturating_sub(max).max(1);
        let end = (i + 1 + max).min(right.len());
        let mut best = current[0];
        for j in start..=end {
            current[j] = (current[j - 1] + 1)
                .min(previous[j] + 1)
                .min(previous[j - 1] + u8::from(unit != right[j - 1]));
            best = best.min(current[j]);
        }
        if best as usize > max { return false; }
        std::mem::swap(&mut previous, &mut current);
    }
    previous[right.len()] as usize <= max
}

#[cfg(test)]
mod band_tests {
    use super::*;
    fn full(a: &[u16], b: &[u16]) -> usize {
        let mut row: Vec<usize> = (0..=b.len()).collect();
        for (i, x) in a.iter().enumerate() {
            let mut next = vec![i + 1; b.len() + 1];
            for (j, y) in b.iter().enumerate() {
                next[j + 1] = (next[j] + 1).min(row[j + 1] + 1).min(row[j] + usize::from(x != y));
            }
            row = next;
        }
        row[b.len()]
    }
    #[test]
    fn band_matches_full_matrix_exhaustively() {
        let mut words = vec![Vec::new()];
        for len in 1..=6 {
            for bits in 0..(1 << len) {
                words.push((0..len).map(|i| ((bits >> i) & 1) as u16).collect());
            }
        }
        for a in &words { for b in &words { for max in 0..=2 {
            assert_eq!(edit_distance_at_most(a, b, max), full(a, b) <= max, "{a:?}/{b:?}/{max}");
        } } }
    }
}
