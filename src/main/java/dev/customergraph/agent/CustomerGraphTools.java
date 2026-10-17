package dev.customergraph.agent;

import dev.customergraph.customer.CustomerRelationshipsResponse;
import dev.customergraph.customer.CustomerRelationshipsService;
import dev.customergraph.customer.CustomerAddressResponse;
import dev.customergraph.customer.CustomerAddressService;
import dev.customergraph.customer.CustomerPhoneResponse;
import dev.customergraph.customer.CustomerPhoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "agent.enabled", havingValue = "true")
public class CustomerGraphTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerGraphTools.class);

    private static final String SELECTED_CUSTOMER_ID = "selectedCustomerId";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("(?<!\\d)\\+?\\d[\\d\\s().-]{6,}\\d(?!\\d)");

    private final CustomerRelationshipsService customerRelationshipsService;
    private final CustomerAddressService customerAddressService;
    private final CustomerPhoneService customerPhoneService;
    private final OpenAiWebSearchService openAiWebSearchService;

    public CustomerGraphTools(
            CustomerRelationshipsService customerRelationshipsService,
            CustomerAddressService customerAddressService,
            CustomerPhoneService customerPhoneService,
            OpenAiWebSearchService openAiWebSearchService) {
        this.customerRelationshipsService = customerRelationshipsService;
        this.customerAddressService = customerAddressService;
        this.customerPhoneService = customerPhoneService;
        this.openAiWebSearchService = openAiWebSearchService;
    }

    @Tool(description = "Look up the selected customer's accounts and possible connections through shared address, phone, or email records. Use for account and relationship questions. Shared records are evidence of a possible connection, not proof of family.")
    public CustomerRelationshipsResponse loadSelectedCustomerRelationships(ToolContext toolContext) {
        log.debug("Agent selected the relationship lookup tool");
        return customerRelationshipsService.getRelationships(selectedCustomerId(toolContext));
    }

    @Tool(description = "Retrieve the selected customer's linked postal address details (street, unit, city, and postal code). Use for address questions.")
    public CustomerAddressResponse loadSelectedCustomerAddresses(ToolContext toolContext) {
        log.debug("Agent selected the address lookup tool");
        return customerAddressService.getAddresses(selectedCustomerId(toolContext));
    }

    @Tool(description = "Retrieve the selected customer's linked phone numbers. Use for phone-number questions.")
    public CustomerPhoneResponse loadSelectedCustomerPhoneNumbers(ToolContext toolContext) {
        log.debug("Agent selected the phone lookup tool");
        return customerPhoneService.getPhoneNumbers(selectedCustomerId(toolContext));
    }

    @Tool(description = "Search the live public web for current or external information needed to answer the user. For ATM searches, use only the city and postal code. Never include customer names, IDs, full street addresses, phone numbers, email addresses, or account details in the query. Return source links with the result.")
    public OpenAiWebSearchResponse searchPublicWeb(
            @ToolParam(description = "A concise public-information search query. Do not include customer-identifying or contact details.") String query,
            ToolContext toolContext) {
        String customerId = selectedCustomerId(toolContext);
        String safeQuery = removeCustomerData(query, customerId);
        log.debug("Agent selected web search; sanitized query length is {} characters", safeQuery.length());
        return openAiWebSearchService.search(safeQuery);
    }

    private String selectedCustomerId(ToolContext toolContext) {
        Object selectedCustomerId = toolContext.getContext().get(SELECTED_CUSTOMER_ID);
        if (!(selectedCustomerId instanceof String customerId) || customerId.isBlank()) {
            throw new IllegalArgumentException("No selected customer was provided for this request");
        }
        return customerId;
    }

    private String removeCustomerData(String query, String customerId) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("A web search query is required");
        }
        String safeQuery = query;
        CustomerRelationshipsResponse relationships = customerRelationshipsService.getRelationships(customerId);
        safeQuery = redact(safeQuery, customerId);
        safeQuery = redact(safeQuery, relationships.customer().firstName());
        safeQuery = redact(safeQuery, relationships.customer().lastName());
        for (CustomerRelationshipsResponse.PossibleConnection connection : relationships.possibleConnections()) {
            safeQuery = redact(safeQuery, connection.customerId());
            safeQuery = redact(safeQuery, connection.firstName());
            safeQuery = redact(safeQuery, connection.lastName());
        }
        for (CustomerRelationshipsResponse.AccountSummary account : relationships.accounts()) {
            safeQuery = redact(safeQuery, account.accountId());
        }
        for (CustomerAddressResponse.AddressDetails address : customerAddressService.getAddresses(customerId).addresses()) {
            safeQuery = redact(safeQuery, address.addressId());
            safeQuery = redact(safeQuery, address.line1());
            safeQuery = redact(safeQuery, address.unit());
        }
        for (String phoneNumber : customerPhoneService.getPhoneNumbers(customerId).phoneNumbers()) {
            safeQuery = redact(safeQuery, phoneNumber);
        }
        safeQuery = EMAIL_PATTERN.matcher(safeQuery).replaceAll(" ");
        safeQuery = LONG_NUMBER_PATTERN.matcher(safeQuery).replaceAll(" ");
        safeQuery = safeQuery.replaceAll("\\s+", " ").trim();
        if (safeQuery.isBlank()) {
            throw new IllegalArgumentException("The query contains no public search terms after removing customer details");
        }
        return safeQuery;
    }

    private String redact(String text, String sensitiveValue) {
        if (sensitiveValue == null || sensitiveValue.isBlank()) {
            return text;
        }
        return text.replaceAll("(?i)" + Pattern.quote(sensitiveValue), " ");
    }
}
