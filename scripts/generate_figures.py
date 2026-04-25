#!/usr/bin/env python3
"""
Generate report-ready SVG figures from the analysis CSV outputs.

This script uses only Python's standard library so it works without pandas or
matplotlib. It
 the tables produced by Member1Analysis and writes SVG
charts into outputs/figures/.
"""

from __future__ import annotations

import csv
import math
from html import escape
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TABLE_DIR = ROOT / "outputs" / "tables"
FIGURE_DIR = ROOT / "outputs" / "figures"

METRICS_CSV = TABLE_DIR / "member1_metrics.csv"
HIDDEN_BW_CSV = TABLE_DIR / "hidden_gems_betweenness.csv"
HIDDEN_PR_CSV = TABLE_DIR / "hidden_gems_pagerank.csv"
COMMUNITY_SUMMARY_CSV = TABLE_DIR / "community_summary.csv"
PROPAGATION_SUMMARY_CSV = TABLE_DIR / "propagation_summary.csv"
PAPER_COMMUNITIES_CSV = ROOT / "outputs" / "processed" / "paper_communities.csv"
EDGES_WCC_CSV = ROOT / "outputs" / "processed" / "edges_wcc.csv"

WIDTH = 980
HEIGHT = 640
BACKGROUND = "#fffaf2"
TEXT = "#22313f"
MUTED = "#5d6d7e"
GRID = "#dfd5c7"
POINT = "#b8c5d6"
PAGE_RANK = "#2f6f7f"
BETWEENNESS = "#b45a2f"
PAGE_RANK_HIGHLIGHT = "#0f766e"
BETWEENNESS_HIGHLIGHT = "#c2410c"
BAR_FILL = "#d97706"
BAR_FILL_ALT = "#0f766e"
EDGE_COLOR = "#46556a"
NETWORK_STROKE = "#213547"


def read_metrics(path: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            rows.append(
                {
                    "paper_id": row["paper_id"],
                    "citation_count": int(row["citation_count"]),
                    "pagerank": float(row["pagerank"]),
                    "betweenness": float(row["betweenness"]),
                    "closeness": float(row["closeness"]) if row["closeness"] else None,
                    "year": row["year"] or None,
                }
            )
    return rows


def read_hidden(path: Path, score_key: str) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            rows.append(
                {
                    "paper_id": row["paper_id"],
                    "citation_count": int(row["citation_count"]),
                    score_key: float(row[score_key]),
                    "year": row["year"] or None,
                }
            )
    return rows


def read_community_summary(path: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            rows.append(
                {
                    "community_id": int(row["community_id"]),
                    "size": int(row["size"]),
                    "avg_citations": float(row["avg_citations"]),
                    "avg_pagerank": float(row["avg_pagerank"]),
                    "avg_betweenness": float(row["avg_betweenness"]),
                    "avg_total_reach": float(row["avg_total_reach"]),
                    "hidden_bw_count": int(row["hidden_bw_count"]),
                    "hidden_pr_count": int(row["hidden_pr_count"]),
                    "top_pagerank_paper": row["top_pagerank_paper"],
                    "top_betweenness_paper": row["top_betweenness_paper"],
                    "top_reach_paper": row["top_reach_paper"],
                }
            )
    return rows


def read_paper_communities(path: Path) -> dict[str, int]:
    mapping: dict[str, int] = {}
    with path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            mapping[row["paper_id"]] = int(row["community_id"])
    return mapping


def ensure_inputs_exist() -> None:
    required = (
        METRICS_CSV,
        HIDDEN_BW_CSV,
        HIDDEN_PR_CSV,
        COMMUNITY_SUMMARY_CSV,
        PAPER_COMMUNITIES_CSV,
        EDGES_WCC_CSV,
    )
    missing = [path for path in required if not path.exists()]
    if missing:
        missing_text = ", ".join(str(path) for path in missing)
        raise FileNotFoundError(f"Missing required CSV file(s): {missing_text}")


def svg_header(width: int, height: int, title: str) -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" role="img" aria-label="{escape(title)}">'
        "<style>"
        f".title {{ font: 700 28px Georgia, 'Times New Roman', serif; fill: {TEXT}; }}"
        f".subtitle {{ font: 15px Helvetica, Arial, sans-serif; fill: {MUTED}; }}"
        f".axis-label {{ font: 14px Helvetica, Arial, sans-serif; fill: {TEXT}; }}"
        f".tick {{ font: 12px Helvetica, Arial, sans-serif; fill: {MUTED}; }}"
        f".annotation {{ font: 12px Helvetica, Arial, sans-serif; fill: {TEXT}; "
        f"paint-order: stroke; stroke: {BACKGROUND}; stroke-width: 4px; stroke-linejoin: round; }}"
        f".bar-label {{ font: 13px Helvetica, Arial, sans-serif; fill: {TEXT}; }}"
        "</style>"
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="{BACKGROUND}"/>'
    )


def svg_footer() -> str:
    return "</svg>"


def write_svg(path: Path, body: list[str], title: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = [svg_header(WIDTH, HEIGHT, title)]
    content.extend(body)
    content.append(svg_footer())
    path.write_text("\n".join(content), encoding="utf-8")


def metric_format(value: float) -> str:
    if value >= 0.01:
        return f"{value:.4f}"
    if value >= 0.0001:
        return f"{value:.6f}"
    return f"{value:.2e}"


def citation_ticks(max_citations: int) -> list[int]:
    candidates = [0, 1, 3, 10, 30, 100, 300, 1000, 3000]
    ticks = [value for value in candidates if value <= max_citations]
    if not ticks or ticks[-1] != max_citations:
        ticks.append(max_citations)
    return ticks


def log_ticks(min_value: float, max_value: float) -> list[float]:
    ticks: list[float] = []
    start_exp = math.floor(math.log10(min_value))
    end_exp = math.ceil(math.log10(max_value))
    for exponent in range(start_exp, end_exp + 1):
        for multiplier in (1, 3):
            value = multiplier * (10 ** exponent)
            if min_value <= value <= max_value * 1.000001:
                ticks.append(value)
    if not ticks:
        ticks = [min_value, max_value]
    return ticks


def add_frame(body: list[str], left: int, top: int, width: int, height: int) -> None:
    body.append(
        f'<rect x="{left}" y="{top}" width="{width}" height="{height}" '
        f'fill="none" stroke="{TEXT}" stroke-width="1.2"/>'
    )


def draw_scatter(
    all_rows: list[dict[str, object]],
    highlighted_rows: list[dict[str, object]],
    metric_key: str,
    title: str,
    subtitle: str,
    output_name: str,
    point_color: str,
    highlight_color: str,
) -> None:
    left = 110
    top = 110
    plot_width = 760
    plot_height = 430
    bottom = top + plot_height
    right = left + plot_width

    citations = [int(row["citation_count"]) for row in all_rows]
    metric_values = [float(row[metric_key]) for row in all_rows]
    positive_metrics = [value for value in metric_values if value > 0]
    epsilon = min(positive_metrics) / 2.0 if positive_metrics else 1e-12

    min_x = 0.0
    max_x = math.log10(max(citations) + 1.0)
    min_metric = max(min(value for value in metric_values if value >= 0.0), epsilon)
    max_metric = max(metric_values)
    min_y = math.log10(min_metric if min_metric > 0 else epsilon)
    max_y = math.log10(max_metric)

    def x_scale(citation_count: int) -> float:
        value = math.log10(citation_count + 1.0)
        return left + (value - min_x) / (max_x - min_x) * plot_width

    def y_scale(score: float) -> float:
        value = math.log10(max(score, epsilon))
        return bottom - (value - min_y) / (max_y - min_y) * plot_height

    body: list[str] = []
    body.append(f'<text class="title" x="{left}" y="54">{escape(title)}</text>')
    body.append(f'<text class="subtitle" x="{left}" y="82">{escape(subtitle)}</text>')

    for tick in citation_ticks(max(citations)):
        tick_x = x_scale(tick)
        body.append(
            f'<line x1="{tick_x:.2f}" y1="{top}" x2="{tick_x:.2f}" y2="{bottom}" '
            f'stroke="{GRID}" stroke-width="1"/>'
        )
        body.append(f'<text class="tick" x="{tick_x:.2f}" y="{bottom + 24}" text-anchor="middle">{tick}</text>')

    for tick in log_ticks(10 ** min_y, 10 ** max_y):
        tick_y = y_scale(tick)
        body.append(
            f'<line x1="{left}" y1="{tick_y:.2f}" x2="{right}" y2="{tick_y:.2f}" '
            f'stroke="{GRID}" stroke-width="1"/>'
        )
        body.append(
            f'<text class="tick" x="{left - 10}" y="{tick_y + 4:.2f}" text-anchor="end">{metric_format(tick)}</text>'
        )

    add_frame(body, left, top, plot_width, plot_height)

    for row in all_rows:
        cx = x_scale(int(row["citation_count"]))
        cy = y_scale(float(row[metric_key]))
        body.append(
            f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="2.2" fill="{point_color}" opacity="0.35"/>'
        )

    for row in highlighted_rows:
        cx = x_scale(int(row["citation_count"]))
        cy = y_scale(float(row[metric_key]))
        body.append(
            f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="4.4" fill="{highlight_color}" '
            f'stroke="{BACKGROUND}" stroke-width="1.5"/>'
        )

    label_offsets = [(10, -8), (10, 14), (-10, -8), (-10, 14), (14, 2), (-14, 2)]
    for index, row in enumerate(highlighted_rows[:8]):
        cx = x_scale(int(row["citation_count"]))
        cy = y_scale(float(row[metric_key]))
        dx, dy = label_offsets[index % len(label_offsets)]
        anchor = "start" if dx >= 0 else "end"
        label = str(row["paper_id"])
        body.append(
            f'<text class="annotation" x="{cx + dx:.2f}" y="{cy + dy:.2f}" text-anchor="{anchor}">{escape(label)}</text>'
        )

    body.append(
        f'<text class="axis-label" x="{left + plot_width / 2:.2f}" y="{HEIGHT - 40}" text-anchor="middle">'
        "Citation count (log scale)"
        "</text>"
    )
    body.append(
        f'<text class="axis-label" transform="translate(34 {top + plot_height / 2:.2f}) rotate(-90)" text-anchor="middle">'
        f'{escape(metric_key.capitalize())} (log scale)'
        "</text>"
    )

    legend_x = right - 170
    legend_y = top - 10
    body.append(f'<circle cx="{legend_x}" cy="{legend_y}" r="4" fill="{point_color}" opacity="0.65"/>')
    body.append(f'<text class="tick" x="{legend_x + 12}" y="{legend_y + 4}">All papers</text>')
    body.append(f'<circle cx="{legend_x + 100}" cy="{legend_y}" r="4" fill="{highlight_color}"/>')
    body.append(f'<text class="tick" x="{legend_x + 112}" y="{legend_y + 4}">Hidden gems</text>')

    write_svg(FIGURE_DIR / output_name, body, title)


def draw_horizontal_bar_chart(
    rows: list[dict[str, object]],
    metric_key: str,
    title: str,
    subtitle: str,
    output_name: str,
    fill: str,
) -> None:
    top_rows = rows[:15]
    left = 220
    top = 120
    bar_area_width = 620
    bar_height = 24
    gap = 14
    max_score = max(float(row[metric_key]) for row in top_rows)

    body: list[str] = []
    body.append(f'<text class="title" x="{left}" y="54">{escape(title)}</text>')
    body.append(f'<text class="subtitle" x="{left}" y="82">{escape(subtitle)}</text>')

    for index, row in enumerate(top_rows):
        y = top + index * (bar_height + gap)
        score = float(row[metric_key])
        bar_width = 0.0 if max_score == 0 else bar_area_width * score / max_score
        label = str(row["paper_id"])
        citations = int(row["citation_count"])
        year = row["year"] or "?"

        body.append(
            f'<rect x="{left}" y="{y}" width="{bar_area_width}" height="{bar_height}" rx="6" '
            f'fill="#efe7db"/>'
        )
        body.append(
            f'<rect x="{left}" y="{y}" width="{bar_width:.2f}" height="{bar_height}" rx="6" '
            f'fill="{fill}"/>'
        )
        body.append(
            f'<text class="bar-label" x="{left - 12}" y="{y + 16}" text-anchor="end">{escape(label)}</text>'
        )
        body.append(
            f'<text class="tick" x="{left + bar_area_width + 12}" y="{y + 16}">{metric_format(score)}</text>'
        )
        body.append(
            f'<text class="tick" x="{left + bar_area_width + 110}" y="{y + 16}">cites={citations}, year={year}</text>'
        )

    body.append(
        f'<text class="axis-label" x="{left + bar_area_width / 2:.2f}" y="{HEIGHT - 34}" text-anchor="middle">'
        f"Highest {escape(metric_key)} scores"
        "</text>"
    )

    write_svg(FIGURE_DIR / output_name, body, title)


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def interpolate_color(low_hex: str, high_hex: str, t: float) -> str:
    t = clamp(t, 0.0, 1.0)

    def parse(hex_color: str) -> tuple[int, int, int]:
        hex_color = hex_color.lstrip("#")
        return int(hex_color[0:2], 16), int(hex_color[2:4], 16), int(hex_color[4:6], 16)

    low = parse(low_hex)
    high = parse(high_hex)
    channels = [
        round(low[index] + (high[index] - low[index]) * t)
        for index in range(3)
    ]
    return "#{:02x}{:02x}{:02x}".format(*channels)


def aggregate_community_edges(
    edges_path: Path,
    paper_to_community: dict[str, int],
    included_communities: set[int],
) -> dict[tuple[int, int], int]:
    pair_weights: dict[tuple[int, int], int] = {}

    with edges_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            source = row["source_paper_id"]
            target = row["target_paper_id"]

            source_community = paper_to_community.get(source)
            target_community = paper_to_community.get(target)
            if source_community is None or target_community is None:
                continue
            if source_community == target_community:
                continue
            if source_community not in included_communities or target_community not in included_communities:
                continue

            key = tuple(sorted((source_community, target_community)))
            pair_weights[key] = pair_weights.get(key, 0) + 1

    return pair_weights


def force_layout(
    node_ids: list[int],
    edge_weights: dict[tuple[int, int], int],
    bounds: tuple[float, float, float, float],
    radii: dict[int, float],
) -> dict[int, tuple[float, float]]:
    left, top, width, height = bounds
    n = len(node_ids)
    center_x = left + width / 2.0
    center_y = top + height / 2.0

    positions: dict[int, list[float]] = {}
    for index, node_id in enumerate(node_ids):
        angle = 2.0 * math.pi * index / max(n, 1)
        radius = min(width, height) * (0.28 + 0.12 * (index % 3) / 2.0)
        positions[node_id] = [
            center_x + radius * math.cos(angle),
            center_y + radius * math.sin(angle),
        ]

    area = width * height
    k = math.sqrt(area / max(n, 1)) * 0.55
    max_weight = max(edge_weights.values(), default=1)
    temperature = min(width, height) * 0.12

    for _ in range(220):
        disp: dict[int, list[float]] = {node_id: [0.0, 0.0] for node_id in node_ids}

        for i, source in enumerate(node_ids):
            x1, y1 = positions[source]
            for target in node_ids[i + 1:]:
                x2, y2 = positions[target]
                dx = x1 - x2
                dy = y1 - y2
                dist = math.hypot(dx, dy) + 0.01
                repulsion = (k * k) / dist
                ux = dx / dist
                uy = dy / dist
                disp[source][0] += ux * repulsion
                disp[source][1] += uy * repulsion
                disp[target][0] -= ux * repulsion
                disp[target][1] -= uy * repulsion

        for (source, target), weight in edge_weights.items():
            x1, y1 = positions[source]
            x2, y2 = positions[target]
            dx = x1 - x2
            dy = y1 - y2
            dist = math.hypot(dx, dy) + 0.01
            ux = dx / dist
            uy = dy / dist
            strength = 0.6 + 1.6 * weight / max_weight
            attraction = (dist * dist / k) * strength * 0.0018
            disp[source][0] -= ux * attraction
            disp[source][1] -= uy * attraction
            disp[target][0] += ux * attraction
            disp[target][1] += uy * attraction

        for node_id in node_ids:
            x, y = positions[node_id]
            dx, dy = disp[node_id]
            dist = math.hypot(dx, dy)
            if dist > 0:
                step = min(dist, temperature)
                x += dx / dist * step
                y += dy / dist * step

            margin_x = radii[node_id] + 18
            margin_y = radii[node_id] + 18
            positions[node_id][0] = clamp(x, left + margin_x, left + width - margin_x)
            positions[node_id][1] = clamp(y, top + margin_y, top + height - margin_y)

        temperature *= 0.97

    return {node_id: (positions[node_id][0], positions[node_id][1]) for node_id in node_ids}


def draw_community_meta_network(
    community_rows: list[dict[str, object]],
    paper_to_community: dict[str, int],
    title: str,
    subtitle: str,
    output_name: str,
) -> None:
    selected = community_rows[:14]
    selected_ids = [int(row["community_id"]) for row in selected]
    selected_id_set = set(selected_ids)
    by_id = {int(row["community_id"]): row for row in selected}

    pair_weights = aggregate_community_edges(EDGES_WCC_CSV, paper_to_community, selected_id_set)
    kept_edges = sorted(pair_weights.items(), key=lambda item: item[1], reverse=True)[:26]

    left = 55
    top = 118
    network_width = 570
    network_height = 455
    sidebar_x = 665
    sidebar_width = 255

    sizes = [int(row["size"]) for row in selected]
    reaches = [float(row["avg_total_reach"]) for row in selected]
    min_size = min(sizes)
    max_size = max(sizes)
    min_reach = min(reaches)
    max_reach = max(reaches)
    max_edge = max((weight for _, weight in kept_edges), default=1)

    radii: dict[int, float] = {}
    for node_id in selected_ids:
        size = int(by_id[node_id]["size"])
        if max_size == min_size:
            radii[node_id] = 32.0
        else:
            t = (math.sqrt(size) - math.sqrt(min_size)) / (math.sqrt(max_size) - math.sqrt(min_size))
            radii[node_id] = 18.0 + t * 38.0

    positions = force_layout(selected_ids, dict(kept_edges), (left, top, network_width, network_height), radii)

    body: list[str] = []
    body.append(f'<text class="title" x="{left}" y="54">{escape(title)}</text>')
    body.append(f'<text class="subtitle" x="{left}" y="82">{escape(subtitle)}</text>')

    body.append(
        f'<rect x="{left}" y="{top}" width="{network_width}" height="{network_height}" rx="20" '
        f'fill="#f4ecdf" stroke="#d7cbbb" stroke-width="1.2"/>'
    )
    body.append(
        f'<rect x="{sidebar_x}" y="{top}" width="{sidebar_width}" height="{network_height}" rx="20" '
        f'fill="#f4ecdf" stroke="#d7cbbb" stroke-width="1.2"/>'
    )

    for (source, target), weight in kept_edges:
        x1, y1 = positions[source]
        x2, y2 = positions[target]
        width = 1.2 + 8.0 * weight / max_edge
        opacity = 0.15 + 0.55 * weight / max_edge
        body.append(
            f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" '
            f'stroke="{EDGE_COLOR}" stroke-width="{width:.2f}" stroke-linecap="round" opacity="{opacity:.3f}"/>'
        )

    for node_id in selected_ids:
        row = by_id[node_id]
        x, y = positions[node_id]
        reach = float(row["avg_total_reach"])
        if max_reach == min_reach:
            t = 0.5
        else:
            t = (reach - min_reach) / (max_reach - min_reach)
        fill = interpolate_color("#f59e0b", "#0f766e", t)
        radius = radii[node_id]

        body.append(
            f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{radius:.2f}" fill="{fill}" '
            f'stroke="{NETWORK_STROKE}" stroke-width="2.2" opacity="0.95"/>'
        )
        body.append(
            f'<text x="{x:.2f}" y="{y + 5:.2f}" text-anchor="middle" '
            f'style="font: 700 16px Helvetica, Arial, sans-serif; fill: white;">C{node_id}</text>'
        )
        body.append(
            f'<text class="tick" x="{x:.2f}" y="{y + radius + 18:.2f}" text-anchor="middle">'
            f'{int(row["size"]):,}'
            "</text>"
        )

    body.append(
        f'<text class="axis-label" x="{left + 20}" y="{top + 28}">Top communities by size</text>'
    )
    body.append(
        f'<text class="tick" x="{left + 20}" y="{top + 48}">Node size = community size, node color = average downstream reach, edge width = cross-community citation volume</text>'
    )

    legend_top = top + 34
    body.append(f'<text class="axis-label" x="{sidebar_x + 22}" y="{legend_top}">Community Key</text>')
    body.append(
        f'<text class="tick" x="{sidebar_x + 22}" y="{legend_top + 20}">Without titles or abstracts, labels are structural rather than topical.</text>'
    )

    for index, row in enumerate(selected[:12]):
        y = legend_top + 52 + index * 31
        community_id = int(row["community_id"])
        size = int(row["size"])
        hidden_total = int(row["hidden_bw_count"]) + int(row["hidden_pr_count"])
        anchor = str(row["top_pagerank_paper"])

        body.append(
            f'<text class="bar-label" x="{sidebar_x + 22}" y="{y}">C{community_id}</text>'
        )
        body.append(
            f'<text class="tick" x="{sidebar_x + 58}" y="{y}">size={size:,} | hidden={hidden_total} | anchor={escape(anchor)}</text>'
        )

    scale_x = sidebar_x + 24
    scale_y = top + network_height - 90
    body.append(f'<text class="axis-label" x="{scale_x}" y="{scale_y}">Reach Color Scale</text>')
    for i in range(8):
        t = i / 7.0
        body.append(
            f'<rect x="{scale_x + i * 18}" y="{scale_y + 12}" width="18" height="12" '
            f'fill="{interpolate_color("#f59e0b", "#0f766e", t)}" stroke="none"/>'
        )
    body.append(f'<text class="tick" x="{scale_x}" y="{scale_y + 40}">lower reach</text>')
    body.append(f'<text class="tick" x="{scale_x + 96}" y="{scale_y + 40}">higher reach</text>')

    write_svg(FIGURE_DIR / output_name, body, title)


def main() -> None:
    ensure_inputs_exist()

    metrics_rows = read_metrics(METRICS_CSV)
    hidden_bw_rows = read_hidden(HIDDEN_BW_CSV, "betweenness")
    hidden_pr_rows = read_hidden(HIDDEN_PR_CSV, "pagerank")
    community_rows = read_community_summary(COMMUNITY_SUMMARY_CSV)
    paper_to_community = read_paper_communities(PAPER_COMMUNITIES_CSV)

    hidden_bw_ids = {str(row["paper_id"]) for row in hidden_bw_rows}
    hidden_pr_ids = {str(row["paper_id"]) for row in hidden_pr_rows}

    metrics_hidden_bw = [row for row in metrics_rows if str(row["paper_id"]) in hidden_bw_ids]
    metrics_hidden_pr = [row for row in metrics_rows if str(row["paper_id"]) in hidden_pr_ids]

    metrics_hidden_bw.sort(key=lambda row: float(row["betweenness"]), reverse=True)
    metrics_hidden_pr.sort(key=lambda row: float(row["pagerank"]), reverse=True)

    hidden_bw_rows.sort(key=lambda row: float(row["betweenness"]), reverse=True)
    hidden_pr_rows.sort(key=lambda row: float(row["pagerank"]), reverse=True)
    community_rows.sort(key=lambda row: int(row["size"]), reverse=True)

    FIGURE_DIR.mkdir(parents=True, exist_ok=True)

    draw_scatter(
        metrics_rows,
        metrics_hidden_pr,
        "pagerank",
        "Citation Count vs. PageRank",
        "All papers are shown in blue-gray; hidden PageRank gems are highlighted in teal.",
        "citation_vs_pagerank.svg",
        PAGE_RANK,
        PAGE_RANK_HIGHLIGHT,
    )

    draw_scatter(
        metrics_rows,
        metrics_hidden_bw,
        "betweenness",
        "Citation Count vs. Betweenness",
        "Low-citation papers with unusually high bridge importance stand out in orange.",
        "citation_vs_betweenness.svg",
        BETWEENNESS,
        BETWEENNESS_HIGHLIGHT,
    )

    draw_horizontal_bar_chart(
        hidden_bw_rows,
        "betweenness",
        "Top Hidden Gems by Betweenness",
        "These papers have below-median citation counts but still occupy structurally central positions.",
        "top_hidden_gems_betweenness.svg",
        BAR_FILL,
    )

    draw_horizontal_bar_chart(
        hidden_pr_rows,
        "pagerank",
        "Top Hidden Gems by PageRank",
        "These papers receive unusually strong structural influence relative to their raw citation counts.",
        "top_hidden_gems_pagerank.svg",
        BAR_FILL_ALT,
    )

    draw_community_meta_network(
        community_rows,
        paper_to_community,
        "Community Meta-Network",
        "Each node is a detected community; this view shows how the largest research clusters connect through citations.",
        "community_meta_network.svg",
    )

    print("Wrote figures to", FIGURE_DIR)
    for path in sorted(FIGURE_DIR.glob("*.svg")):
        print(" -", path.name)


if __name__ == "__main__":
    main()
