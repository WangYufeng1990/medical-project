package com.example.medical.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("business")
                .packagesToScan("com.example.medical.module")
                .packagesToExclude(
                        "com.example.medical.module.patient.controller.FhirPatientController",
                        "com.example.medical.module.patient.controller.FhirObservationController")
                .build();
    }
}
