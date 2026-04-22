import java.io.*;
import java.nio.file.*;

/**
 * Loads the SNAP Cit-HEP-Ph edge list and optional dates file into a CitationGraph.
 */
public class GraphLoader {

    /**
     * Parse the SNAP edge list (lines starting with '#' are comments).
     * Edge format: "fromId toId"
     */
    public static CitationGraph loadEdgeList(String filePath) throws IOException {
        CitationGraph G = new CitationGraph();
        long lineCount = 0, edgeCount = 0;

        System.out.println("Loading edge list from: " + filePath);
        try (BufferedReader br = Files.newBufferedReader(Path.of(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lineCount++;
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                G.addEdge(parts[0], parts[1]);
                edgeCount++;
            }
        }
        System.out.printf("  Nodes: %,d   Edges: %,d%n", G.nodeCount(), G.edgeCount());
        return G;
    }

    /**
     * Attach publication years from the SNAP dates file.
     * Format per line: "paperId YYYY-MM-DD"
     */
    public static void attachDates(CitationGraph G, String datesPath) throws IOException {
        int attached = 0;
        System.out.println("Attaching dates from: " + datesPath);
        try (BufferedReader br = Files.newBufferedReader(Path.of(datesPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String nodeId = parts[0];
                String dateStr = parts[1];
                if (dateStr.length() >= 4) {
                    try {
                        int year = Integer.parseInt(dateStr.substring(0, 4));
                        G.setYear(nodeId, year);
                        attached++;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        System.out.printf("  Dates attached to %,d nodes.%n", attached);
    }
}
