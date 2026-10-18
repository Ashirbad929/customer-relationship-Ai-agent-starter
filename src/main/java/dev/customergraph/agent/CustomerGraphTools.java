package dev.customergraph.agent;

import dev.customergraph.customer.CustomerRelationshipsResponse;
import dev.customergraph.customer.CustomerRelationshipsService;
import dev.customergraph.customer.CustomerAddressResponse;
import dev.customergraph.customer.CustomerAddressService;
import dev.customergraph.customer.CustomerPhoneResponse;
import dev.customergraph.customer.CustomerPhoneService;
import dev.customergraph.customer.CustomerEmailResponse;
import dev.customergraph.customer.CustomerEmailService;
import dev.customergraph.customer.CustomerFinancialService;
import dev.customergraph.customer.CustomerCardsResponse;
import dev.customergraph.customer.CustomerBillsResponse;
import dev.customergraph.customer.CustomerLoansResponse;
import dev.customergraph.customer.CustomerActivityResponse;
import dev.customergraph.customer.CustomerActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
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
    private final CustomerEmailService customerEmailService;
    private final CustomerFinancialService customerFinancialService;
    private final CustomerActivityService customerActivityService;
    private final OpenAiWebSearchService openAiWebSearchService;

    public CustomerGraphTools(
            CustomerRelationshipsService customerRelationshipsService,
            CustomerAddressService customerAddressService,
            CustomerPhoneService customerPhoneService,
            CustomerEmailService customerEmailService,
            CustomerFinancialService customerFinancialService,
            CustomerActivityService customerActivityService,
            OpenAiWebSearchService openAiWebSearchService) {
        this.customerRelationshipsService = customerRelationshipsService;
        this.customerAddressService = customerAddressService;
        this.customerPhoneService = customerPhoneService;
        this.customerEmailService = customerEmailService;
        this.customerFinancialService = customerFinancialService;
        this.customerActivityService = customerActivityService;
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

    @Tool(description = "Retrieve the selected customer's linked email addresses. Use only when asked about contact information.")
    public CustomerEmailResponse loadSelectedCustomerEmailAddresses(ToolContext toolContext) {
        log.debug("Agent selected the email lookup tool");
        return customerEmailService.getEmailAddresses(selectedCustomerId(toolContext));
    }

    @Tool(description = "Retrieve the selected customer's credit-card accounts, linked card product tiers, and demo-only card details. Clearly state that values marked SYNTHETIC_DEMO are fabricated sample data.")
    public CustomerCardsResponse loadSelectedCustomerCards(ToolContext toolContext) {
        log.debug("Agent selected the credit-card lookup tool");
        return customerFinancialService.getCards(selectedCustomerId(toolContext));
    }

    @Tool(description = "Retrieve the selected customer's linked credit-card bills, amounts, due dates, and statuses. Clearly state that records marked SYNTHETIC_DEMO are fabricated sample data.")
    public CustomerBillsResponse loadSelectedCustomerBills(ToolContext toolContext) {
        log.debug("Agent selected the credit-card bill lookup tool");
        return customerFinancialService.getBills(selectedCustomerId(toolContext));
    }

    @Tool(description = "Retrieve the selected customer's linked loan details, types, statuses, balances, EMI, and due dates. Clearly state that values marked SYNTHETIC_DEMO are fabricated sample data.")
    public CustomerLoansResponse loadSelectedCustomerLoans(ToolContext toolContext) {
        log.debug("Agent selected the loan lookup tool");
        return customerFinancialService.getLoans(selectedCustomerId(toolContext));
    }

    @Tool(description = "Trace the selected customer's synthetic account activity and find other customers who transacted at the same merchants. Use for transaction, spending-pattern, merchant, or shared-merchant questions. Results are fabricated SYNTHETIC_DEMO data; shared merchant activity is not proof of a personal relationship or suspicious behavior.")
    public CustomerActivityResponse investigateSelectedCustomerActivity(ToolContext toolContext) {
        log.debug("Agent selected multi-hop customer-account-transaction-merchant traversal");
        return customerActivityService.getActivity(selectedCustomerId(toolContext));
    }

    @Tool(description = "Search the public web directly through OpenAI for current or external information, including ATM and bank-branch listings. Use this tool whenever the user asks for web-search results. For customer-specific nearby searches, first retrieve the linked address and search using only city and postal code. Never send customer names, IDs, full street addresses, phone numbers, email addresses, or account details to web search. Include source links in the result.")
    public PublicWebSearchResponse searchPublicWeb(
            @ToolParam(description = "A concise public-information search query. Do not include customer-identifying or contact details.") String query,
            ToolContext toolContext) {
        String customerId = selectedCustomerId(toolContext);
        String safeQuery = removeCustomerData(query, customerId);
        log.debug("Agent selected OpenAI web search; sanitized query length is {} characters", safeQuery.length());
        OpenAiWebSearchResponse response = openAiWebSearchService.search(safeQuery);
        List<PublicWebSearchResponse.Source> sources = response.sources().stream()
                .map(source -> new PublicWebSearchResponse.Source(source.title(), source.url()))
                .toList();
        return new PublicWebSearchResponse(response.answer(), sources);
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
