package dev.customergraph.agent;

import java.util.List;

public record OpenAiWebSearchResponse(String answer, List<Source> sources) {

    public record Source(String title, String url) {
    }
}
