package dev.customergraph.customer;

import java.util.List;

public record CustomerLoansResponse(String customerId, List<LoanDetails> loans) {

    public record LoanDetails(
            String loanId,
            String accountId,
            String loanType,
            String status,
            Long demoPrincipalInr,
            Long demoOutstandingInr,
            Long demoEmiInr,
            String nextDueDate,
            String dataOrigin) {
    }
}
