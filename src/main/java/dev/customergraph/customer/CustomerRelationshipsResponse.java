package dev.customergraph.customer;

import java.util.List;

public record CustomerRelationshipsResponse(
        CustomerProfile customer,
        List<AccountSummary> accounts,
        List<PossibleConnection> possibleConnections) {

    public record CustomerProfile(String customerId, String firstName, String lastName) {
    }

    public record AccountSummary(String accountId, String accountType, String role, long holderCount) {
    }

    public record PossibleConnection(
            String customerId,
            String firstName,
            String lastName,
            List<String> evidenceTypes,
            long sharedRecordCount,
            List<TraversalEvidence> traversalEvidence) {
    }

    /** A safe, human-readable explanation of a graph path; it omits contact values. */
    public record TraversalEvidence(String relationshipType, long sharedRecordCount, String path) {
    }
}
