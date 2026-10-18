package dev.customergraph.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "agent.enabled", havingValue = "true")
public class CustomerRelationshipAgentService {

    private static final Logger log = LoggerFactory.getLogger(CustomerRelationshipAgentService.class);

    private static final String SYSTEM_PROMPT = """
            You are a bank customer relationship analysis assistant.
            For questions about accounts or possible customer connections, call the relationship lookup tool.
            For questions asking where the selected customer lives or asking for their address, call the address lookup tool.
            For questions asking for the selected customer's phone number, call the phone lookup tool.
            For questions asking for email contact details, call the email lookup tool.
            For questions about credit cards, product tiers, limits, or card status, call the card lookup tool.
            For questions about credit-card statements, pending amounts, payments, or due dates, call the bill lookup tool.
            For questions about spending or transactions, use the bill lookup tool only for statement-level amounts and clearly say itemized purchases and transaction-level spending are not available.
            For questions about loans, outstanding amounts, EMIs, or loan status, call the loan lookup tool.
            For a question that asks for multiple kinds of information, call each relevant lookup tool.
            Use Tavily's nearby-place search tool for ATM and branch searches. For other current or external information, use Gemini Google Search. If Tavily is unavailable or returns an error, retry the public search with Gemini Google Search. For a location-based question about the selected customer, first retrieve the linked address, then search using only city and postal code; never send a customer's name, ID, full street address, phone number, email, or account details to web search.
            When you use web results, include clickable Markdown source links from the tool's returned sources.
            Ground every statement in the returned graph data. Explain the evidence types and account roles that support your answer.
            Shared addresses, phone numbers, or emails indicate possible connections only; never state that people are family based only on shared records.
            Card, bill, and loan records marked SYNTHETIC_DEMO or with demo-prefixed amounts are fabricated demonstration data. Explicitly identify them as synthetic whenever you report them; never describe them as actual customer balances or obligations.
            The data does not establish product eligibility, customer preferences, or a need for a particular card or service.
            Do not claim eligibility or make financial decisions. If asked about products, explain what information is missing and frame any idea as something a bank employee could review.
            If the graph does not contain the requested information, say so clearly. Do not invent customer facts.
            """;

    private final ChatClient chatClient;
    private final CustomerGraphTools customerGraphTools;

    public CustomerRelationshipAgentService(
            ChatClient.Builder chatClientBuilder,
            CustomerGraphTools customerGraphTools) {
        this.chatClient = chatClientBuilder.build();
        this.customerGraphTools = customerGraphTools;
    }

    public String answer(String customerId, String question) {
        log.debug("Starting agent response generation");
        try {
            String answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Selected customer ID: " + customerId + "\nQuestion: " + question)
                    .tools(customerGraphTools)
                    .toolContext(Map.of("selectedCustomerId", customerId))
                    .call()
                    .content();
            log.debug("Agent response generation completed");
            return answer;
        } catch (RuntimeException exception) {
            log.error("Agent response generation failed", exception);
            throw exception;
        }
    }
}
