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
 *   7. Detect communities and measure propagation reach
 *   8. Export tables and processed datasets
 */
public class Member1Analysis {

    // Adjust these paths if your data directory is somewhere else
    static final String EDGE_FILE     = "data/cit-HepPh.txt";
    static final String DATES_FILE    = "data/cit-HepPh-dates.txt";
    static final String TABLE_DIR     = "outputs/tables";
    static final String PROCESSED_DIR = "outputs/processed";

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

        // ── 9. Community detection ─────────────────────────────────────────
        System.out.println("\n=== Community Detection ===");
        CommunityDetection.Result communities = CommunityDetection.labelPropagation(G, 20, 42L);
        printTopCommunities(G, communities, pageRank, betweenness);

        // ── 10. Propagation analysis ───────────────────────────────────────
        System.out.println("\n=== Influence Propagation ===");
        CitationGraph influenceGraph = G.reverse();
        Map<String, PropagationAnalysis.ReachSummary> propagation =
                PropagationAnalysis.compute(influenceGraph, 3);
        printTopPropagation(G, communities, propagation, citationCount);

        // ── 11. Write CSVs ─────────────────────────────────────────────────
        Files.createDirectories(Path.of(TABLE_DIR));
        Files.createDirectories(Path.of(PROCESSED_DIR));

        writeCSV(
                G,
                citationCount,
                pageRank,
                betweenness,
                closeness,
                TABLE_DIR + "/member1_metrics.csv"
        );

        writeHiddenGemsBetweenness(
                G,
                hiddenGemsBetweenness,
                citationCount,
                betweenness,
                TABLE_DIR + "/hidden_gems_betweenness.csv"
        );

        writeHiddenGemsPageRank(
                G,
                hiddenGemsPageRank,
                citationCount,
                pageRank,
                TABLE_DIR + "/hidden_gems_pagerank.csv"
        );

        writeProcessedPapers(
                G,
                citationCount,
                pageRank,
                betweenness,
                closeness,
                communities,
                propagation,
                PROCESSED_DIR + "/papers_processed.csv"
        );

        writeProcessedEdges(
                G,
                PROCESSED_DIR + "/edges_wcc.csv"
        );

        writePaperCommunities(
                G,
                communities,
                citationCount,
                pageRank,
                betweenness,
                propagation,
                new HashSet<>(hiddenGemsBetweenness),
                new HashSet<>(hiddenGemsPageRank),
                PROCESSED_DIR + "/paper_communities.csv"
        );

        writeCommunitySummary(
                communities,
                citationCount,
                pageRank,
                betweenness,
                propagation,
                new HashSet<>(hiddenGemsBetweenness),
                new HashSet<>(hiddenGemsPageRank),
                TABLE_DIR + "/community_summary.csv"
        );

        writePropagationSummary(
                G,
                communities,
                citationCount,
                pageRank,
                betweenness,
                propagation,
                TABLE_DIR + "/propagation_summary.csv"
        );

        System.out.println("\nResults written to " + TABLE_DIR);
        System.out.println("Processed datasets written to " + PROCESSED_DIR);
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

    private static void printTopCommunities(CitationGraph G,
                                            CommunityDetection.Result communities,
                                            Map<String, Double> pageRank,
                                            Map<String, Double> betweenness) {
        System.out.printf("Detected %,d communities.%n", communities.communityCount());
        System.out.println("\nTop 10 communities by size:");
        System.out.printf("%-12s  %8s  %-12s  %-12s%n",
                "Community", "Size", "TopPR", "TopBetween");
        System.out.println("-".repeat(56));

        int shown = 0;
        for (int communityId : communities.communityIds()) {
            List<String> members = communities.members(communityId);
            if (members.isEmpty()) continue;

            String topPR = topNodeByScore(members, pageRank);
            String topBW = topNodeByScore(members, betweenness);

            System.out.printf("%-12d  %8d  %-12s  %-12s%n",
                    communityId,
                    members.size(),
                    topPR,
                    topBW);

            shown++;
            if (shown == 10) break;
        }
    }

    private static void printTopPropagation(CitationGraph G,
                                            CommunityDetection.Result communities,
                                            Map<String, PropagationAnalysis.ReachSummary> propagation,
                                            Map<String, Integer> citations) {
        List<String> nodes = sortedNodes(G);
        nodes.sort((a, b) -> Integer.compare(
                propagation.get(b).totalReach,
                propagation.get(a).totalReach
        ));

        System.out.println("\nTop 10 papers by downstream reach within 3 hops:");
        System.out.printf("%-12s  %10s  %8s  %10s%n",
                "PaperID", "TotalReach", "Cites", "Community");
        System.out.println("-".repeat(50));

        for (int i = 0; i < Math.min(10, nodes.size()); i++) {
            String node = nodes.get(i);
            PropagationAnalysis.ReachSummary reach = propagation.get(node);

            System.out.printf("%-12s  %10d  %8d  %10d%n",
                    node,
                    reach.totalReach,
                    citations.get(node),
                    communities.communityOf(node));
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

            for (String node : sortedNodes(G)) {
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

    private static void writeProcessedPapers(CitationGraph G,
                                             Map<String, Integer> citations,
                                             Map<String, Double> pageRank,
                                             Map<String, Double> betweenness,
                                             Map<String, Double> closeness,
                                             CommunityDetection.Result communities,
                                             Map<String, PropagationAnalysis.ReachSummary> propagation,
                                             String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("paper_id,year,citation_count,out_degree,pagerank,betweenness,closeness,closeness_available,community_id,community_size,reach_1,reach_2,reach_3,total_reach");

            for (String node : sortedNodes(G)) {
                Integer yr = G.getYear(node);
                Double cl = closeness.get(node);
                int communityId = communities.communityOf(node);
                int communitySize = communities.communitySize(communityId);
                PropagationAnalysis.ReachSummary reach = propagation.get(node);

                pw.printf("%s,%s,%d,%d,%.8f,%.8f,%s,%s,%d,%d,%d,%d,%d,%d%n",
                        node,
                        yr != null ? yr.toString() : "",
                        citations.get(node),
                        G.outDegree(node),
                        pageRank.get(node),
                        betweenness.get(node),
                        cl != null ? String.format("%.8f", cl) : "",
                        cl != null ? "true" : "false",
                        communityId,
                        communitySize,
                        reach.reach1,
                        reach.reach2,
                        reach.reach3,
                        reach.totalReach);
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static void writeProcessedEdges(CitationGraph G,
                                            String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("source_paper_id,target_paper_id,source_year,target_year");

            for (String source : sortedNodes(G)) {
                List<String> targets = new ArrayList<>(G.successors(source));
                Collections.sort(targets);

                Integer sourceYear = G.getYear(source);
                for (String target : targets) {
                    Integer targetYear = G.getYear(target);
                    pw.printf("%s,%s,%s,%s%n",
                            source,
                            target,
                            sourceYear != null ? sourceYear.toString() : "",
                            targetYear != null ? targetYear.toString() : "");
                }
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static void writePaperCommunities(CitationGraph G,
                                              CommunityDetection.Result communities,
                                              Map<String, Integer> citations,
                                              Map<String, Double> pageRank,
                                              Map<String, Double> betweenness,
                                              Map<String, PropagationAnalysis.ReachSummary> propagation,
                                              Set<String> hiddenBw,
                                              Set<String> hiddenPr,
                                              String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("paper_id,community_id,community_size,year,citation_count,pagerank,betweenness,total_reach,hidden_gem_betweenness,hidden_gem_pagerank");

            for (String node : sortedNodes(G)) {
                int communityId = communities.communityOf(node);
                int communitySize = communities.communitySize(communityId);
                Integer yr = G.getYear(node);
                PropagationAnalysis.ReachSummary reach = propagation.get(node);

                pw.printf("%s,%d,%d,%s,%d,%.8f,%.8f,%d,%s,%s%n",
                        node,
                        communityId,
                        communitySize,
                        yr != null ? yr.toString() : "",
                        citations.get(node),
                        pageRank.get(node),
                        betweenness.get(node),
                        reach.totalReach,
                        hiddenBw.contains(node) ? "true" : "false",
                        hiddenPr.contains(node) ? "true" : "false");
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static void writeCommunitySummary(CommunityDetection.Result communities,
                                              Map<String, Integer> citations,
                                              Map<String, Double> pageRank,
                                              Map<String, Double> betweenness,
                                              Map<String, PropagationAnalysis.ReachSummary> propagation,
                                              Set<String> hiddenBw,
                                              Set<String> hiddenPr,
                                              String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("community_id,size,avg_citations,avg_pagerank,avg_betweenness,avg_total_reach,hidden_bw_count,hidden_pr_count,top_pagerank_paper,top_betweenness_paper,top_reach_paper");

            for (int communityId : communities.communityIds()) {
                List<String> members = communities.members(communityId);
                if (members.isEmpty()) continue;

                long sumCitations = 0;
                double sumPageRank = 0.0;
                double sumBetweenness = 0.0;
                long sumReach = 0;
                int hiddenBwCount = 0;
                int hiddenPrCount = 0;

                String topPageRankPaper = members.get(0);
                String topBetweennessPaper = members.get(0);
                String topReachPaper = members.get(0);

                for (String node : members) {
                    sumCitations += citations.get(node);
                    sumPageRank += pageRank.get(node);
                    sumBetweenness += betweenness.get(node);
                    sumReach += propagation.get(node).totalReach;

                    if (hiddenBw.contains(node)) hiddenBwCount++;
                    if (hiddenPr.contains(node)) hiddenPrCount++;

                    if (pageRank.get(node) > pageRank.get(topPageRankPaper)) topPageRankPaper = node;
                    if (betweenness.get(node) > betweenness.get(topBetweennessPaper)) topBetweennessPaper = node;
                    if (propagation.get(node).totalReach > propagation.get(topReachPaper).totalReach) {
                        topReachPaper = node;
                    }
                }

                double size = members.size();
                pw.printf("%d,%d,%.2f,%.8f,%.8f,%.2f,%d,%d,%s,%s,%s%n",
                        communityId,
                        members.size(),
                        sumCitations / size,
                        sumPageRank / size,
                        sumBetweenness / size,
                        sumReach / size,
                        hiddenBwCount,
                        hiddenPrCount,
                        topPageRankPaper,
                        topBetweennessPaper,
                        topReachPaper);
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static void writePropagationSummary(CitationGraph G,
                                                CommunityDetection.Result communities,
                                                Map<String, Integer> citations,
                                                Map<String, Double> pageRank,
                                                Map<String, Double> betweenness,
                                                Map<String, PropagationAnalysis.ReachSummary> propagation,
                                                String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(path)))) {
            pw.println("paper_id,community_id,year,citation_count,pagerank,betweenness,reach_1,reach_2,reach_3,total_reach");

            for (String node : sortedNodes(G)) {
                Integer yr = G.getYear(node);
                PropagationAnalysis.ReachSummary reach = propagation.get(node);

                pw.printf("%s,%d,%s,%d,%.8f,%.8f,%d,%d,%d,%d%n",
                        node,
                        communities.communityOf(node),
                        yr != null ? yr.toString() : "",
                        citations.get(node),
                        pageRank.get(node),
                        betweenness.get(node),
                        reach.reach1,
                        reach.reach2,
                        reach.reach3,
                        reach.totalReach);
            }
        }

        System.out.println("Wrote: " + path);
    }

    private static String topNodeByScore(List<String> members, Map<String, Double> scores) {
        String best = members.get(0);
        for (String node : members) {
            if (scores.get(node) > scores.get(best)) best = node;
        }
        return best;
    }

    private static List<String> sortedNodes(CitationGraph G) {
        List<String> nodes = new ArrayList<>(G.nodes());
        Collections.sort(nodes);
        return nodes;
    }

    private static int medianInt(List<Integer> vals) {
        Collections.sort(vals);
        return vals.get(vals.size() / 2);
    }
}
