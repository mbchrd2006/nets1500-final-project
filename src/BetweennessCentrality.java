import java.util.*;

/**
 * Approximated betweenness centrality using the Brandes algorithm with random pivot sampling.
 *
 * Exact betweenness is O(V * E) which is too slow for this graph (~34k nodes, ~420k edges).
 * We instead sample k pivot nodes uniformly at random and accumulate pair-dependency scores
 * only from those pivots, then rescale. This gives an unbiased estimator of the true
 * betweenness with standard error O(1/sqrt(k)).
 *
 * With k=300 pivots the runtime is ~300 BFS traversals: feasible in a few minutes.
 *
 * Betweenness centrality of node v = fraction of shortest paths between all pairs (s,t)
 * that pass through v. A high score means v acts as a bridge between communities.
 */
public class BetweennessCentrality {

    /**
     * @param G    the citation graph
     * @param k    number of pivot nodes to sample (300 is a good default)
     * @param seed random seed for reproducibility
     * @return map of node -> normalized betweenness score
     */
    public static Map<String, Double> compute(CitationGraph G, int k, long seed) {
        List<String> nodes = new ArrayList<>(G.nodes());
        int n = nodes.size();
        Random rng = new Random(seed);

        Map<String, Double> betweenness = new HashMap<>();
        for (String node : nodes) betweenness.put(node, 0.0);

        System.out.printf("  Running Brandes BFS from %d sampled pivot nodes...%n", k);
        long t0 = System.currentTimeMillis();

        for (int i = 0; i < k; i++) {
            String source = nodes.get(rng.nextInt(n));
            _brandesBFS(G, source, betweenness);
            if ((i + 1) % 50 == 0) {
                long elapsed = System.currentTimeMillis() - t0;
                System.out.printf("    Pivot %d/%d  (%.1fs elapsed)%n", i + 1, k, elapsed / 1000.0);
            }
        }

        // Normalize: scale from k-pivot estimate to full-graph estimate,
        // then apply standard normalization 1/((n-1)*(n-2)) for directed graphs.
        double scaleFactor = (double) n / k;
        double norm = (n > 2) ? 1.0 / ((n - 1.0) * (n - 2.0)) : 1.0;
        for (String node : nodes) {
            betweenness.put(node, betweenness.get(node) * scaleFactor * norm);
        }

        return betweenness;
    }

    /**
     * One pass of Brandes BFS from a single source.
     * Accumulates pair-dependency scores into the betweenness map.
     */
    private static void _brandesBFS(CitationGraph G, String source, Map<String, Double> betweenness) {
        // Stack of nodes in non-increasing order of distance from source
        Deque<String> stack = new ArrayDeque<>();
        // Predecessors on shortest paths
        Map<String, List<String>> pred = new HashMap<>();
        // Number of shortest paths from source to each node
        Map<String, Long> sigma = new HashMap<>();
        // Distance from source
        Map<String, Integer> dist = new HashMap<>();

        for (String v : G.nodes()) {
            pred.put(v, new ArrayList<>());
            sigma.put(v, 0L);
            dist.put(v, -1);
        }
        sigma.put(source, 1L);
        dist.put(source, 0);

        Queue<String> queue = new ArrayDeque<>();
        queue.add(source);

        while (!queue.isEmpty()) {
            String v = queue.poll();
            stack.push(v);
            for (String w : G.successors(v)) {
                // First time we reach w?
                if (dist.get(w) == -1) {
                    dist.put(w, dist.get(v) + 1);
                    queue.add(w);
                }
                // Is this a shortest path to w via v?
                if (dist.get(w) == dist.get(v) + 1) {
                    sigma.put(w, sigma.get(w) + sigma.get(v));
                    pred.get(w).add(v);
                }
            }
        }

        // Back-propagate dependencies
        Map<String, Double> delta = new HashMap<>();
        for (String v : G.nodes()) delta.put(v, 0.0);

        while (!stack.isEmpty()) {
            String w = stack.pop();
            for (String v : pred.get(w)) {
                double contribution = ((double) sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                delta.put(v, delta.get(v) + contribution);
            }
            if (!w.equals(source)) {
                betweenness.put(w, betweenness.get(w) + delta.get(w));
            }
        }
    }
}
