package dev.customergraph.customer;

import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import dev.customergraph.customer.CustomerRelationshipsResponse.AccountSummary;
import dev.customergraph.customer.CustomerRelationshipsResponse.CustomerProfile;
import dev.customergraph.customer.CustomerRelationshipsResponse.PossibleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
            WHERE type(firstLink) IN ['LIVES_AT', 'HAS_PHONE', 'HAS_EMAIL']
              AND type(secondLink) = type(firstLink)
              AND other.customer_id <> customer.customer_id
            WITH other,
                 collect(DISTINCT type(firstLink)) AS evidenceTypes,
                 count(DISTINCT shared) AS sharedRecordCount
            RETURN other.customer_id AS customerId,
                   other.first_name AS firstName,
                   other.last_name AS lastName,
                   evidenceTypes,
                   sharedRecordCount
            ORDER BY sharedRecordCount DESC, customerId
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

        List<PossibleConnection> possibleConnections = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(CONNECTIONS_QUERY, parameters)) {
            List<String> evidenceTypes = record.getValue("evidenceTypes");
            Number sharedRecordCount = record.getValue("sharedRecordCount");
            possibleConnections.add(new PossibleConnection(
                    record.getString("customerId"),
                    record.getString("firstName"),
                    record.getString("lastName"),
                    evidenceTypes == null ? List.of() : List.copyOf(evidenceTypes),
                    sharedRecordCount == null ? 0 : sharedRecordCount.longValue()));
        }

        log.debug("FalkorDB relationship lookup returned {} account(s) and {} possible connection(s)",
                accounts.size(), possibleConnections.size());
        return new CustomerRelationshipsResponse(customer, List.copyOf(accounts), List.copyOf(possibleConnections));
    }
}
