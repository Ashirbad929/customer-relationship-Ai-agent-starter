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
public class OpenAiWebSearchService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiWebSearchService.class);

    private final RestClient openAiClient;
    private final String apiKey;
    private final String model;

    public OpenAiWebSearchService(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${OPENAI_MODEL:${spring.ai.openai.chat.model:gpt-5-mini}}") String model) {
        this.openAiClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public OpenAiWebSearchResponse search(String query) {
        if (apiKey.isBlank()) {
            log.error("Web search requested but OPENAI_API_KEY is not configured");
            throw new IllegalStateException("OPENAI_API_KEY is required for web search");
        }

        log.debug("Calling OpenAI Responses API web search using model {}", model);

        Map<String, Object> request = Map.of(
                "model", model,
                "input", query,
                "tools", List.of(Map.of("type", "web_search")),
                "tool_choice", "required",
                "reasoning", Map.of("effort", "none"),
                "store", false);

        Map<?, ?> response;
        try {
            response = openAiClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            log.error("OpenAI Responses API web search request failed", exception);
            throw exception;
        }

        if (response == null) {
            log.error("OpenAI Responses API returned an empty web-search response");
            throw new IllegalStateException("OpenAI web search returned an empty response");
        }
        OpenAiWebSearchResponse result = parseResponse(response);
        log.debug("OpenAI web search completed with {} source(s)", result.sources().size());
        return result;
    }

    private OpenAiWebSearchResponse parseResponse(Map<?, ?> response) {
        StringBuilder answer = new StringBuilder();
        Map<String, OpenAiWebSearchResponse.Source> sourcesByUrl = new LinkedHashMap<>();
        for (Object rawItem : list(response.get("output"))) {
            Map<?, ?> item = map(rawItem);
            if (!"message".equals(string(item.get("type")))) {
                continue;
            }
            for (Object rawContent : list(item.get("content"))) {
                Map<?, ?> content = map(rawContent);
                if (!"output_text".equals(string(content.get("type")))) {
                    continue;
                }
                String text = string(content.get("text"));
                if (!answer.isEmpty()) {
                    answer.append('\n');
                }
                answer.append(text);
                for (Object rawAnnotation : list(content.get("annotations"))) {
                    Map<?, ?> annotation = map(rawAnnotation);
                    if ("url_citation".equals(string(annotation.get("type")))) {
                        String url = string(annotation.get("url"));
                        if (!url.isBlank()) {
                            sourcesByUrl.putIfAbsent(url, new OpenAiWebSearchResponse.Source(
                                    stringOrDefault(annotation.get("title"), url), url));
                        }
                    }
                }
            }
        }
        List<OpenAiWebSearchResponse.Source> sources = new ArrayList<>(sourcesByUrl.values());
        return new OpenAiWebSearchResponse(answer.toString(), List.copyOf(sources));
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
