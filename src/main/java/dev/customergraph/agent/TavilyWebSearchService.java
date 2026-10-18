package dev.customergraph.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "agent.enabled", havingValue = "true")
public class TavilyWebSearchService {

    private static final Logger log = LoggerFactory.getLogger(TavilyWebSearchService.class);

    private final RestClient tavilyClient;
    private final String apiKey;

    public TavilyWebSearchService(@Value("${app.web-search.tavily.api-key:}") String apiKey) {
        this.tavilyClient = RestClient.builder()
                .baseUrl("https://api.tavily.com")
                .build();
        this.apiKey = apiKey;
    }

    public PublicWebSearchResponse search(String query) {
        if (apiKey.isBlank()) {
            log.error("Web search requested but TAVILY_API_KEY is not configured");
            throw new IllegalStateException("TAVILY_API_KEY is required for web search");
        }

        log.debug("Calling Tavily web search; query length is {} characters", query.length());
        Map<String, Object> request = Map.of(
                "query", query,
                "max_results", 5,
                "search_depth", "basic",
                "include_answer", false,
                "include_raw_content", false);

        Map<?, ?> response;
        try {
            response = tavilyClient.post()
                    .uri("/search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            log.error("Tavily web search request failed", exception);
            throw exception;
        }

        if (response == null) {
            log.error("Tavily returned an empty search response");
            throw new IllegalStateException("Web search returned an empty response");
        }

        PublicWebSearchResponse result = parseResponse(response);
        log.debug("Tavily web search completed with {} source(s)", result.sources().size());
        return result;
    }

    private PublicWebSearchResponse parseResponse(Map<?, ?> response) {
        List<String> snippets = new ArrayList<>();
        Map<String, PublicWebSearchResponse.Source> sourcesByUrl = new LinkedHashMap<>();
        Object rawResults = response.get("results");
        if (rawResults instanceof List<?> results) {
            for (Object rawItem : results) {
                if (!(rawItem instanceof Map<?, ?> item)) {
                    continue;
                }
                String title = value(item, "title", "Web result");
                String url = value(item, "url", "");
                String content = value(item, "content", "");
                if (!url.isBlank()) {
                    sourcesByUrl.putIfAbsent(url, new PublicWebSearchResponse.Source(title, url));
                }
                if (!content.isBlank()) {
                    snippets.add(title + ": " + content);
                }
            }
        }

        String answer = snippets.isEmpty()
                ? "No useful web results were returned."
                : String.join("\n", snippets);
        return new PublicWebSearchResponse(answer, List.copyOf(sourcesByUrl.values()));
    }

    private String value(Map<?, ?> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
