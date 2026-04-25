# Academic Citation Network Analysis

This project analyzes the SNAP `cit-HepPh` citation network to compare raw citation counts with graph-based importance measures. Our goal is to identify papers that are structurally influential even when they do not have especially high citation counts.

## Project Goal

We model the citation network as a directed graph:

- Nodes are papers.
- A directed edge `u -> v` means paper `u` cites paper `v`.
- A paper's in-degree is its raw citation count.

The analysis compares:

- citation count
- PageRank
- betweenness centrality
- closeness centrality
- label-propagation community structure
- downstream citation reach within 3 hops

We also identify "hidden gems": papers with below-median citation counts but unusually high structural importance.

## Dataset

Source: SNAP `cit-HepPh` citation network

- Raw edge list: [`data/cit-HepPh.txt`](data/cit-HepPh.txt)
- Publication dates: [`data/cit-HepPh-dates.txt`](data/cit-HepPh-dates.txt)

## Preprocessing Pipeline

The analysis pipeline performs the following steps:

1. Load the raw citation edge list into a directed graph.
2. Attach publication years from the dates file when available.
3. Extract the largest weakly connected component (WCC).
4. Run graph algorithms on the WCC.
5. Detect communities on the undirected view of the WCC with label propagation.
6. Reverse the citation graph and measure downstream reach within 1, 2, and 3 hops.
7. Export tables, processed datasets, and figures.

Restricting to the largest WCC removes small disconnected fragments and keeps the analysis focused on the main citation network.

## Methods

- `PageRank`: measures how much structural importance a paper receives from other citing papers.
- `Betweenness centrality`: estimates how often a paper lies on shortest paths and acts as a bridge between parts of the network.
- `Closeness centrality`: measures how quickly a paper can reach other papers through directed citation paths.
- `Community detection`: uses label propagation on the undirected citation network to assign each paper to a research community.
- `Propagation reach`: counts how many downstream papers are reached at 1, 2, and 3 hops in the reversed citation graph.
- `Hidden gems`: papers with citation count at or below the median, but with PageRank or betweenness in the top 10%.

Implementation files:

- [`src/CitationGraph.java`](src/CitationGraph.java)
- [`src/GraphLoader.java`](src/GraphLoader.java)
- [`src/PageRank.java`](src/PageRank.java)
- [`src/BetweennessCentrality.java`](src/BetweennessCentrality.java)
- [`src/ClosenessCentrality.java`](src/ClosenessCentrality.java)
- [`src/CommunityDetection.java`](src/CommunityDetection.java)
- [`src/Member1Analysis.java`](src/Member1Analysis.java)
- [`src/PropagationAnalysis.java`](src/PropagationAnalysis.java)

## How To Run

Compile the Java code:

```bash
bash compile.sh
```

Run the main analysis:

```bash
bash run_member1.sh
```

Generate the SVG figures from the CSV outputs:

```bash
bash run_figures.sh
```

## Outputs

### Tables

Location: [`outputs/tables`](outputs/tables)

- `member1_metrics.csv`: paper-level metrics for all papers in the WCC
- `hidden_gems_betweenness.csv`: low-citation papers with top betweenness scores
- `hidden_gems_pagerank.csv`: low-citation papers with top PageRank scores
- `community_summary.csv`: community-level aggregates, hidden-gem counts, and representative papers
- `propagation_summary.csv`: paper-level downstream reach statistics within 3 hops

### Processed Datasets

Location: [`outputs/processed`](outputs/processed)

- `papers_processed.csv`: cleaned paper-level dataset with year, citation count, degrees, centrality metrics, community assignment, and propagation reach
- `edges_wcc.csv`: cleaned edge list restricted to the largest weakly connected component
- `paper_communities.csv`: paper-to-community assignments with hidden-gem flags

### Figures

Location: [`outputs/figures`](outputs/figures)

- `citation_vs_pagerank.svg`
- `citation_vs_betweenness.svg`
- `top_hidden_gems_betweenness.svg`
- `top_hidden_gems_pagerank.svg`

## Caveats

- Betweenness centrality is approximate because it uses random pivot sampling for scalability.
- Closeness centrality is computed on a random sample of nodes rather than the full graph.
- Community detection uses label propagation, which is heuristic and can vary with update order.
- Propagation reach is capped at 3 hops, so it measures local-to-medium downstream spread rather than the full cascade.
- Some papers do not have attached year metadata, so year fields may be blank.
- The processed edge list and metrics are restricted to the largest weakly connected component.

## Current Status

Completed:

- graph loading and preprocessing
- WCC extraction
- PageRank, betweenness, and sampled closeness
- hidden gem identification
- community detection
- downstream reach analysis
- CSV table exports
- SVG visualizations
- processed dataset exports

Still to extend:

- final report writeup
