package dev.customergraph.api;

import dev.customergraph.customer.CustomerAddressResponse;
import dev.customergraph.customer.CustomerAddressService;
import dev.customergraph.customer.CustomerEmailResponse;
import dev.customergraph.customer.CustomerEmailService;
import dev.customergraph.customer.CustomerPhoneResponse;
import dev.customergraph.customer.CustomerPhoneService;
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
public class CustomerDataController {

    private static final Logger log = LoggerFactory.getLogger(CustomerDataController.class);

    private final CustomerAddressService addressService;
    private final CustomerPhoneService phoneService;
    private final CustomerEmailService emailService;

    public CustomerDataController(CustomerAddressService addressService, CustomerPhoneService phoneService,
                                  CustomerEmailService emailService) {
        this.addressService = addressService;
        this.phoneService = phoneService;
        this.emailService = emailService;
    }

    @PostMapping("/addresses")
    public CustomerAddressResponse addresses(@RequestBody CustomerRelationshipsRequest request) {
        String customerId = requireCustomerId(request);
        log.debug("Direct address lookup requested");
        return addressService.getAddresses(customerId);
    }

    @PostMapping("/phone-numbers")
    public CustomerPhoneResponse phoneNumbers(@RequestBody CustomerRelationshipsRequest request) {
        String customerId = requireCustomerId(request);
        log.debug("Direct phone lookup requested");
        return phoneService.getPhoneNumbers(customerId);
    }

    @PostMapping("/email-addresses")
    public CustomerEmailResponse emailAddresses(@RequestBody CustomerRelationshipsRequest request) {
        String customerId = requireCustomerId(request);
        log.debug("Direct email lookup requested");
        return emailService.getEmailAddresses(customerId);
    }

    private String requireCustomerId(CustomerRelationshipsRequest request) {
        if (request == null || request.customerId() == null || request.customerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        return request.customerId();
    }
}
