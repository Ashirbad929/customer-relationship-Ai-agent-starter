package dev.customergraph.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
@ConditionalOnProperty(name = "agent.enabled", havingValue = "true")
public class AgentChatController {

    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    private final CustomerRelationshipAgentService agentService;

    public AgentChatController(CustomerRelationshipAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestBody AgentChatRequest request) {
        log.debug("Received agent chat request");
        if (request.customerId() == null || request.customerId().isBlank()) {
            log.warn("Rejected agent chat request because customerId is missing");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        }
        if (request.message() == null || request.message().isBlank()) {
            log.warn("Rejected agent chat request because message is missing");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        try {
            AgentChatResult result = agentService.answer(request.customerId(), request.message());
            log.debug("Agent chat request completed");
            return new AgentChatResponse(
                    request.customerId(),
                    result == null || result.answer() == null ? "The assistant returned an empty answer." : result.answer(),
                    result == null ? null : result.visualization());
        } catch (RuntimeException exception) {
            log.error("Agent chat request failed", exception);
            throw exception;
        }
    }
}
