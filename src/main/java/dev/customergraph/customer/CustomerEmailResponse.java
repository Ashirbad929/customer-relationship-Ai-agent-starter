package dev.customergraph.customer;

import java.util.List;

public record CustomerEmailResponse(String customerId, List<String> emailAddresses) {
}
