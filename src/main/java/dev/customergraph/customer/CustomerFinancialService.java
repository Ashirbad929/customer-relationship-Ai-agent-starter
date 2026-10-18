package dev.customergraph.customer;

import com.falkordb.Graph;
import com.falkordb.Record;
import dev.customergraph.customer.CustomerBillsResponse.BillDetails;
import dev.customergraph.customer.CustomerCardsResponse.CardDetails;
import dev.customergraph.customer.CustomerLoansResponse.LoanDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CustomerFinancialService {

    private static final Logger log = LoggerFactory.getLogger(CustomerFinancialService.class);

    private static final String CARDS_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[holding:HOLDS]->(account:Account {account_type: 'CREDIT_CARD'})
            OPTIONAL MATCH (account)-[:HAS_PRODUCT]->(product:CardProduct)
            RETURN account.account_id AS accountId,
                   holding.role AS role,
                   product.product_name AS productName,
                   product.tier AS tier,
                   account.demo_credit_limit_inr AS demoCreditLimitInr,
                   account.demo_available_credit_inr AS demoAvailableCreditInr,
                   account.demo_card_status AS demoCardStatus,
                   coalesce(account.demo_data_origin, product.data_origin) AS dataOrigin
            ORDER BY account.account_id
            """;

    private static final String BILLS_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[:HOLDS]->(account:Account)-[:HAS_BILL]->(bill:CreditCardBill)
            RETURN bill.bill_id AS billId,
                   account.account_id AS accountId,
                   bill.statement_month AS statementMonth,
                   bill.total_due_inr AS totalDueInr,
                   bill.minimum_due_inr AS minimumDueInr,
                   bill.amount_paid_inr AS amountPaidInr,
                   bill.due_date AS dueDate,
                   bill.status AS status,
                   bill.data_origin AS dataOrigin
            ORDER BY bill.due_date, bill.bill_id
            """;

    private static final String LOANS_QUERY = """
            MATCH (customer:Customer {customer_id: $customerId})-[:HOLDS]->(account:Account {account_type: 'LOAN'})-[:HAS_LOAN_DETAILS]->(loan:LoanDetails)
            RETURN loan.loan_id AS loanId,
                   account.account_id AS accountId,
                   loan.loan_type AS loanType,
                   loan.status AS status,
                   loan.demo_principal_inr AS demoPrincipalInr,
                   loan.demo_outstanding_inr AS demoOutstandingInr,
                   loan.demo_emi_inr AS demoEmiInr,
                   loan.next_due_date AS nextDueDate,
                   loan.data_origin AS dataOrigin
            ORDER BY account.account_id
            """;

    private final Graph graph;

    public CustomerFinancialService(Graph graph) {
        this.graph = graph;
    }

    public CustomerCardsResponse getCards(String customerId) {
        log.debug("Querying FalkorDB for customer credit cards");
        List<CardDetails> cards = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(CARDS_QUERY, Map.of("customerId", customerId))) {
            cards.add(new CardDetails(
                    record.getString("accountId"), record.getString("role"), record.getString("productName"),
                    record.getString("tier"), number(record, "demoCreditLimitInr"),
                    number(record, "demoAvailableCreditInr"), record.getString("demoCardStatus"),
                    record.getString("dataOrigin")));
        }
        log.debug("Card lookup returned {} record(s)", cards.size());
        return new CustomerCardsResponse(customerId, List.copyOf(cards));
    }

    public CustomerBillsResponse getBills(String customerId) {
        log.debug("Querying FalkorDB for customer credit-card bills");
        List<BillDetails> bills = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(BILLS_QUERY, Map.of("customerId", customerId))) {
            bills.add(new BillDetails(
                    record.getString("billId"), record.getString("accountId"), record.getString("statementMonth"),
                    number(record, "totalDueInr"), number(record, "minimumDueInr"), number(record, "amountPaidInr"),
                    record.getString("dueDate"), record.getString("status"), record.getString("dataOrigin")));
        }
        log.debug("Bill lookup returned {} record(s)", bills.size());
        return new CustomerBillsResponse(customerId, List.copyOf(bills));
    }

    public CustomerLoansResponse getLoans(String customerId) {
        log.debug("Querying FalkorDB for customer loans");
        List<LoanDetails> loans = new ArrayList<>();
        for (Record record : graph.readOnlyQuery(LOANS_QUERY, Map.of("customerId", customerId))) {
            loans.add(new LoanDetails(
                    record.getString("loanId"), record.getString("accountId"), record.getString("loanType"),
                    record.getString("status"), number(record, "demoPrincipalInr"),
                    number(record, "demoOutstandingInr"), number(record, "demoEmiInr"),
                    record.getString("nextDueDate"), record.getString("dataOrigin")));
        }
        log.debug("Loan lookup returned {} record(s)", loans.size());
        return new CustomerLoansResponse(customerId, List.copyOf(loans));
    }

    private Long number(Record record, String field) {
        Object value = record.getValue(field);
        return value instanceof Number number ? number.longValue() : null;
    }
}
