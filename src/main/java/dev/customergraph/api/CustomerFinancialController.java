package dev.customergraph.api;

import dev.customergraph.customer.CustomerBillsResponse;
import dev.customergraph.customer.CustomerCardsResponse;
import dev.customergraph.customer.CustomerFinancialService;
import dev.customergraph.customer.CustomerLoansResponse;
import dev.customergraph.customer.CustomerRelationshipsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customers")
public class CustomerFinancialController {

    private static final Logger log = LoggerFactory.getLogger(CustomerFinancialController.class);

    private final CustomerFinancialService financialService;

    public CustomerFinancialController(CustomerFinancialService financialService) {
        this.financialService = financialService;
    }

    @PostMapping("/cards")
    public CustomerCardsResponse cards(@RequestBody CustomerRelationshipsRequest request) {
        String customerId = requireCustomerId(request);
        log.debug("Direct card lookup requested");
        return financialService.getCards(customerId);
    }

    @PostMapping("/bills")
    public CustomerBillsResponse bills(@RequestBody CustomerRelationshipsRequest request) {
        String customerId = requireCustomerId(request);
        log.debug("Direct bill lookup requested");
        return financialService.getBills(customerId);
    }

    @PostMapping("/loans")
    public CustomerLoansResponse loans(@RequestBody CustomerRelationshipsRequest request) {
        String customerId = requireCustomerId(request);
        log.debug("Direct loan lookup requested");
        return financialService.getLoans(customerId);
    }

    private String requireCustomerId(CustomerRelationshipsRequest request) {
        if (request == null || request.customerId() == null || request.customerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        return request.customerId();
    }
}
