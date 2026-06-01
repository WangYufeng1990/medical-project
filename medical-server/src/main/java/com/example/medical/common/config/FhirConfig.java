package com.example.medical.common.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @RestController
    public static class CapabilityStatementController {

        private static final String SMART_ON_FHIR = "http://hl7.org/fhir/smart-app-launch";
        private static final String OAUTH_URIS_EXT = "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris";

        private final FhirContext fhirContext;

        public CapabilityStatementController(FhirContext fhirContext) {
            this.fhirContext = fhirContext;
        }

        @GetMapping("/api/v1/fhir/metadata")
        public ResponseEntity<String> capabilityStatement() {
            CapabilityStatement cs = new CapabilityStatement();
            cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
            cs.setDate(new java.util.Date());
            cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
            cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
            cs.setPublisher("Medical Management System");
            cs.addFormat("application/fhir+json");
            cs.addImplementationGuide(
                    "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core");

            CapabilityStatement.CapabilityStatementRestComponent rest =
                    cs.addRest();
            rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

            CapabilityStatement.CapabilityStatementRestSecurityComponent security =
                    rest.getSecurity();
            security.addService().addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/restful-security-service")
                    .setCode("SMART-on-FHIR")
                    .setDisplay("SMART on FHIR");
            security.addExtension()
                    .setUrl(OAUTH_URIS_EXT)
                    .addExtension().setUrl("token").setValue(new org.hl7.fhir.r4.model.UriType(
                            "/api/v1/auth/login"));
            security.addExtension()
                    .setUrl(OAUTH_URIS_EXT)
                    .addExtension().setUrl("authorize").setValue(new org.hl7.fhir.r4.model.UriType(
                            "/api/v1/auth/login"));

            CapabilityStatement.CapabilityStatementRestResourceComponent patientResource =
                    rest.addResource();
            patientResource.setType("Patient");
            patientResource.addInteraction().setCode(
                    CapabilityStatement.TypeRestfulInteraction.READ);
            patientResource.addInteraction().setCode(
                    CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);

            CapabilityStatement.CapabilityStatementRestResourceComponent encounterResource =
                    rest.addResource();
            encounterResource.setType("Encounter");
            encounterResource.addInteraction().setCode(
                    CapabilityStatement.TypeRestfulInteraction.READ);

            CapabilityStatement.CapabilityStatementRestResourceComponent medResource =
                    rest.addResource();
            medResource.setType("MedicationRequest");
            medResource.addInteraction().setCode(
                    CapabilityStatement.TypeRestfulInteraction.READ);

            CapabilityStatement.CapabilityStatementRestResourceComponent conditionResource =
                    rest.addResource();
            conditionResource.setType("Condition");
            conditionResource.addInteraction().setCode(
                    CapabilityStatement.TypeRestfulInteraction.READ);

            CapabilityStatement.CapabilityStatementRestResourceComponent allergyResource =
                    rest.addResource();
            allergyResource.setType("AllergyIntolerance");
            allergyResource.addInteraction().setCode(
                    CapabilityStatement.TypeRestfulInteraction.READ);

            IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/fhir+json"))
                    .body(parser.encodeResourceToString(cs));
        }
    }
}
