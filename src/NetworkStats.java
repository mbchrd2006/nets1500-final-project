import java.util.*;

/**
 * Basic descriptive statistics for the citation graph.
 */
public class NetworkStats {

    public static void printSummary(CitationGraph G) {
        int n = G.nodeCount();
        int e = G.edgeCount();

        // In-degree stats
        List<Integer> inDegrees = new ArrayList<>(n);
        int maxIn = 0, zeroCite = 0;
        long sumIn = 0;
        for (String node : G.nodes()) {
            int d = G.inDegree(node);
            inDegrees.add(d);
            sumIn += d;
            if (d > maxIn) maxIn = d;
            if (d == 0) zeroCite++;
        }
        Collections.sort(inDegrees);
        double meanIn = (double) sumIn / n;
        int medianIn = inDegrees.get(n / 2);
        double density = (double) e / ((long) n * (n - 1));

        System.out.println("\n========== Network Summary ==========");
        System.out.printf("Nodes          : %,d%n", n);
        System.out.printf("Edges          : %,d%n", e);
        System.out.printf("Density        : %.7f%n", density);
        System.out.printf("Max in-degree  : %,d%n", maxIn);
        System.out.printf("Mean in-degree : %.2f%n", meanIn);
        System.out.printf("Median in-degree: %d%n", medianIn);
        System.out.printf("Papers with 0 citations: %,d (%.1f%%)%n",
                zeroCite, 100.0 * zeroCite / n);

        // WCC
        Set<String> wcc = G.largestWCC();
        System.out.printf("Largest WCC    : %,d nodes (%.1f%% of total)%n",
                wcc.size(), 100.0 * wcc.size() / n);
        System.out.println("=====================================\n");
    }

    /**
     * Return the top-k nodes by a given metric map, sorted descending.
     */
    public static List<Map.Entry<String, Double>> topK(Map<String, Double> scores, int k) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scores.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return entries.subList(0, Math.min(k, entries.size()));
    }

    /**
     * Compute the median value of a score map.
     */
    public static double median(Map<String, Double> scores) {
        List<Double> vals = new ArrayList<>(scores.values());
        Collections.sort(vals);
        int mid = vals.size() / 2;
        return vals.size() % 2 == 0 ? (vals.get(mid - 1) + vals.get(mid)) / 2.0 : vals.get(mid);
    }

    /**
     * Compute the p-th quantile (0.0 to 1.0) of a score map.
     */
    public static double quantile(Map<String, Double> scores, double p) {
        List<Double> vals = new ArrayList<>(scores.values());
        Collections.sort(vals);
        int idx = (int) Math.ceil(p * vals.size()) - 1;
        idx = Math.max(0, Math.min(idx, vals.size() - 1));
        return vals.get(idx);
    }

    /**
     * Spearman rank correlation between two score maps (only common keys).
     */
    public static double spearmanCorrelation(Map<String, Integer> rawCitations,
                                              Map<String, Double> metric) {
        List<String> common = new ArrayList<>(rawCitations.keySet());
        common.retainAll(metric.keySet());
        int n = common.size();
        if (n < 2) return Double.NaN;

        // Rank the citation counts
        common.sort(Comparator.comparingInt(rawCitations::get));
        Map<String, Double> rankA = new HashMap<>();
        for (int i = 0; i < n; i++) rankA.put(common.get(i), (double)(i + 1));

        // Rank the metric
        common.sort(Comparator.comparingDouble(metric::get));
        Map<String, Double> rankB = new HashMap<>();
        for (int i = 0; i < n; i++) rankB.put(common.get(i), (double)(i + 1));

        // Pearson correlation of ranks = Spearman
        double sumA = 0, sumB = 0;
        for (String v : common) { sumA += rankA.get(v); sumB += rankB.get(v); }
        double meanA = sumA / n, meanB = sumB / n;
        double num = 0, denA = 0, denB = 0;
        for (String v : common) {
            double dA = rankA.get(v) - meanA, dB = rankB.get(v) - meanB;
            num  += dA * dB;
            denA += dA * dA;
            denB += dB * dB;
        }
        return num / Math.sqrt(denA * denB);
    }
}
