package dev.customergraph.agent;

import java.util.List;

public record PublicWebSearchResponse(String answer, List<Source> sources) {

    public record Source(String title, String url) {
    }
}
