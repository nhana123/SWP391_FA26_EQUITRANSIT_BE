/*
 * BackendApplication
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Start the Spring Boot application.
 */
package com.equitransit.backend;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/** Starts the EquiTransit backend. */
@SpringBootApplication
public class BackendApplication {

    /**
     * Starts the application.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
