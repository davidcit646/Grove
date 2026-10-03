use std::cmp::Ordering;

// --- Procedural wallpaper renderer ----------------------------------------

const WALLPAPER_COLORS: [[u32; 3]; 3] = [
    // Style 0 -> green, style 1 -> orange, style 2 -> purple
    // (mirrors WallpaperArt.createCanvas palette selection)
    [0xff9ab095, 0xff416e60, 0xff142f30],
    [0xffe5b886, 0xffa25440, 0xff263c37],
    [0xff666788, 0xffc19a98, 0xff18343b],
];

fn argb_to_f(c: u32) -> [f32; 4] {
    [
        ((c >> 24) & 0xff) as f32 / 255.0,
        ((c >> 16) & 0xff) as f32 / 255.0,
        ((c >> 8) & 0xff) as f32 / 255.0,
        (c & 0xff) as f32 / 255.0,
    ]
}

fn f_to_argb(px: [f32; 4]) -> u32 {
    let q = |v: f32| (v.clamp(0.0, 1.0) * 255.0).round() as u32;
    (q(px[0]) << 24) | (q(px[1]) << 16) | (q(px[2]) << 8) | q(px[3])
}

/// SRC_OVER blend; the destination is always opaque here, so alpha stays 1.
fn blend_over(src: u32, dst: [f32; 4]) -> [f32; 4] {
    let s = argb_to_f(src);
    let inv = 1.0 - s[0];
    [
        1.0,
        s[1] * s[0] + dst[1] * inv,
        s[2] * s[0] + dst[2] * inv,
        s[3] * s[0] + dst[3] * inv,
    ]
}

fn lerp_color(a: u32, b: u32, t: f32) -> [f32; 4] {
    let fa = argb_to_f(a);
    let fb = argb_to_f(b);
    [
        1.0,
        fa[1] + (fb[1] - fa[1]) * t,
        fa[2] + (fb[2] - fa[2]) * t,
        fa[3] + (fb[3] - fa[3]) * t,
    ]
}

/// Diagonal gradient matching Android's LinearGradient(0,0 -> w,h)
/// with three evenly distributed color stops.
fn gradient_at(colors: &[u32; 3], x: f32, y: f32, w: f32, h: f32) -> [f32; 4] {
    let t = ((x * w + y * h) / (w * w + h * h)).clamp(0.0, 1.0);
    if t < 0.5 {
        lerp_color(colors[0], colors[1], t * 2.0)
    } else {
        lerp_color(colors[1], colors[2], (t - 0.5) * 2.0)
    }
}

/// Renders one of Grove's three generative wallpaper styles to ARGB pixels.
/// Pure software rasterizer: no Android graphics APIs involved.
pub(crate) fn render_wallpaper(style: usize, width: usize, height: usize) -> Vec<u32> {
    // Mirrors WallpaperArt.createCanvas: style 1 and 2 pick their palettes,
    // everything else falls back to style 0's; mountains draw for any nonzero.
    let colors = &WALLPAPER_COLORS[match style {
        1 => 1,
        2 => 2,
        _ => 0,
    }];
    let w = width as f32;
    let h = height as f32;
    let mut out = Vec::with_capacity(width * height);

    // Precompute the mountain polygons for styles 1 and 2.
    let mountains: Vec<Vec<(f32, f32)>> = if style == 0 {
        Vec::new()
    } else {
        (0..4)
            .map(|i| {
                let i = i as f32;
                let y = 1150.0 + i * 270.0;
                vec![
                    (-100.0, y + 400.0),
                    (260.0, y + 160.0),
                    (570.0, y - 180.0),
                    (840.0, y + 140.0),
                    (1180.0, y + 50.0),
                    (1180.0, 2400.0),
                    (-100.0, 2400.0),
                ]
            })
            .collect()
    };
    let mountain_colors: Vec<u32> = (0..4)
        .map(|i| {
            let (r, g, b) = (55 - i * 10, 78 - i * 12, 76 - i * 10);
            0xff000000 | ((r as u32) << 16) | ((g as u32) << 8) | (b as u32)
        })
        .collect();

    // Scanline spans for the mountains, computed once per row.
    let spans: Vec<Vec<(i32, i32, u32)>> = (0..height)
        .map(|row| {
            let y = row as f32 + 0.5;
            let mut row_spans = Vec::new();
            for (poly, &color) in mountains.iter().zip(mountain_colors.iter()) {
                let mut xs = Vec::new();
                for e in 0..poly.len() {
                    let (x1, y1) = poly[e];
                    let (x2, y2) = poly[(e + 1) % poly.len()];
                    if (y1 <= y && y < y2) || (y2 <= y && y < y1) {
                        xs.push(x1 + (y - y1) / (y2 - y1) * (x2 - x1));
                    }
                }
                xs.sort_by(|a, b| a.partial_cmp(b).unwrap_or(Ordering::Equal));
                for pair in xs.chunks_exact(2) {
                    row_spans.push((pair[0].floor() as i32, pair[1].ceil() as i32, color));
                }
            }
            row_spans
        })
        .collect();

    for row in 0..height {
        let y = row as f32 + 0.5;
        for col in 0..width {
            let x = col as f32 + 0.5;
            let mut px = gradient_at(colors, x, y, w, h);

            // Soft accent circle.
            let dx = x - 800.0;
            let dy = y - 740.0;
            if dx * dx + dy * dy <= 180.0 * 180.0 {
                px = blend_over(0x44fff1cc, px);
            }

            if style == 0 {
                // Six translucent ellipses, back to front.
                for i in 0..6 {
                    let i = i as f32;
                    let left = -500.0 + i * 100.0;
                    let top = 1100.0 + i * 150.0;
                    let right = 1600.0;
                    let bottom = 2900.0 + i * 130.0;
                    let cx = (left + right) / 2.0;
                    let cy = (top + bottom) / 2.0;
                    let rx = (right - left) / 2.0;
                    let ry = (bottom - top) / 2.0;
                    let ex = (x - cx) / rx;
                    let ey = (y - cy) / ry;
                    if ex * ex + ey * ey <= 1.0 {
                        let a = 45 + i as u32 * 20;
                        px = blend_over((a << 24) | (14 << 16) | (50 << 8) | 40, px);
                    }
                }
            } else {
                // Later mountains draw on top of earlier ones: the last
                // matching span wins.
                for &(x0, x1, color) in spans[row].iter().rev() {
                    if (col as i32) >= x0 && (col as i32) < x1 {
                        px = blend_over(color, px);
                        break;
                    }
                }
            }

            out.push(f_to_argb(px));
        }
    }
    out
}

