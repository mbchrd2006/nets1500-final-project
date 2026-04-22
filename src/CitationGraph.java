import java.util.*;

/**
 * Directed graph backed by HashMap adjacency lists.
 * Edge (u -> v) means paper u cites paper v,
 * so v's in-degree equals its raw citation count.
 */
public class CitationGraph {

    // outgoing neighbors: node -> set of nodes it points to
    private final Map<String, Set<String>> outEdges = new HashMap<>();
    // incoming neighbors: node -> set of nodes pointing to it
    private final Map<String, Set<String>> inEdges  = new HashMap<>();
    // optional metadata per node
    private final Map<String, Integer> publicationYear = new HashMap<>();

    public void addEdge(String from, String to) {
        outEdges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
        inEdges .computeIfAbsent(to,   k -> new HashSet<>()).add(from);
        // ensure both nodes exist even if they have no outgoing / incoming edges
        outEdges.computeIfAbsent(to,   k -> new HashSet<>());
        inEdges .computeIfAbsent(from, k -> new HashSet<>());
    }

    public void setYear(String node, int year) {
        publicationYear.put(node, year);
    }

    public Integer getYear(String node) {
        return publicationYear.get(node);   // null if unknown
    }

    public Set<String> nodes() {
        return Collections.unmodifiableSet(outEdges.keySet());
    }

    public int nodeCount() { return outEdges.size(); }

    public int edgeCount() {
        int total = 0;
        for (Set<String> nbrs : outEdges.values()) total += nbrs.size();
        return total;
    }

    /** Papers that this paper cites (outgoing). */
    public Set<String> successors(String node) {
        return outEdges.getOrDefault(node, Collections.emptySet());
    }

    /** Papers that cite this paper (incoming). */
    public Set<String> predecessors(String node) {
        return inEdges.getOrDefault(node, Collections.emptySet());
    }

    public int inDegree(String node)  { return inEdges .getOrDefault(node, Collections.emptySet()).size(); }
    public int outDegree(String node) { return outEdges.getOrDefault(node, Collections.emptySet()).size(); }

    /**
     * Return a new graph with all edges reversed.
     * Useful for forward-reachability analysis (who cites a paper downstream).
     */
    public CitationGraph reverse() {
        CitationGraph R = new CitationGraph();
        for (Map.Entry<String, Set<String>> entry : outEdges.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                R.addEdge(to, from);
            }
        }
        publicationYear.forEach(R::setYear);
        return R;
    }

    /**
     * BFS from a source node; returns map of node -> shortest-path distance.
     * Traverses outgoing edges (successors).
     */
    public Map<String, Integer> bfsDistances(String source) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        dist.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            String u = queue.poll();
            int d = dist.get(u);
            for (String v : successors(u)) {
                if (!dist.containsKey(v)) {
                    dist.put(v, d + 1);
                    queue.add(v);
                }
            }
        }
        return dist;
    }

    /**
     * Return all nodes in the largest weakly connected component.
     * WCC treats edges as undirected.
     */
    public Set<String> largestWCC() {
        Map<String, Boolean> visited = new HashMap<>();
        Set<String> best = Collections.emptySet();

        for (String start : nodes()) {
            if (visited.containsKey(start)) continue;
            // BFS treating graph as undirected
            Set<String> component = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>();
            queue.add(start);
            component.add(start);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                visited.put(u, true);
                Set<String> neighbors = new HashSet<>(successors(u));
                neighbors.addAll(predecessors(u));
                for (String v : neighbors) {
                    if (!component.contains(v)) {
                        component.add(v);
                        queue.add(v);
                    }
                }
            }
            if (component.size() > best.size()) best = component;
        }
        return best;
    }

    /**
     * Return a subgraph containing only the given nodes.
     * Edges between nodes outside the set are dropped.
     */
    public CitationGraph subgraph(Set<String> nodeSet) {
        CitationGraph sub = new CitationGraph();
        for (String u : nodeSet) {
            for (String v : successors(u)) {
                if (nodeSet.contains(v)) sub.addEdge(u, v);
            }
            Integer yr = getYear(u);
            if (yr != null) sub.setYear(u, yr);
        }
        return sub;
    }
}
