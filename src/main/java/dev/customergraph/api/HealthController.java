package dev.customergraph.api;

import com.falkordb.Graph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final Graph graph;
    private final String graphName;

    public HealthController(Graph graph, @Value("${app.falkordb.graph}") String graphName) {
        this.graph = graph;
        this.graphName = graphName;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/graph/health")
    public Map<String, String> graphHealth() {
        graph.query("RETURN 1 AS ready");
        return Map.of("status", "connected", "graph", graphName);
    }
}
