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
public class CustomerEmailService {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailService.class);

    private static final String EMAIL_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[:HAS_EMAIL]->(email:Email)
            RETURN email.address AS emailAddress
            ORDER BY email.address
            """;

    private final Graph graph;

    public CustomerEmailService(Graph graph) {
        this.graph = graph;
    }

    public CustomerEmailResponse getEmailAddresses(String customerId) {
        log.debug("Querying FalkorDB for linked customer email records");
        List<String> emailAddresses = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(EMAIL_QUERY, Map.of("customerId", customerId))) {
            emailAddresses.add(record.getString("emailAddress"));
        }
        log.debug("Email lookup returned {} record(s)", emailAddresses.size());
        return new CustomerEmailResponse(customerId, List.copyOf(emailAddresses));
    }
}
