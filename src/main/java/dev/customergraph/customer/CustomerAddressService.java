package dev.customergraph.customer;

import com.falkordb.Graph;
import com.falkordb.Record;
import dev.customergraph.customer.CustomerAddressResponse.AddressDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CustomerAddressService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAddressService.class);

    private static final String ADDRESS_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[:LIVES_AT]->(address:Address)
            RETURN address.address_id AS addressId,
                   address.line1 AS line1,
                   address.unit AS unit,
                   address.city AS city,
                   address.postal_code AS postalCode
            ORDER BY address.address_id
            """;

    private final Graph graph;

    public CustomerAddressService(Graph graph) {
        this.graph = graph;
    }

    public CustomerAddressResponse getAddresses(String customerId) {
        log.debug("Querying FalkorDB for linked customer address records");
        List<AddressDetails> addresses = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(ADDRESS_QUERY, Map.of("customerId", customerId))) {
            addresses.add(new AddressDetails(
                    record.getString("addressId"),
                    record.getString("line1"),
                    record.getString("unit"),
                    record.getString("city"),
                    record.getString("postalCode")));
        }
        log.debug("Address lookup returned {} record(s)", addresses.size());
        return new CustomerAddressResponse(customerId, List.copyOf(addresses));
    }
}
