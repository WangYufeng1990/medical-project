package com.example.medical.module.chat.controller;

import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.chat.dto.agent.ApproveReviewRequest;
import com.example.medical.module.chat.dto.agent.RejectReviewRequest;
import com.example.medical.module.chat.dto.agent.ReviewDecisionVO;
import com.example.medical.module.chat.dto.agent.ReviewItemVO;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.chat.service.AgentReviewEventService;
import com.example.medical.module.chat.service.AgentReviewService;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The staff review queue (Round 57.1). A patient never reaches this controller: the role check
 * refuses them, and the service additionally scopes every item to the reviewer's patients.
 */
@RestController
@RequestMapping("/api/v1/agent/reviews")
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
@RequiredArgsConstructor
public class AgentReviewController {

    private final AgentReviewService reviews;
    private final AgentReviewEventService eventLog;
    private final DoctorPatientScope scope;

    @GetMapping
    public Result<List<ReviewItemVO>> queue(@AuthenticationPrincipal LoginUser user) {
        // ADMIN is the identity the scope helper resolves as unscoped; asking it here keeps the
        // two from drifting apart (the token's scopes and the ROLE_* authorities differ).
        boolean admin = scope.resolve() == null;
        return Result.ok(reviews.queue(user.getUserId(), admin));
    }

    /** A refused decision is still an action: it is logged before the error reaches the caller. */
    private Result<ReviewDecisionVO> decide(LoginUser user, Long runId, String action,
                                            java.util.function.Supplier<ReviewDecisionVO> decision) {
        try {
            return Result.ok(decision.get());
        } catch (BusinessException refused) {
            // Fixed classification only: the refusal reason can carry patient text.
            eventLog.recordRefusal(runId, "DENIED", user.getUserId(),
                    action + "_" + refused.getCode());
            throw refused;
        } catch (RuntimeException failure) {
            // A system failure is an attempt too; its message is not stored for the same reason.
            eventLog.recordRefusal(runId, "FAILED", user.getUserId(), action + "_SYSTEM_ERROR");
            throw failure;
        }
    }

    @PostMapping("/{runId}/claim")
    public Result<ReviewItemVO> claim(@AuthenticationPrincipal LoginUser user,
                                      @PathVariable Long runId) {
        return Result.ok(reviews.claim(user.getUserId(), runId));
    }

    @PostMapping("/{runId}/approve")
    public Result<ReviewDecisionVO> approve(@AuthenticationPrincipal LoginUser user,
                                            @PathVariable Long runId,
                                            @Valid @RequestBody ApproveReviewRequest request) {
        return decide(user, runId, "APPROVED",
                () -> reviews.approve(user.getUserId(), runId, request.getFinalText()));
    }

    @PostMapping("/{runId}/reject")
    public Result<ReviewDecisionVO> reject(@AuthenticationPrincipal LoginUser user,
                                           @PathVariable Long runId,
                                           @Valid @RequestBody RejectReviewRequest request) {
        return decide(user, runId, "REJECTED",
                () -> reviews.reject(user.getUserId(), runId, request.getReason()));
    }
}
