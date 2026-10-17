package dev.customergraph.config;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class FalkorDbConfiguration {

    @Bean(destroyMethod = "close")
    Driver falkorDbDriver(@Value("${app.falkordb.url}") URI uri) {
        return FalkorDB.driver(uri);
    }

    @Bean
    Graph customerGraph(Driver driver, @Value("${app.falkordb.graph}") String graphName) {
        return driver.graph(graphName);
    }
}
