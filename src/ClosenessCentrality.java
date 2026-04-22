import java.util.*;

/**
 * Closeness centrality computed via BFS, with Wasserman-Faust normalization
 * to handle disconnected graphs (nodes that can't reach everyone still get
 * a meaningful score proportional to what they *can* reach).
 *
 * Formula (Wasserman-Faust):
 *   closeness(v) = [(reachable(v) - 1)^2 / (n - 1)] / sum_of_distances(v)
 *
 * where reachable(v) = number of nodes reachable from v (including v itself),
 * n = total nodes in the graph, sum_of_distances = sum of BFS distances to all
 * reachable nodes.
 *
 * A high closeness means a paper can quickly propagate influence through the
 * citation network — its ideas reach subsequent work in fewer "hops."
 *
 * For scalability on a 34k-node graph, we compute closeness for a random
 * sample of nodes rather than all nodes. 3000 samples gives a representative
 * picture of the distribution.
 */
public class ClosenessCentrality {

    /**
     * Compute closeness for a random sample of nodes.
     *
     * @param G          the citation graph
     * @param sampleSize number of nodes to sample (use -1 for all nodes)
     * @param seed       random seed
     * @return map of sampled node -> closeness score
     */
    public static Map<String, Double> compute(CitationGraph G, int sampleSize, long seed) {
        List<String> nodes = new ArrayList<>(G.nodes());
        int n = nodes.size();

        List<String> targets;
        if (sampleSize < 0 || sampleSize >= n) {
            targets = nodes;
            System.out.printf("  Computing closeness for all %,d nodes...%n", n);
        } else {
            Collections.shuffle(nodes, new Random(seed));
            targets = nodes.subList(0, sampleSize);
            System.out.printf("  Computing closeness for %,d sampled nodes (of %,d total)...%n",
                    sampleSize, n);
        }

        Map<String, Double> closeness = new HashMap<>();
        long t0 = System.currentTimeMillis();

        for (int i = 0; i < targets.size(); i++) {
            String v = targets.get(i);
            closeness.put(v, _closenessBFS(G, v, n));

            if ((i + 1) % 500 == 0) {
                long elapsed = System.currentTimeMillis() - t0;
                System.out.printf("    Node %,d/%,d  (%.1fs elapsed)%n",
                        i + 1, targets.size(), elapsed / 1000.0);
            }
        }

        return closeness;
    }

    /** BFS from source; returns Wasserman-Faust closeness score. */
    private static double _closenessBFS(CitationGraph G, String source, int totalNodes) {
        Map<String, Integer> dist = G.bfsDistances(source);
        int reachable = dist.size();   // includes source (distance 0)
        long sumDist = 0;
        for (int d : dist.values()) sumDist += d;

        if (reachable <= 1 || sumDist == 0) return 0.0;

        // Wasserman-Faust normalization
        double reachableFraction = (double)(reachable - 1) / (totalNodes - 1);
        return reachableFraction * reachableFraction / sumDist * (reachable - 1);
    }
}
