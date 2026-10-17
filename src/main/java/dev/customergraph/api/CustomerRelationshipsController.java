package dev.customergraph.api;

import dev.customergraph.customer.CustomerRelationshipsResponse;
import dev.customergraph.customer.CustomerRelationshipsRequest;
import dev.customergraph.customer.CustomerRelationshipsService;
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
public class CustomerRelationshipsController {

    private static final Logger log = LoggerFactory.getLogger(CustomerRelationshipsController.class);

    private final CustomerRelationshipsService customerRelationshipsService;

    public CustomerRelationshipsController(CustomerRelationshipsService customerRelationshipsService) {
        this.customerRelationshipsService = customerRelationshipsService;
    }

    @PostMapping("/relationships")
    public CustomerRelationshipsResponse getRelationships(@RequestBody CustomerRelationshipsRequest request) {
        log.debug("Received customer relationship lookup request");
        if (request.customerId() == null || request.customerId().isBlank()) {
            log.warn("Rejected relationship lookup request because customerId is missing");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        CustomerRelationshipsResponse response = customerRelationshipsService.getRelationships(request.customerId());
        log.debug("Relationship lookup completed: {} account(s), {} possible connection(s)",
                response.accounts().size(), response.possibleConnections().size());
        return response;
    }
}
