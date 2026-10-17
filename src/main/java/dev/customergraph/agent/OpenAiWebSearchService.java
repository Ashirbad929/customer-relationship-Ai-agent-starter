package dev.customergraph.agent;

import com.fasterxml.jackson.databind.JsonNode;
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
            @Value("${OPENAI_MODEL:gpt-6-luna}") String model) {
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

        JsonNode response;
        try {
            response = openAiClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
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

    private OpenAiWebSearchResponse parseResponse(JsonNode response) {
        StringBuilder answer = new StringBuilder();
        Map<String, OpenAiWebSearchResponse.Source> sourcesByUrl = new LinkedHashMap<>();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) {
                    continue;
                }
                if (!answer.isEmpty()) {
                    answer.append('\n');
                }
                answer.append(content.path("text").asText());
                for (JsonNode annotation : content.path("annotations")) {
                    if ("url_citation".equals(annotation.path("type").asText())) {
                        String url = annotation.path("url").asText();
                        if (!url.isBlank()) {
                            sourcesByUrl.putIfAbsent(url, new OpenAiWebSearchResponse.Source(
                                    annotation.path("title").asText(url), url));
                        }
                    }
                }
            }
        }
        List<OpenAiWebSearchResponse.Source> sources = new ArrayList<>(sourcesByUrl.values());
        return new OpenAiWebSearchResponse(answer.toString(), List.copyOf(sources));
    }
}
