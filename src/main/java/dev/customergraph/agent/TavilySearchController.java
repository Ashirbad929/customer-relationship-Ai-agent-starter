package dev.customergraph.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/search")
@ConditionalOnProperty(name = "agent.enabled", havingValue = "true")
public class TavilySearchController {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchController.class);

    private final TavilyWebSearchService tavilyWebSearchService;

    public TavilySearchController(TavilyWebSearchService tavilyWebSearchService) {
        this.tavilyWebSearchService = tavilyWebSearchService;
    }

    @PostMapping("/tavily")
    @ResponseStatus(HttpStatus.OK)
    public PublicWebSearchResponse searchWithTavily(@RequestBody TavilySearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A public search query is required");
        }
        log.debug("Received direct Tavily search request; query length is {} characters", request.query().length());
        return tavilyWebSearchService.search(request.query().trim());
    }
}
