#[path = "search-baseline.rs"] mod baseline;
#[path = "../../rust/grove-core/src/search.rs"] mod candidate;
use std::time::Instant;
use std::hint::black_box;
fn measure(native: bool, labels: &[String], query: &str) -> Vec<u128> {
    let terms: Vec<&str> = query.split(' ').collect();
    let prepared = candidate::prepare_query(query);
    let mut times = Vec::new();
    for _ in 0..35 {
        let start = Instant::now();
        let scores: Vec<i32> = labels.iter().map(|label| black_box(if native { candidate::score_prepared(black_box(label), &prepared) } else { baseline::score_label(black_box(label), query, &terms) })).collect();
        black_box(candidate::top_indices(&scores, 12));
        times.push(start.elapsed().as_micros());
    }
    times.sort(); times
}
fn main() {
    println!("rows,query,baseline_p50_us,candidate_p50_us,baseline_p95_us,candidate_p95_us");
    for count in [15_000, 50_000] {
        let labels: Vec<String> = (0..count).map(|i| format!("document {i} monthly summary report")).collect();
        for query in ["document", "documemt", "zzzzzz"] {
            let terms: Vec<&str> = query.split(' ').collect();
            let prepared = candidate::prepare_query(query);
            for label in &labels { assert_eq!(baseline::score_label(label, query, &terms), candidate::score_prepared(label, &prepared)); }
            let before = measure(false, &labels, query);
            let after = measure(true, &labels, query);
            println!("{count},{query},{},{},{},{}", before[17], after[17], before[33], after[33]);
        }
    }
}
