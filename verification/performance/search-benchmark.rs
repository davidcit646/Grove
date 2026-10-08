#[path = "search-baseline.rs"] mod baseline;
#[path = "../../rust/grove-core/src/search.rs"] mod candidate;
use std::time::Instant;
use std::hint::black_box;
fn measure(score: fn(&str, &str, &[&str]) -> i32, labels: &[String], query: &str) -> Vec<u128> {
    let terms: Vec<&str> = query.split(' ').collect();
    let mut times = Vec::new();
    for _ in 0..35 {
        let start = Instant::now();
        let scores: Vec<i32> = labels.iter().map(|label| black_box(score(black_box(label), query, &terms))).collect();
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
            for label in &labels { assert_eq!(baseline::score_label(label, query, &terms), candidate::score_label(label, query, &terms)); }
            let before = measure(baseline::score_label, &labels, query);
            let after = measure(candidate::score_label, &labels, query);
            println!("{count},{query},{},{},{},{}", before[17], after[17], before[33], after[33]);
        }
    }
}
