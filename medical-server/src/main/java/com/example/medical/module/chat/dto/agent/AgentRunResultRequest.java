package com.example.medical.module.chat.dto.agent;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * The result a worker submits (Round 57.1): the routing decision, the draft, the citations it
 * relies on and the validator's verdict. Python owns the schema; this class mirrors
 * contracts/agent/v1/run-result.request.schema.json field for field.
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AgentRunResultRequest {

    @NotBlank
    @Pattern(regexp = "v1", message = "unsupported contract_version")
    private String contractVersion;

    @NotNull
    @Min(1)
    private Long runId;

    /** The generation the result belongs to; a stale generation is a conflict. */
    @NotNull
    @Min(1)
    private Long generation;

    @NotBlank
    @Pattern(regexp = "APPOINTMENT|PRESCRIPTION_REFILL|BILLING|LAB_RESULTS|CLINICAL_QUESTION|"
            + "ADMINISTRATIVE_REQUEST|OTHER", message = "unknown intent")
    private String intent;

    @NotBlank
    @Pattern(regexp = "ROUTINE|URGENT|EMERGENCY", message = "unknown risk")
    private String risk;

    /** Required: a missing flag must not silently read as "no clinician needed". */
    @NotNull
    private Boolean requiresClinician;

    @NotBlank
    @Size(max = 1500)
    private String draft;

    @NotNull
    @Size(max = 8)
    private List<@Valid @NotNull Citation> citations;

    @Valid
    @NotNull
    private Validation validation;

    /** Absent when the run used no tool (Round 58.2). */
    @Valid
    private Tool tool;

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class Tool {
        @NotBlank
        @Size(max = 40)
        private String name;

        @NotBlank
        @Pattern(regexp = "ok|needs_human", message = "unknown tool status")
        private String status;

        @Pattern(regexp = "tool_failed|deadline_exceeded|call_budget_exhausted|tool_unavailable",
                message = "a handover reason is a fixed code")
        private String reason;

        @NotNull
        @Size(max = 5)
        private List<@Valid @NotNull Fact> facts;

        /**
         * Spring's mapper is configured to ignore unknown properties globally, so the annotation
         * cannot re-enable the failure. A tool block is new, and a field it does not define must be
         * a 400 rather than silently dropped (Round 58.2 review).
         */
        // Internal state only: a generated setter would let a request write "unknown_field": false
        // after an extra field and clear the mark (Round 58.2 review).
        @com.fasterxml.jackson.annotation.JsonIgnore
        @lombok.Getter(lombok.AccessLevel.NONE)
        @lombok.Setter(lombok.AccessLevel.NONE)
        private boolean unknownField;

        /** Records the fact only; the key is model input and must not reach a log or exception. */
        @JsonAnySetter
        public void markUnknown(String name, Object value) {
            this.unknownField = true;
        }

        public boolean hasUnknownFields() {
            return unknownField;
        }
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class Fact {
        @NotNull
        @Min(1)
        private Long appointmentId;

        @NotNull
        @Min(0)
        private Integer status;

        /** The stored wall time; validated as ISO-8601 when the entity is built. */
        @NotBlank
        @Size(max = 40)
        private String startTime;

        @NotBlank
        @Size(max = 64)
        private String timezone;

        @Min(0)
        private Integer durationMinutes;

        @Size(max = 200)
        private String providerName;

        @Size(max = 120)
        private String department;

        @com.fasterxml.jackson.annotation.JsonIgnore
        @lombok.Getter(lombok.AccessLevel.NONE)
        @lombok.Setter(lombok.AccessLevel.NONE)
        private boolean unknownField;

        @JsonAnySetter
        public void markUnknown(String name, Object value) {
            this.unknownField = true;
        }

        public boolean hasUnknownFields() {
            return unknownField;
        }
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Citation {
        @NotBlank
        @Size(max = 80)
        private String chunkId;

        @NotBlank
        @Size(max = 80)
        private String documentId;

        @NotNull
        @Min(1)
        private Long documentVersion;

        @NotBlank
        @Size(max = 200)
        private String title;

        @NotNull
        @Size(max = 8)
        private List<@NotBlank String> headingPath;

        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}", message = "a citation needs its content hash")
        private String contentHash;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Validation {
        @NotNull
        private Boolean ok;

        @NotBlank
        @Size(max = 32)
        private String kind;

        @NotNull
        @Size(max = 8)
        private List<@NotNull String> reasons;

        @NotNull
        @Size(max = 8)
        private List<@NotNull String> rejectedReasons;
    }
}
