import java.util.*;

/**
 * Measures how far influence can spread from a paper through downstream
 * citations. The caller should pass a reversed citation graph so that outgoing
 * edges point from a paper to later papers that cite it.
 */
public class PropagationAnalysis {

    public static class ReachSummary {
        public final int reach1;
        public final int reach2;
        public final int reach3;
        public final int totalReach;

        ReachSummary(int reach1, int reach2, int reach3, int totalReach) {
            this.reach1 = reach1;
            this.reach2 = reach2;
            this.reach3 = reach3;
            this.totalReach = totalReach;
        }
    }

    public static Map<String, ReachSummary> compute(CitationGraph influenceGraph, int maxDepth) {
        List<String> nodes = new ArrayList<>(influenceGraph.nodes());
        Collections.sort(nodes);

        Map<String, ReachSummary> reachByNode = new HashMap<>();
        System.out.printf("  Computing downstream reach up to %d hops for %,d nodes...%n",
                maxDepth, nodes.size());

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < nodes.size(); i++) {
            String node = nodes.get(i);
            reachByNode.put(node, computeReach(influenceGraph, node, maxDepth));

            if ((i + 1) % 5000 == 0 || i + 1 == nodes.size()) {
                long elapsed = System.currentTimeMillis() - t0;
                System.out.printf("    Node %,d/%,d  (%.1fs elapsed)%n",
                        i + 1, nodes.size(), elapsed / 1000.0);
            }
        }

        return reachByNode;
    }

    private static ReachSummary computeReach(CitationGraph G, String source, int maxDepth) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Queue<Integer> depths = new ArrayDeque<>();
        int[] exactReach = new int[maxDepth + 1];

        visited.add(source);
        queue.add(source);
        depths.add(0);

        while (!queue.isEmpty()) {
            String node = queue.poll();
            int depth = depths.poll();

            if (depth == maxDepth) continue;

            for (String neighbor : G.successors(node)) {
                if (visited.add(neighbor)) {
                    int nextDepth = depth + 1;
                    exactReach[nextDepth]++;
                    queue.add(neighbor);
                    depths.add(nextDepth);
                }
            }
        }

        int totalReach = 0;
        for (int depth = 1; depth <= maxDepth; depth++) totalReach += exactReach[depth];

        return new ReachSummary(
                maxDepth >= 1 ? exactReach[1] : 0,
                maxDepth >= 2 ? exactReach[2] : 0,
                maxDepth >= 3 ? exactReach[3] : 0,
                totalReach
        );
    }
}
