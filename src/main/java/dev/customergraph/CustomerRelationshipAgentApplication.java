package dev.customergraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@SpringBootApplication
public class CustomerRelationshipAgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(CustomerRelationshipAgentApplication.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CustomerRelationshipAgentApplication.class);
        application.addInitializers(selectChatProvider());
        application.run(args);
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext> selectChatProvider() {
        return context -> {
            var environment = context.getEnvironment();
            String geminiKey = environment.getProperty("GEMINI_API_KEY", "").trim();
            String openAiKey = environment.getProperty("OPENAI_API_KEY", "").trim();
            String requestedProvider = environment.getProperty("AI_PROVIDER", "auto").trim().toLowerCase();

            String selectedProvider = switch (requestedProvider) {
                case "auto" -> !geminiKey.isBlank() ? "google-genai"
                        : !openAiKey.isBlank() ? "openai" : "google-genai";
                case "gemini", "google-genai" -> "google-genai";
                case "openai" -> "openai";
                default -> throw new IllegalStateException(
                        "AI_PROVIDER must be auto, google-genai, or openai");
            };

            boolean selectedKeyPresent = "openai".equals(selectedProvider)
                    ? !openAiKey.isBlank() : !geminiKey.isBlank();
            boolean agentEnabled = environment.getProperty("AGENT_ENABLED", Boolean.class, false);
            if (agentEnabled && !selectedKeyPresent) {
                String requiredKey = "openai".equals(selectedProvider) ? "OPENAI_API_KEY" : "GEMINI_API_KEY";
                throw new IllegalStateException("AGENT_ENABLED=true, but the selected " + selectedProvider
                        + " provider requires " + requiredKey + ". Set that key or choose another AI_PROVIDER.");
            }

            environment.getPropertySources().addFirst(new MapPropertySource("selectedAiProvider", Map.of(
                    "spring.ai.model.chat", selectedProvider,
                    "app.ai.provider", selectedProvider)));
            logger.info("Selected AI chat provider: {}{}", selectedProvider,
                    "auto".equals(requestedProvider) && !geminiKey.isBlank() && !openAiKey.isBlank()
                            ? " (both keys are set; Gemini has priority)" : "");
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady() {
        logger.info("Customer Relationship Agent application started successfully");
    }
}
