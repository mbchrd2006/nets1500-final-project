import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Member 1 entry point.
 *
 * Tasks:
 *   1. Load and preprocess the citation graph
 *   2. Print basic network statistics
 *   3. Compute PageRank
 *   4. Compute betweenness centrality
 *   5. Compute closeness centrality
 *   6. Identify "hidden gems" — low citation count but high structural importance
 *   7. Write results to outputs/tables/
 */
public class Member1Analysis {

    // Adjust these paths if your data directory is somewhere else
    static final String EDGE_FILE  = "data/cit-HepPh.txt";
    static final String DATES_FILE = "data/cit-HepPh-dates.txt";
    static final String OUT_DIR    = "outputs/tables";

    public static void main(String[] args) throws Exception {

        // ── 1. Load graph ──────────────────────────────────────────────────
        CitationGraph G = GraphLoader.loadEdgeList(EDGE_FILE);
        GraphLoader.attachDates(G, DATES_FILE);

        int originalNodeCount = G.nodeCount();

        // Restrict analysis to the largest weakly connected component
        System.out.println("Extracting largest WCC ...");
        Set<String> wccNodes = G.largestWCC();
        G = G.subgraph(wccNodes);

        System.out.printf("WCC: %,d nodes, %,d edges (%.1f%% of original)%n",
                G.nodeCount(), G.edgeCount(),
                100.0 * G.nodeCount() / originalNodeCount);

        // ── 2. Basic stats ─────────────────────────────────────────────────
        NetworkStats.printSummary(G);

        // Raw citation counts: in-degree = number of papers citing this paper
        Map<String, Integer> citationCount = new HashMap<>();
        for (String node : G.nodes()) {
            citationCount.put(node, G.inDegree(node));
        }

        // ── 3. PageRank ────────────────────────────────────────────────────
        System.out.println("\n=== PageRank ===");
        Map<String, Double> pageRank = PageRank.compute(G, 0.85, 100);

        System.out.println("\nTop 10 by PageRank:");
        printTop10(G, pageRank, citationCount, "pagerank");

        // ── 4. Betweenness centrality ──────────────────────────────────────
        System.out.println("\n=== Betweenness Centrality ===");
        Map<String, Double> betweenness = BetweennessCentrality.compute(G, 300, 42L);

        System.out.println("\nTop 10 by Betweenness Centrality:");
        printTop10(G, betweenness, citationCount, "betweenness");

        // ── 5. Closeness centrality ────────────────────────────────────────
        System.out.println("\n=== Closeness Centrality ===");
        Map<String, Double> closeness = ClosenessCentrality.compute(G, 3000, 42L);

        System.out.println("\nTop 10 by Closeness Centrality (sampled):");
        printTop10(G, closeness, citationCount, "closeness");

        // ── 6. Hidden gems: low citations, high betweenness ────────────────
        System.out.println("\n=== Hidden Gems Analysis: Betweenness ===");

        int medianCite = medianInt(new ArrayList<>(citationCount.values()));
        double bwTop10 = NetworkStats.quantile(betweenness, 0.90);

        List<String> hiddenGemsBetweenness = new ArrayList<>();
        for (String node : G.nodes()) {
            if (citationCount.get(node) <= medianCite && betweenness.get(node) >= bwTop10) {
                hiddenGemsBetweenness.add(node);
            }
        }

        hiddenGemsBetweenness.sort((a, b) -> Double.compare(betweenness.get(b), betweenness.get(a)));

        System.out.printf("Median citations: %d   Top-10%% betweenness threshold: %.6f%n",
                medianCite, bwTop10);
        System.out.printf("Hidden gems found: %,d%n", hiddenGemsBetweenness.size());

        System.out.println("\nTop 15 hidden gems by Betweenness:");
        System.out.printf("%-12s  %8s  %12s  %6s%n", "PaperID", "Citations", "Betweenness", "Year");
        System.out.println("-".repeat(50));

        for (int i = 0; i < Math.min(15, hiddenGemsBetweenness.size()); i++) {
            String node = hiddenGemsBetweenness.get(i);
            Integer yr = G.getYear(node);

            System.out.printf("%-12s  %8d  %12.6f  %6s%n",
                    node,
                    citationCount.get(node),
                    betweenness.get(node),
                    yr != null ? yr.toString() : "?");
        }

        // ── 7. Hidden gems: low citations, high PageRank ───────────────────
        System.out.println("\n=== Hidden Gems Analysis: PageRank ===");

        double prTop10 = NetworkStats.quantile(pageRank, 0.90);

        List<String> hiddenGemsPageRank = new ArrayList<>();
        for (String node : G.nodes()) {
            if (citationCount.get(node) <= medianCite && pageRank.get(node) >= prTop10) {
                hiddenGemsPageRank.add(node);
            }
        }

        hiddenGemsPageRank.sort((a, b) -> Double.compare(pageRank.get(b), pageRank.get(a)));

        System.out.printf("Median citations: %d   Top-10%% PageRank threshold: %.8f%n",
                medianCite, prTop10);
        System.out.printf("Hidden PageRank gems found: %,d%n", hiddenGemsPageRank.size());

        System.out.println("\nTop 15 hidden gems by PageRank:");
        System.out.printf("%-12s  %8s  %12s  %6s%n", "PaperID", "Citations", "PageRank", "Year");
        System.out.println("-".repeat(50));

        for (int i = 0; i < Math.min(15, hiddenGemsPageRank.size()); i++) {
            String node = hiddenGemsPageRank.get(i);
            Integer yr = G.getYear(node);

            System.out.printf("%-12s  %8d  %12.8f  %6s%n",
                    node,
                    citationCount.get(node),
                    pageRank.get(node),
                    yr != null ? yr.toString() : "?");
        }

        // ── 8. Spearman correlations ───────────────────────────────────────
        System.out.println("\n=== Correlations with Citation Count ===");

        double rhoP = NetworkStats.spearmanCorrelation(citationCount, pageRank);
        System.out.printf("Spearman(citations, PageRank)    = %.4f%n", rhoP);

        double rhoB = NetworkStats.spearmanCorrelation(citationCount, betweenness);
        System.out.printf("Spearman(citations, betweenness) = %.4f%n", rhoB);

        double rhoC = NetworkStats.spearmanCorrelation(citationCount, closeness);
        System.out.printf("Spearman(citations, closeness)   = %.4f  (sampled nodes only)%n", rhoC);

        // ── 9. Write CSVs ──────────────────────────────────────────────────
        Files.createDirectories(Path.of(OUT_DIR));

        writeCSV(
                G,
                citationCount,
                pageRank,
                betweenness,
                closeness,
                OUT_DIR + "/member1_metrics.csv"
        );

        writeHiddenGemsBetweenness(
                G,
                hiddenGemsBetweenness,
                citationCount,
                betweenness,
                OUT_DIR + "/hidden_gems_betweenness.csv"
        );

        writeHiddenGemsPageRank(
                G,
                hiddenGemsPageRank,
                citationCount,
                pageRank,
                OUT_DIR + "/hidden_gems_pagerank.csv"
        );

        System.out.println("\nResults written to " + OUT_DIR);
        System.out.println("Done.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static void printTop10(CitationGraph G,
                                   Map<String, Double> scores,
                                   Map<String, Integer> citations,
                                   String label) {
        System.out.printf("%-12s  %8s  %12s  %6s%n", "PaperID", "Citations", label, "Year");
        System.out.println("-".repeat(50));

        for (Map.Entry<String, Double> e : NetworkStats.topK(scores, 10)) {
            Integer yr = G.getYear(e.getKey());

            System.out.printf("%-12s  %8d  %12.8f  %6s%n",
                    e.getKey(),
                    citations.getOrDefault(e.getKey(), 0),
                    e.getValue(),
                    yr != null ? yr.toString() : "?");
        }
    }

    private static void writeCSV(CitationGraph G,
                                 Map<String, Integer> citations,
                                 Map<String, Double> pageRank,
                                 Map<String, Double> betweenness,
                                 Map<String, Double> closeness,
                                 String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("paper_id,citation_count,pagerank,betweenness,closeness,year");

            for (String node : G.nodes()) {
                Integer yr = G.getYear(node);
                Double cl = closeness.get(node);

                pw.printf("%s,%d,%.8f,%.8f,%s,%s%n",
                        node,
                        citations.get(node),
                        pageRank.get(node),
                        betweenness.get(node),
                        cl != null ? String.format("%.8f", cl) : "",
                        yr != null ? yr.toString() : "");
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static void writeHiddenGemsBetweenness(CitationGraph G,
                                                   List<String> gems,
                                                   Map<String, Integer> citations,
                                                   Map<String, Double> betweenness,
                                                   String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("paper_id,citation_count,betweenness,year");

            for (String node : gems) {
                Integer yr = G.getYear(node);

                pw.printf("%s,%d,%.8f,%s%n",
                        node,
                        citations.get(node),
                        betweenness.get(node),
                        yr != null ? yr.toString() : "");
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static void writeHiddenGemsPageRank(CitationGraph G,
                                                List<String> gems,
                                                Map<String, Integer> citations,
                                                Map<String, Double> pageRank,
                                                String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("paper_id,citation_count,pagerank,year");

            for (String node : gems) {
                Integer yr = G.getYear(node);

                pw.printf("%s,%d,%.8f,%s%n",
                        node,
                        citations.get(node),
                        pageRank.get(node),
                        yr != null ? yr.toString() : "");
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static int medianInt(List<Integer> vals) {
        Collections.sort(vals);
        return vals.get(vals.size() / 2);
    }
}