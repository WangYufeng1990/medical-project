package com.example.medical.module.chat.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.chat.dto.agent.AgentRunResultRequest;
import com.example.medical.module.chat.dto.agent.ReviewItemVO;
import com.example.medical.module.chat.entity.AgentReviewClaim;
import com.example.medical.module.chat.entity.AgentRun;
import com.example.medical.module.chat.entity.AgentRunResult;
import com.example.medical.module.chat.entity.AgentRunState;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.AgentReviewClaimRepository;
import com.example.medical.module.chat.repository.AgentRunRepository;
import com.example.medical.module.chat.repository.AgentRunResultRepository;
import com.example.medical.module.chat.repository.ChatConversationRepository;
import com.example.medical.module.chat.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The staff review queue (Round 57.1).
 * <p>
 * An item exists when a patient's message is the last word in a thread: either with the draft the
 * worker produced, or — when the agent produced nothing usable — as a human-only item, because an
 * AI failure must never take a patient's question out of human hands.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentReviewService {

    private static final int MAX_THREADS = 200;

    private final AgentRunResultRepository results;
    private final AgentReviewClaimRepository claims;
    private final AgentRunRepository runs;
    private final ChatConversationRepository conversations;
    private final MessageRepository messages;
    private final DoctorPatientScope scope;
    private final ObjectMapper objectMapper;

    /** Stores (or replaces) the result of a run the worker just submitted. */
    @Transactional
    public void recordResult(AgentRun run, AgentRunResultRequest request) {
        AgentRunResult result = results.findByRunId(run.getId()).orElseGet(AgentRunResult::new);
        result.setRunId(run.getId());
        result.setIntent(request.getIntent());
        result.setRisk(request.getRisk());
        result.setRequiresClinician(request.isRequiresClinician());
        result.setDraft(request.getDraft());
        result.setValidationOk(request.getValidation().isOk());
        result.setValidationKind(request.getValidation().getKind());
        result.setValidationReasons(String.join("\n", request.getValidation().getReasons()));
        result.setRejectedReasons(String.join("\n", request.getValidation().getRejectedReasons()));
        result.setCitationsJson(citationsJson(request.getCitations()));
        results.save(result);
    }

    /** The queue for this reviewer: their own patient threads, or every thread for ADMIN. */
    public List<ReviewItemVO> queue(Long staffId, boolean admin) {
        List<ReviewItemVO> items = new ArrayList<>();
        for (Long patientId : patientPartners(staffId, admin)) {
            if (!scope.permits(patientId)) continue;
            ReviewItemVO item = itemFor(staffId, patientId);
            if (item != null) items.add(item);
        }
        return items;
    }

    /** Takes a run for review; one reviewer at a time, and a second cannot take it. */
    @Transactional
    public ReviewItemVO claim(Long staffId, Long runId) {
        AgentRun run = runs.findById(runId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "run not found"));
        scope.requireAccess(run.getPatientId());
        if (!AgentRunState.WAITING_REVIEW.name().equals(run.getState())) {
            throw new BusinessException(ResultCode.CONFLICT, "run is not waiting for review");
        }
        claims.findByRunId(runId).ifPresentOrElse(
                existing -> {
                    if (!existing.getStaffId().equals(staffId)) {
                        throw new BusinessException(ResultCode.CONFLICT,
                                "run is claimed by another reviewer");
                    }
                },
                () -> {
                    AgentReviewClaim claim = new AgentReviewClaim();
                    claim.setRunId(runId);
                    claim.setStaffId(staffId);
                    claims.save(claim);
                });
        ReviewItemVO item = itemFor(staffId, run.getPatientId());
        if (item == null) {
            throw new BusinessException(ResultCode.CONFLICT, "run is no longer in the queue");
        }
        return item;
    }

    private List<Long> patientPartners(Long staffId, boolean admin) {
        Set<Long> patientIds = new LinkedHashSet<>();
        if (admin) {
            conversations.findPageForType(ChatService.PATIENT,
                            PageRequest.of(0, MAX_THREADS))
                    .forEach(conversation -> patientIds.add(
                            partnerId(conversation.getPartyAType(), conversation.getPartyAId(),
                                    conversation.getPartyBType(), conversation.getPartyBId())));
        } else {
            conversations.findPageForParty(ChatService.STAFF, staffId,
                            PageRequest.of(0, MAX_THREADS))
                    .forEach(conversation -> patientIds.add(
                            partnerId(conversation.getPartyAType(), conversation.getPartyAId(),
                                    conversation.getPartyBType(), conversation.getPartyBId())));
        }
        return new ArrayList<>(patientIds);
    }

    private static Long partnerId(String typeA, Long idA, String typeB, Long idB) {
        return ChatService.PATIENT.equals(typeA) ? idA : idB;
    }

    /** Null when the thread does not end with a patient message (nothing to answer). */
    private ReviewItemVO itemFor(Long staffId, Long patientId) {
        List<Message> thread = messages.findMessagesBetween(
                staffId, ChatService.STAFF, patientId, ChatService.PATIENT);
        if (thread.isEmpty()) return null;
        Message last = thread.get(thread.size() - 1);
        if (!ChatService.PATIENT.equals(last.getSenderType())) return null;

        ReviewItemVO item = new ReviewItemVO();
        item.setMessageId(last.getId());
        item.setPatientId(patientId);
        item.setRequiresClinician(false);

        AgentRun run = runs.findByMessageId(last.getId()).orElse(null);
        if (run == null) {
            return humanOnly(item, "NO_RUN");
        }
        item.setRunId(run.getId());
        item.setState(run.getState());
        AgentRunResult result = results.findByRunId(run.getId()).orElse(null);
        if (!AgentRunState.WAITING_REVIEW.name().equals(run.getState())) {
            return humanOnly(item, AgentRunState.FAILED.name().equals(run.getState())
                    ? "RUN_FAILED" : "RUN_PENDING");
        }
        if (result == null) {
            return humanOnly(item, "NO_RESULT");
        }
        item.setIntent(result.getIntent());
        item.setRisk(result.getRisk());
        item.setRequiresClinician(Boolean.TRUE.equals(result.getRequiresClinician()));
        item.setDraft(result.getDraft());
        item.setCitations(readCitations(result.getCitationsJson()));
        item.setValidation(readValidation(result));
        claims.findByRunId(run.getId()).ifPresent(claim -> {
            item.setClaimedBy(claim.getStaffId());
            item.setClaimedByMe(claim.getStaffId().equals(staffId));
        });
        return item;
    }

    private ReviewItemVO humanOnly(ReviewItemVO item, String reason) {
        item.setDraft(null);
        item.setReason(reason);
        claims.findByRunId(item.getRunId() == null ? -1L : item.getRunId()).ifPresent(claim -> {
            item.setClaimedBy(claim.getStaffId());
        });
        return item;
    }

    private String citationsJson(List<AgentRunResultRequest.Citation> citations) {
        // Store the queue's own shape: the request is snake_case, the API response is camelCase,
        // and converting explicitly keeps the two contracts from leaking into each other.
        List<ReviewItemVO.Citation> view = citations.stream().map(citation -> {
            ReviewItemVO.Citation item = new ReviewItemVO.Citation();
            item.setChunkId(citation.getChunkId());
            item.setDocumentId(citation.getDocumentId());
            item.setDocumentVersion(citation.getDocumentVersion());
            item.setTitle(citation.getTitle());
            item.setHeadingPath(new ArrayList<>(citation.getHeadingPath()));
            item.setContentHash(citation.getContentHash());
            return item;
        }).toList();
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a citation list must be serialisable", e);
        }
    }

    private List<ReviewItemVO.Citation> readCitations(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            log.warn("stored citations could not be read", e);
            return new ArrayList<>();
        }
    }

    private ReviewItemVO.Validation readValidation(AgentRunResult result) {
        ReviewItemVO.Validation validation = new ReviewItemVO.Validation();
        validation.setOk(Boolean.TRUE.equals(result.getValidationOk()));
        validation.setKind(result.getValidationKind());
        validation.setReasons(splitLines(result.getValidationReasons()));
        validation.setRejectedReasons(splitLines(result.getRejectedReasons()));
        return validation;
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(value.split("\n")));
    }
}
