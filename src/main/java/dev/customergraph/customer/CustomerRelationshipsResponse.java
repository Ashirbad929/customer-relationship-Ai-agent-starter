package dev.customergraph.customer;

import java.util.List;

public record CustomerRelationshipsResponse(
        CustomerProfile customer,
        List<AccountSummary> accounts,
        List<PossibleConnection> possibleConnections) {

    public record CustomerProfile(String customerId, String firstName, String lastName) {
    }

    public record AccountSummary(String accountId, String accountType, String role) {
    }

    public record PossibleConnection(
            String customerId,
            String firstName,
            String lastName,
            List<String> evidenceTypes,
            long sharedRecordCount) {
    }
}
