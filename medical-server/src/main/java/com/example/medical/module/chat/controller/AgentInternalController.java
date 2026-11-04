package com.example.medical.module.chat.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.lookup.PatientLookup;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.PatientPortalAccountState;
import com.example.medical.module.chat.agent.AgentCapabilityService;
import com.example.medical.module.chat.agent.AgentServiceToken;
import com.example.medical.module.chat.dto.agent.AgentRunContextVO;
import com.example.medical.module.chat.dto.agent.AgentRunResultRequest;
import com.example.medical.module.chat.entity.AgentRun;
import com.example.medical.module.appointment.dto.AgentAppointmentView;
import com.example.medical.module.appointment.service.AgentAppointmentReadService;
import com.example.medical.module.chat.dto.agent.RunReceiptVO;
import com.example.medical.module.chat.service.AgentReviewService;
import com.example.medical.module.chat.service.AgentRunReceiptService;
import com.example.medical.module.chat.service.AgentRunStateStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * The operations the agent service may perform (Round 55.3) — the whole internal allowlist.
 * <p>
 * Two credentials are required and they mean different things: the service token says
 * <em>which process</em> is calling, the capability says <em>which run and generation</em> it
 * may act on. Neither alone reaches patient data, and the service token is useless on the
 * patient-facing API because that needs a JWT.
 */
@RestController
@RequestMapping("/api/v1/internal/agent")
@RequiredArgsConstructor
public class AgentInternalController {

    private final AgentServiceToken serviceToken;
    private final AgentCapabilityService capabilities;
    private final PatientPortalAccountState patientAccounts;
    private final PatientLookup patients;
    private final AgentRunStateStore states;
    private final AgentReviewService reviews;
    private final AgentRunReceiptService receipts;
    private final AgentAppointmentReadService appointmentReads;

    @GetMapping("/runs/{runId}")
    public Result<AgentRunContextVO> run(@RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
                                         @RequestHeader(value = "X-Agent-Capability", required = false) String capability,
                                         @PathVariable Long runId) {
        serviceToken.require(token);
        AgentRun run = capabilities.verify(runId, capability);
        requireActiveAccount(run);
        return Result.ok(AgentRunContextVO.of(run));
    }

    /**
     * Read-only appointments for the patient this run serves (Round 58.1). The patient comes from the
     * run, never from the request, and the payload has no clinical text fields.
     */
    @GetMapping("/runs/{runId}/appointments")
    public Result<java.util.List<AgentAppointmentView>> appointments(
            @RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
            @RequestHeader(value = "X-Agent-Capability", required = false) String capability,
            @PathVariable Long runId,
            @RequestParam(defaultValue = "10") int limit,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.agent.appointment-timezone:America/New_York}") String timezone) {
        serviceToken.require(token);
        AgentRun run = capabilities.verify(runId, capability);
        requireActiveAccount(run);
        return Result.ok(appointmentReads.upcoming(run.getPatientId(),
                java.time.ZoneId.of(timezone), limit));
    }

    @GetMapping("/runs/{runId}/appointments/{appointmentId}")
    public Result<AgentAppointmentView> appointment(
            @RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
            @RequestHeader(value = "X-Agent-Capability", required = false) String capability,
            @PathVariable Long runId, @PathVariable Long appointmentId,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.agent.appointment-timezone:America/New_York}") String timezone) {
        serviceToken.require(token);
        AgentRun run = capabilities.verify(runId, capability);
        requireActiveAccount(run);
        return Result.ok(appointmentReads.one(run.getPatientId(), appointmentId,
                java.time.ZoneId.of(timezone)));
    }

    @GetMapping("/runs/{runId}/receipt")
    public Result<RunReceiptVO> receipt(
            @RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
            @PathVariable Long runId,
            @RequestParam(required = false) Long generation) {
        serviceToken.require(token);
        return Result.ok(receipts.receipt(runId, generation));
    }

    @PostMapping("/runs/{runId}/results")
    public Result<Void> result(@RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
                               @RequestHeader(value = "X-Agent-Capability", required = false) String capability,
                               @PathVariable Long runId,
                               @Valid @RequestBody AgentRunResultRequest request) {
        serviceToken.require(token);
        AgentRun run = capabilities.verify(runId, capability);
        if (!runId.equals(request.getRunId())) {
            // A result has to describe the run it was submitted for; a mismatch is a wiring bug,
            // not something to store under the capability's run.
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "run_id in the body does not match the path");
        }
        if (!request.getGeneration().equals(run.getExecutionGeneration())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "result belongs to a previous execution generation");
        }
        requireActiveAccount(run);
        reviews.acceptResult(runId, run.getExecutionGeneration(), request);
        return Result.ok();
    }

    /**
     * The account check the plan requires on every call: a run whose patient record or
     * portal account disappeared must not keep producing drafts.
     */
    private void requireActiveAccount(AgentRun run) {
        if (!patients.exists(run.getPatientId()) || !patientAccounts.isActive(run.getPatientId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "patient account is no longer active");
        }
    }
}
