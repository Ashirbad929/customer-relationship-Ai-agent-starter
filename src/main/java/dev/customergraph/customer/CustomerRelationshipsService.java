package dev.customergraph.customer;

import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import dev.customergraph.customer.CustomerRelationshipsResponse.AccountSummary;
import dev.customergraph.customer.CustomerRelationshipsResponse.CustomerProfile;
import dev.customergraph.customer.CustomerRelationshipsResponse.PossibleConnection;
import dev.customergraph.customer.CustomerRelationshipsResponse.TraversalEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CustomerRelationshipsService {

    private static final Logger log = LoggerFactory.getLogger(CustomerRelationshipsService.class);

    private static final String CUSTOMER_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})
            RETURN customer.customer_id AS customerId,
                   customer.first_name AS firstName,
                   customer.last_name AS lastName
            LIMIT 1
            """;

    private static final String ACCOUNTS_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[holding:HOLDS]->(account:Account)
            OPTIONAL MATCH (account)<-[:HOLDS]-(holder:Customer)
            RETURN account.account_id AS accountId,
                   account.account_type AS accountType,
                   holding.role AS role,
                   count(DISTINCT holder) AS holderCount
            ORDER BY account.account_id
            """;

    private static final String CONNECTIONS_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[firstLink]->(shared)<-[secondLink]-(other:Customer)
            WHERE ((type(firstLink) IN ['LIVES_AT', 'HAS_PHONE', 'HAS_EMAIL']
                    AND type(secondLink) = type(firstLink))
                   OR (type(firstLink) = 'HOLDS' AND type(secondLink) = 'HOLDS'))
              AND other.customer_id <> customer.customer_id
            WITH other, type(firstLink) AS relationshipType, count(DISTINCT shared) AS sharedRecordCount
            RETURN other.customer_id AS customerId,
                   other.first_name AS firstName,
                   other.last_name AS lastName,
                   relationshipType,
                   sharedRecordCount
            ORDER BY customerId, relationshipType
            """;

    private final Graph graph;

    public CustomerRelationshipsService(Graph graph) {
        this.graph = graph;
    }

    public CustomerRelationshipsResponse getRelationships(String customerId) {
        log.debug("Querying FalkorDB for customer relationships");
        Map<String, Object> parameters = Map.of("customerId", customerId);

        ResultSet customerResult = graph.readOnlyQuery(CUSTOMER_QUERY, parameters);
        Iterator<Record> customerIterator = customerResult.iterator();
        if (!customerIterator.hasNext()) {
            log.debug("Customer relationship lookup returned no matching customer");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        Record customerRecord = customerIterator.next();

        CustomerProfile customer = new CustomerProfile(
                customerRecord.getString("customerId"),
                customerRecord.getString("firstName"),
                customerRecord.getString("lastName"));

        List<AccountSummary> accounts = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(ACCOUNTS_QUERY, parameters)) {
            Number holderCount = record.getValue("holderCount");
            accounts.add(new AccountSummary(
                    record.getString("accountId"),
                    record.getString("accountType"),
                    record.getString("role"),
                    holderCount == null ? 0 : holderCount.longValue()));
        }

        Map<String, ConnectionAccumulator> connectionsByCustomer = new LinkedHashMap<>();
        for (Record record : graph.readOnlyQuery(CONNECTIONS_QUERY, parameters)) {
            Number sharedRecordCount = record.getValue("sharedRecordCount");
            String connectedCustomerId = record.getString("customerId");
            String relationshipType = record.getString("relationshipType");
            long count = sharedRecordCount == null ? 0 : sharedRecordCount.longValue();
            connectionsByCustomer.computeIfAbsent(connectedCustomerId, ignored -> new ConnectionAccumulator(
                    connectedCustomerId, record.getString("firstName"), record.getString("lastName")))
                    .add(relationshipType, count);
        }
        List<PossibleConnection> possibleConnections = connectionsByCustomer.values().stream()
                .map(ConnectionAccumulator::toResponse)
                .toList();

        log.debug("FalkorDB relationship lookup returned {} account(s) and {} possible connection(s)",
                accounts.size(), possibleConnections.size());
        return new CustomerRelationshipsResponse(customer, List.copyOf(accounts), List.copyOf(possibleConnections));
    }

    private static final class ConnectionAccumulator {
        private final String customerId;
        private final String firstName;
        private final String lastName;
        private final List<TraversalEvidence> evidence = new ArrayList<>();

        private ConnectionAccumulator(String customerId, String firstName, String lastName) {
            this.customerId = customerId;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        private void add(String relationshipType, long sharedRecordCount) {
            String path = switch (relationshipType) {
                case "LIVES_AT" -> "Customer → shared address record → Customer";
                case "HAS_PHONE" -> "Customer → shared phone record → Customer (value withheld)";
                case "HAS_EMAIL" -> "Customer → shared email record → Customer (value withheld)";
                case "HOLDS" -> "Customer → jointly held account → Customer";
                default -> "Customer → shared record → Customer";
            };
            evidence.add(new TraversalEvidence(relationshipType, sharedRecordCount, path));
        }

        private PossibleConnection toResponse() {
            long totalSharedRecords = evidence.stream().mapToLong(TraversalEvidence::sharedRecordCount).sum();
            List<String> types = evidence.stream().map(TraversalEvidence::relationshipType).toList();
            return new PossibleConnection(customerId, firstName, lastName, types, totalSharedRecords,
                    List.copyOf(evidence));
        }
    }
}
