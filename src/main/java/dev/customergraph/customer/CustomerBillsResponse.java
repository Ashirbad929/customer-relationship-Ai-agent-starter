package dev.customergraph.customer;

import java.util.List;

public record CustomerBillsResponse(String customerId, List<BillDetails> bills) {

    public record BillDetails(
            String billId,
            String accountId,
            String statementMonth,
            Long totalDueInr,
            Long minimumDueInr,
            Long amountPaidInr,
            String dueDate,
            String status,
            String dataOrigin) {
    }
}
