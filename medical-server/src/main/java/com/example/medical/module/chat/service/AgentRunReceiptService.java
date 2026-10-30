package com.example.medical.module.chat.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.chat.dto.agent.RunReceiptVO;
import com.example.medical.module.chat.entity.AgentRun;
import com.example.medical.module.chat.entity.AgentRunResult;
import com.example.medical.module.chat.repository.AgentRunRepository;
import com.example.medical.module.chat.repository.AgentRunResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Answers "did this generation's result get accepted" (Round 57.3). A refused replay is a 403 or a
 * 409, which on its own cannot distinguish a finished run from a stale one, so recovery asks here.
 */
@Service
@RequiredArgsConstructor
public class AgentRunReceiptService {

    private final AgentRunRepository runs;
    private final AgentRunResultRepository results;

    public RunReceiptVO receipt(Long runId, Long generation) {
        AgentRun run = runs.findById(runId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "run not found"));
        AgentRunResult result = results.findByRunId(runId).orElse(null);
        RunReceiptVO receipt = new RunReceiptVO();
        receipt.setRunId(runId);
        receipt.setGeneration(generation);
        receipt.setState(run.getState());
        receipt.setHasResult(result != null);
        receipt.setResultGeneration(result == null ? null : run.getExecutionGeneration());
        receipt.setAccepted(result != null && generation != null
                && generation.equals(run.getExecutionGeneration()));
        return receipt;
    }
}
