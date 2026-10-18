package dev.customergraph.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "agent.enabled", havingValue = "true")
public class GeminiWebSearchService {

    private static final Logger log = LoggerFactory.getLogger(GeminiWebSearchService.class);

    private final RestClient geminiClient;
    private final String apiKey;
    private final String model;

    public GeminiWebSearchService(
            @Value("${spring.ai.google.genai.api-key:}") String apiKey,
            @Value("${spring.ai.google.genai.chat.model:gemini-3.8-flash}") String model) {
        this.geminiClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public PublicWebSearchResponse search(String query) {
        if (apiKey.isBlank()) {
            log.error("Gemini web search requested but GEMINI_API_KEY is not configured");
            throw new IllegalStateException("GEMINI_API_KEY is required for Gemini web search");
        }

        log.debug("Calling Gemini Google Search grounding using model {}", model);
        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", query)))),
                "tools", List.of(Map.of("google_search", Map.of())));

        Map<?, ?> response;
        try {
            response = geminiClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            log.error("Gemini Google Search grounding request failed", exception);
            throw exception;
        }

        if (response == null) {
            log.error("Gemini returned an empty grounded-search response");
            throw new IllegalStateException("Gemini web search returned an empty response");
        }
        PublicWebSearchResponse result = parseResponse(response);
        log.debug("Gemini web search completed with {} source(s)", result.sources().size());
        return result;
    }

    private PublicWebSearchResponse parseResponse(Map<?, ?> response) {
        StringBuilder answer = new StringBuilder();
        Map<String, PublicWebSearchResponse.Source> sourcesByUrl = new LinkedHashMap<>();
        for (Object rawCandidate : list(response.get("candidates"))) {
            Map<?, ?> candidate = map(rawCandidate);
            Map<?, ?> content = map(candidate.get("content"));
            for (Object rawPart : list(content.get("parts"))) {
                String text = string(map(rawPart).get("text"));
                if (!text.isBlank()) {
                    if (!answer.isEmpty()) {
                        answer.append('\n');
                    }
                    answer.append(text);
                }
            }
            Map<?, ?> groundingMetadata = map(candidate.get("groundingMetadata"));
            for (Object rawChunk : list(groundingMetadata.get("groundingChunks"))) {
                Map<?, ?> web = map(map(rawChunk).get("web"));
                String url = string(web.get("uri"));
                if (!url.isBlank()) {
                    sourcesByUrl.putIfAbsent(url, new PublicWebSearchResponse.Source(
                            stringOrDefault(web.get("title"), url), url));
                }
            }
        }
        List<PublicWebSearchResponse.Source> sources = new ArrayList<>(sourcesByUrl.values());
        return new PublicWebSearchResponse(answer.toString(), List.copyOf(sources));
    }

    private Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> result ? result : Map.of();
    }

    private List<?> list(Object value) {
        return value instanceof List<?> result ? result : List.of();
    }

    private String string(Object value) {
        return value instanceof String result ? result : "";
    }

    private String stringOrDefault(Object value, String fallback) {
        String result = string(value);
        return result.isBlank() ? fallback : result;
    }
}
