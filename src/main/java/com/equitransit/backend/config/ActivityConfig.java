/*
 * ActivityConfig
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Enable bounded in-process notification delivery.
 */
package com.equitransit.backend.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


/** No external bus; existing SQL audit facts are the durable source. */
@Configuration
@EnableScheduling
public class ActivityConfig {
}
