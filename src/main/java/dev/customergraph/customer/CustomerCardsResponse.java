package dev.customergraph.customer;

import java.util.List;

public record CustomerCardsResponse(String customerId, List<CardDetails> cards) {

    public record CardDetails(
            String accountId,
            String role,
            String productName,
            String tier,
            Long demoCreditLimitInr,
            Long demoAvailableCreditInr,
            String demoCardStatus,
            String dataOrigin) {
    }
}
