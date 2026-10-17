package dev.customergraph.api;

import dev.customergraph.customer.CustomerRelationshipsResponse;
import dev.customergraph.customer.CustomerRelationshipsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerRelationshipsController {

    private final CustomerRelationshipsService customerRelationshipsService;

    public CustomerRelationshipsController(CustomerRelationshipsService customerRelationshipsService) {
        this.customerRelationshipsService = customerRelationshipsService;
    }

    @GetMapping("/{customerId}/relationships")
    public CustomerRelationshipsResponse getRelationships(@PathVariable String customerId) {
        return customerRelationshipsService.getRelationships(customerId);
    }
}
