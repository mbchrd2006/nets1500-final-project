import java.util.*;

/**
 * Lightweight community detection via label propagation on the undirected view
 * of the citation graph.
 */
public class CommunityDetection {

    public static class Result {
        private final Map<String, Integer> communityByNode;
        private final Map<Integer, List<String>> membersByCommunity;

        Result(Map<String, Integer> communityByNode, Map<Integer, List<String>> membersByCommunity) {
            this.communityByNode = new HashMap<>(communityByNode);
            this.membersByCommunity = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<String>> entry : membersByCommunity.entrySet()) {
                this.membersByCommunity.put(
                        entry.getKey(),
                        Collections.unmodifiableList(new ArrayList<>(entry.getValue()))
                );
            }
        }

        public int communityOf(String node) {
            return communityByNode.getOrDefault(node, -1);
        }

        public int communitySize(int communityId) {
            List<String> members = membersByCommunity.get(communityId);
            return members == null ? 0 : members.size();
        }

        public int communityCount() {
            return membersByCommunity.size();
        }

        public Set<Integer> communityIds() {
            return Collections.unmodifiableSet(membersByCommunity.keySet());
        }

        public List<String> members(int communityId) {
            List<String> members = membersByCommunity.get(communityId);
            return members == null ? Collections.emptyList() : members;
        }
    }

    public static Result labelPropagation(CitationGraph G, int maxIterations, long seed) {
        List<String> nodes = new ArrayList<>(G.nodes());
        Collections.sort(nodes);

        Map<String, List<String>> neighbors = buildUndirectedNeighbors(G, nodes);
        Map<String, String> label = new HashMap<>();
        for (String node : nodes) label.put(node, node);

        List<String> order = new ArrayList<>(nodes);
        Random rng = new Random(seed);

        System.out.printf("  Running label propagation for up to %d iterations...%n", maxIterations);

        for (int iter = 0; iter < maxIterations; iter++) {
            Collections.shuffle(order, rng);
            int changes = 0;

            for (String node : order) {
                List<String> nodeNeighbors = neighbors.get(node);
                if (nodeNeighbors.isEmpty()) continue;

                Map<String, Integer> labelCounts = new HashMap<>();
                for (String neighbor : nodeNeighbors) {
                    labelCounts.merge(label.get(neighbor), 1, Integer::sum);
                }

                String currentLabel = label.get(node);
                int bestCount = 0;
                for (int count : labelCounts.values()) {
                    if (count > bestCount) bestCount = count;
                }

                if (labelCounts.getOrDefault(currentLabel, 0) == bestCount) continue;

                String bestLabel = null;
                for (Map.Entry<String, Integer> entry : labelCounts.entrySet()) {
                    String candidate = entry.getKey();
                    int count = entry.getValue();

                    if (count == bestCount &&
                            (bestLabel == null || candidate.compareTo(bestLabel) < 0)) {
                        bestLabel = candidate;
                    }
                }

                if (bestLabel != null && !bestLabel.equals(currentLabel)) {
                    label.put(node, bestLabel);
                    changes++;
                }
            }

            System.out.printf("    Iteration %d/%d  (%d label changes)%n", iter + 1, maxIterations, changes);
            if (changes == 0) break;
        }

        Map<String, List<String>> groupedByLabel = new HashMap<>();
        for (String node : nodes) {
            groupedByLabel.computeIfAbsent(label.get(node), k -> new ArrayList<>()).add(node);
        }

        List<Map.Entry<String, List<String>>> groupedEntries = new ArrayList<>(groupedByLabel.entrySet());
        groupedEntries.sort((a, b) -> {
            int bySize = Integer.compare(b.getValue().size(), a.getValue().size());
            if (bySize != 0) return bySize;
            return a.getKey().compareTo(b.getKey());
        });

        Map<String, Integer> communityByNode = new HashMap<>();
        Map<Integer, List<String>> membersByCommunity = new LinkedHashMap<>();

        int communityId = 1;
        for (Map.Entry<String, List<String>> entry : groupedEntries) {
            List<String> members = new ArrayList<>(entry.getValue());
            Collections.sort(members);
            membersByCommunity.put(communityId, members);
            for (String node : members) {
                communityByNode.put(node, communityId);
            }
            communityId++;
        }

        return new Result(communityByNode, membersByCommunity);
    }

    private static Map<String, List<String>> buildUndirectedNeighbors(CitationGraph G, List<String> nodes) {
        Map<String, List<String>> neighbors = new HashMap<>();

        for (String node : nodes) {
            Set<String> undirected = new HashSet<>(G.successors(node));
            undirected.addAll(G.predecessors(node));

            List<String> neighborList = new ArrayList<>(undirected);
            Collections.sort(neighborList);
            neighbors.put(node, neighborList);
        }

        return neighbors;
    }
}
