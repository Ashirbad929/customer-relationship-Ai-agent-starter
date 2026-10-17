package dev.customergraph.customer;

import com.falkordb.Graph;
import com.falkordb.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CustomerPhoneService {

    private static final Logger log = LoggerFactory.getLogger(CustomerPhoneService.class);

    private static final String PHONE_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[:HAS_PHONE]->(phone:Phone)
            RETURN phone.number AS phoneNumber
            ORDER BY phone.number
            """;

    private final Graph graph;

    public CustomerPhoneService(Graph graph) {
        this.graph = graph;
    }

    public CustomerPhoneResponse getPhoneNumbers(String customerId) {
        log.debug("Querying FalkorDB for linked customer phone records");
        List<String> phoneNumbers = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(PHONE_QUERY, Map.of("customerId", customerId))) {
            phoneNumbers.add(record.getString("phoneNumber"));
        }
        log.debug("Phone lookup returned {} record(s)", phoneNumbers.size());
        return new CustomerPhoneResponse(customerId, List.copyOf(phoneNumbers));
    }
}
