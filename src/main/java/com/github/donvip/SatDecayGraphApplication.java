package com.github.donvip;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;

@EnableCaching
@SpringBootApplication
public class SatDecayGraphApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(SatDecayGraphApplication.class);

    @Autowired
    private GraphService graphService;

    public static void main(String[] args) {
        SpringApplication.run(SatDecayGraphApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            graphService.generateGraphs();
        } catch (IOException | InterruptedException | SecurityException | ReflectiveOperationException e) {
            logger.error("Failed to generate graphs", e);
        }
        event.getApplicationContext().close();
    }
}
