package dev.customergraph.config;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class FalkorDbConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FalkorDbConfiguration.class);

    @Bean(destroyMethod = "close")
    Driver falkorDbDriver(@Value("${app.falkordb.url}") URI uri) {
        log.info("Configuring FalkorDB driver for {}:{}", uri.getHost(), uri.getPort());
        Driver driver = FalkorDB.driver(uri);
        log.info("FalkorDB driver configured");
        return driver;
    }

    @Bean
    Graph customerGraph(Driver driver, @Value("${app.falkordb.graph}") String graphName) {
        log.info("Configuring FalkorDB graph '{}'", graphName);
        return driver.graph(graphName);
    }
}
