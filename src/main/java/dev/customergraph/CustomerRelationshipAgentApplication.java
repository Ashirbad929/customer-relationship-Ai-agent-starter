package dev.customergraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class CustomerRelationshipAgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(CustomerRelationshipAgentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CustomerRelationshipAgentApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady() {
        logger.info("Customer Relationship Agent application started successfully");
    }
}
