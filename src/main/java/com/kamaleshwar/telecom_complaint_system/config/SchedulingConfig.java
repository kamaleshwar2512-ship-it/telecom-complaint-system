package com.kamaleshwar.telecom_complaint_system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on Spring's {@code @Scheduled} support, which {@code SlaMonitoringService} relies on. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
