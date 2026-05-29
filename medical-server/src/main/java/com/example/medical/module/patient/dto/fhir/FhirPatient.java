package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
public class FhirPatient {
    private final String resourceType = "Patient";
    private String id;
    private List<FhirIdentifier> identifier;
    private List<FhirHumanName> name;
    private String gender;
    private String birthDate;
    private List<FhirAddress> address;
    private List<FhirContactPoint> telecom;
    private List<FhirExtension> extension;

    public FhirPatient addIdentifier(FhirIdentifier identifier) {
        if (this.identifier == null) this.identifier = new ArrayList<>();
        this.identifier.add(identifier);
        return this;
    }

    public FhirPatient addName(FhirHumanName name) {
        if (this.name == null) this.name = new ArrayList<>();
        this.name.add(name);
        return this;
    }

    public FhirPatient addAddress(FhirAddress address) {
        if (this.address == null) this.address = new ArrayList<>();
        this.address.add(address);
        return this;
    }

    public FhirPatient addTelecom(FhirContactPoint telecom) {
        if (this.telecom == null) this.telecom = new ArrayList<>();
        this.telecom.add(telecom);
        return this;
    }

    public FhirPatient addExtension(FhirExtension extension) {
        if (this.extension == null) this.extension = new ArrayList<>();
        this.extension.add(extension);
        return this;
    }
}
