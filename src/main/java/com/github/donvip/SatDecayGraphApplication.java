package com.github.donvip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class SatDecayGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(SatDecayGraphApplication.class, args);
    }
}
