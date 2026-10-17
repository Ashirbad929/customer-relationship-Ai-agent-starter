package dev.customergraph.api;

import com.falkordb.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final Graph graph;
    private final String graphName;

    public HealthController(Graph graph, @Value("${app.falkordb.graph}") String graphName) {
        this.graph = graph;
        this.graphName = graphName;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        log.debug("Application health check requested");
        return Map.of("status", "ok");
    }

    @GetMapping("/graph/health")
    public Map<String, String> graphHealth() {
        log.debug("Checking FalkorDB graph connectivity for '{}'", graphName);
        try {
            graph.query("RETURN 1 AS ready");
        } catch (RuntimeException exception) {
            log.error("FalkorDB graph health check failed for '{}'", graphName, exception);
            throw exception;
        }
        log.info("FalkorDB graph '{}' is reachable", graphName);
        return Map.of("status", "connected", "graph", graphName);
    }
}
