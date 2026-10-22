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

    @GetMapping("/runs/{runId}")
    public Result<AgentRunContextVO> run(@RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
                                         @RequestHeader(value = "X-Agent-Capability", required = false) String capability,
                                         @PathVariable Long runId) {
        serviceToken.require(token);
        AgentRun run = capabilities.verify(runId, capability);
        requireActiveAccount(run);
        return Result.ok(AgentRunContextVO.of(run));
    }

    @PostMapping("/runs/{runId}/results")
    public Result<Void> result(@RequestHeader(value = "X-Agent-Service-Token", required = false) String token,
                               @RequestHeader(value = "X-Agent-Capability", required = false) String capability,
                               @PathVariable Long runId,
                               @Valid @RequestBody AgentRunResultRequest request) {
        serviceToken.require(token);
        AgentRun run = capabilities.verify(runId, capability);
        if (!request.getGeneration().equals(run.getExecutionGeneration())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "result belongs to a previous execution generation");
        }
        requireActiveAccount(run);
        if (!states.complete(runId, run.getExecutionGeneration())) {
            throw new BusinessException(ResultCode.CONFLICT, "run is no longer accepting a result");
        }
        capabilities.revoke(runId);
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
