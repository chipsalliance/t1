use std::cmp::max;
use std::collections::HashMap;

use svg::{Document, Node};
use svg::node::element;
use svg::node::element::path::Data;

use crate::Pos;

pub fn print_svg(layout: &HashMap<usize, Pos>) {
    let (x_max, y_max) = layout.values().fold(
        (i64::MIN, i64::MIN),
        |min_pos, &pos| (max(min_pos.0, pos.0), max(min_pos.1, pos.1))
    );

    let l = 100;
    let box_half_width = 20;

    let view_x_min = -0.3 * l as f64;
    let view_y_min = -0.3 * l as f64;
    let view_x_max = (x_max as f64 + 0.3) * l as f64;
    let view_y_max = (y_max as f64 + 0.3) * l as f64;

    let mut document = Document::new()
        .set("viewBox", (
            view_x_min, view_y_min, view_x_max - view_x_min, view_y_max - view_y_min
        )
    );

    for x in 0..x_max+1 {
        document.append(element::Path::new()
            .set("d", Data::new()
                .move_to((x * l, view_y_min))
                .line_to((x * l, view_y_max))
                .close()
            )
            .set("stroke", "black")
            .set("stroke-width", 1)
        )
    }

    for y in 0..y_max+1 {
        document.append(element::Path::new()
            .set("d", Data::new()
                .move_to((view_x_min, y * l))
                .line_to((view_x_max, y * l))
                .close()
            )
            .set("stroke", "black")
            .set("stroke-width", 1)
        )
    }

    for (&idx, &(x, y)) in layout {
        document.append(element::Circle::new()
            .set("cx", x * l)
            .set("cy", y * l)
            .set("r", box_half_width)
            .set("stroke", "black")
            .set("stroke-width", 2)
            .set("fill", "white")
        );
        document.append(element::Text::new()
            .set("x", x * l)
            .set("y", y * l)
            .set("dominant-baseline", "central")
            .set("text-anchor", "middle")
            .set("style", "font-size: 20px; font-family: sans-serif;")
            .add(svg::node::Text::new(format!("{}", idx)))
        )
    }

    println!("{}", document.to_string());
}