mod svg_gen;

use std::cmp::{min, max};
use std::collections::HashMap;

use rand::{Rng, prelude::SliceRandom, distributions::Uniform, SeedableRng};

use clap::{Parser, ValueEnum};

use kdam::par_tqdm;

use rayon::prelude::*;

use crate::svg_gen::print_svg;

#[derive(ValueEnum, Clone, Copy, Debug)]
enum OutputFormat {
    Text,
    Json,
    Svg,
}

#[derive(Parser, Debug)]
struct Args {
    #[arg(short)]
    n: u64,

    #[arg(long)]
    iter: isize,

    #[arg(long, default_value_t = 19260817)]
    seed: u64,

    #[arg(long, value_enum, default_value_t = OutputFormat::Text)]
    output_format: OutputFormat,

    #[arg(long)]
    /// if set, will not stop until find a d smaller or equal to that
    search_until_d: Option<i64>,

    #[arg(long)]
    /// the maximum layout width
    width_limit: Option<u64>,
}

fn main() {
    let args = Args::parse();

    let mut searcher = LayoutSearcher::new(args.n, args.width_limit);
    searcher.search(args.iter, args.seed, args.search_until_d);

    let layout = searcher.best_layout.unwrap();
    let min_pos = layout.values().fold(
        (i64::MAX, i64::MAX),
        |min_pos, &pos| (min(min_pos.0, pos.0), min(min_pos.1, pos.1))
    );  // transform the layout to make coordinates non-negative
    let transformed_layout: HashMap<usize, Pos> = layout.iter().map(|(&k, &v)| {
        (k, (v.0 - min_pos.0, v.1 - min_pos.1))
    }).collect();

    eprintln!("found a layout with the max distance: {}", searcher.best_d);
    match args.output_format {
        OutputFormat::Json => {
            println!("{}", serde_json::to_string_pretty(&transformed_layout).unwrap());
        },
        OutputFormat::Text => {
            for idx in 0..args.n as usize {
                let (x, y) = transformed_layout[&idx];
                println!("{} -> ({}, {})", idx, x, y);
            }
        }
        OutputFormat::Svg => {
            print_svg(&transformed_layout);
        }
    }
}

struct LaneNode {
    neighbors: Vec<usize>,
}

type Pos = (i64, i64);
fn dist(p1: Pos, p2: Pos) -> i64 {
    (p1.0 - p2.0).abs() + (p1.1 - p2.1).abs()
}

struct LayoutSearcher {
    n: u64,

    best_d: i64,
    best_layout: Option<HashMap<usize, Pos>>,

    nodes: Vec<LaneNode>,

    y_limit_min: i64,
    y_limit_max: i64,
}

impl LayoutSearcher {
    fn new(n: u64, width_limit: Option<u64>) -> Self {
        let mut nodes: Vec<LaneNode> = Vec::new();
        nodes.reserve(n as usize);
        for idx in 0..n {
            nodes.push(LaneNode{
                neighbors: vec![
                    ((2 * idx) % n) as usize,
                    ((2 * idx + 1) % n) as usize,
                    (idx / 2) as usize,
                    ((idx + n) / 2) as usize,
                ],
            })
        }

        assert!(width_limit.map_or(true, |x| x > 0u64), "error: width_limit should be non-zero if specified");
        let y_limit_min = width_limit.map_or(i64::MIN, |l| -(l as i64 / 2));
        let y_limit_max = width_limit.map_or(i64::MAX, |l| (l as i64 - 1) / 2);
        assert!(width_limit.is_none() || y_limit_max - y_limit_min + 1 == width_limit.unwrap() as i64);
        Self{
            n, best_d: i64::MAX, best_layout: None, nodes,
            y_limit_min, y_limit_max,
        }
    }

    fn search_once(&self, seed: u64) -> (i64, HashMap<usize, Pos>) {
        let mut rng = rand::rngs::StdRng::seed_from_u64(seed);
        let mut idx_permu: Vec<usize> = (0..self.n as usize).collect();
        idx_permu.shuffle(&mut rng);
        let mut occupied: HashMap<Pos, usize> = HashMap::new(); // (x, y) -> idx
        let mut layout: HashMap<usize, Pos> = HashMap::new(); // idx -> (x, y)

        occupied.insert((0, 0), idx_permu[0]);
        layout.insert(idx_permu[0], (0, 0));

        let mut d_for_layout = i64::MIN;

        let (mut xmin, mut xmax, mut ymin, mut ymax) = (0i64, 0i64, 0i64, 0i64);

        for (count, shuffle_idx) in idx_permu.iter().enumerate() {
            let pos = if count == 0 {
                (0i64, 0i64)
            } else {
                let cur_node = self.nodes.get(*shuffle_idx).unwrap();
                let placed_neighbors_pos: Vec<(i64, i64)> = cur_node.neighbors.iter().filter(|neigh| {
                    layout.contains_key(neigh)
                }).map(|neigh|
                    layout[neigh]
                ).collect();

                if placed_neighbors_pos.is_empty() {
                    // sample a random empty point in span range
                    let span_range_size = (xmax - xmin + 1) * (ymax - ymin + 1);
                    let enlarge = (count > (0.9 * span_range_size as f64).floor() as usize) as i64;  // enlarge by 1 if too full
                    let x_range = Uniform::from(xmin - enlarge..xmax + enlarge + 1);

                    // handle y limit
                    let y_max_limited = min(self.y_limit_max, ymax + enlarge);
                    let y_min_limited = max(self.y_limit_min, ymin - enlarge);
                    let y_range = Uniform::from(y_min_limited..y_max_limited + 1);
                    loop {
                        let x = rng.sample(x_range);
                        let y = rng.sample(y_range);
                        if !occupied.contains_key(&(x, y)) {
                            break (x, y)
                        }
                    }

                } else {
                    let mut best_max_dist = i64::MAX;
                    let mut best_pos: Option<Pos> = None;
                    for x in xmin-1..xmax+2 {
                        // handle y limit
                        let y_max_limited = min(self.y_limit_max, ymax + 1);
                        let y_min_limited = max(self.y_limit_min, ymin - 1);
                        for y in y_min_limited..y_max_limited+1 {
                            let candidate_pos = (x, y);
                            if !occupied.contains_key(&candidate_pos) {
                                let max_dist_to_neighbors: i64 = placed_neighbors_pos.iter()
                                    .map(|neigh_pos| dist(*neigh_pos, candidate_pos))
                                    .max().unwrap();
                                if max_dist_to_neighbors < best_max_dist {
                                    best_max_dist = max_dist_to_neighbors;
                                    best_pos = Some(candidate_pos);
                                }
                            };
                        }
                    }
                    d_for_layout = max(best_max_dist, d_for_layout);
                    best_pos.unwrap()
                }
            };
            occupied.insert(pos, *shuffle_idx);
            layout.insert(*shuffle_idx, pos);
            xmin = min(xmin, pos.0);
            xmax = max(xmax, pos.0);
            ymin = min(ymin, pos.1);
            ymax = max(ymax, pos.1);
        };

        (d_for_layout, layout)
    }

    fn search(&mut self, iter_times: isize, seed: u64, until: Option<i64>) {
        loop {
            let (min_d, best_layout) = par_tqdm!((0..iter_times).into_par_iter())
                .map(|i| self.search_once(seed ^ i as u64))  // propagate seed by xor
                .reduce(|| (i64::MAX, HashMap::new()), |reduced, cur| {
                    let (d, layout) = cur;
                    let (min_d, best_layout) = reduced;
                    if d < min_d {
                        (d, layout)
                    } else {
                        (min_d, best_layout)
                    }
                });
            self.best_d = min_d;
            self.best_layout = Some(best_layout);

            if until.is_none() || until.unwrap() >= self.best_d {
                break
            }
        }
    }
}
