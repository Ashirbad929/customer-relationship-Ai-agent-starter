package dev.customergraph.customer;

import java.util.List;

public record CustomerAddressResponse(String customerId, List<AddressDetails> addresses) {

    public record AddressDetails(
            String addressId,
            String line1,
            String unit,
            String city,
            String postalCode) {
    }
}
