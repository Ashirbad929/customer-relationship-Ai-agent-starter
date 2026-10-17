package dev.customergraph.customer;

import java.util.List;

public record CustomerPhoneResponse(String customerId, List<String> phoneNumbers) {
}
