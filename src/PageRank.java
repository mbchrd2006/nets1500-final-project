import java.util.*;

/**
 * Computes PageRank for a directed citation graph.
 *
 * In our graph, edge u -> v means paper u cites paper v.
 * Therefore, paper v receives PageRank from paper u.
 */
public class PageRank {

    /**
     * Compute PageRank scores.
     *
     * @param G citation graph
     * @param damping usually 0.85
     * @param iterations usually 50 or 100
     * @return map from paper ID to PageRank score
     */
    public static Map<String, Double> compute(CitationGraph G, double damping, int iterations) {
        List<String> nodes = new ArrayList<>(G.nodes());
        int n = nodes.size();

        Map<String, Double> rank = new HashMap<>();
        Map<String, Double> newRank = new HashMap<>();

        double initialRank = 1.0 / n;

        for (String node : nodes) {
            rank.put(node, initialRank);
        }

        for (int iter = 0; iter < iterations; iter++) {
            double danglingMass = 0.0;

            // If a paper cites nothing, it is a dangling node.
            // Its rank gets spread evenly across all papers.
            for (String node : nodes) {
                if (G.outDegree(node) == 0) {
                    danglingMass += rank.get(node);
                }
            }

            for (String node : nodes) {
                double score = (1.0 - damping) / n;

                // Contribution from dangling nodes
                score += damping * danglingMass / n;

                // Contribution from papers that cite this paper
                for (String pred : G.predecessors(node)) {
                    int outDegree = G.outDegree(pred);

                    if (outDegree > 0) {
                        score += damping * rank.get(pred) / outDegree;
                    }
                }

                newRank.put(node, score);
            }

            // Swap maps for next iteration
            Map<String, Double> temp = rank;
            rank = newRank;
            newRank = temp;
            newRank.clear();
        }

        return rank;
    }
}