package com.example.medical.module.chat.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.chat.dto.ConversationVO;
import com.example.medical.module.chat.dto.MessageFormDTO;
import com.example.medical.module.chat.dto.MessageVO;
import com.example.medical.module.chat.service.ChatService;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    public Result<PageResult<ConversationVO>> conversations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(chatService.getConversations(loginUser.getUserId(), ChatService.STAFF, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(chatService.unreadCount(loginUser.getUserId(), ChatService.STAFF));
    }

    @GetMapping("/{partnerId}")
    public Result<PageResult<MessageVO>> conversation(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long partnerId,
            @RequestParam String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        validateType(partnerType);
        return Result.ok(chatService.getConversation(loginUser.getUserId(), ChatService.STAFF,
                partnerId, partnerType, page, size));
    }

    @PostMapping
    public Result<MessageVO> send(@AuthenticationPrincipal LoginUser loginUser,
                                   @Valid @RequestBody MessageFormDTO dto) {
        if (dto.getReceiverType() == null || dto.getReceiverType().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "receiverType is required (STAFF or PATIENT)");
        }
        validateType(dto.getReceiverType());
        return Result.ok(chatService.sendMessage(loginUser.getUserId(), ChatService.STAFF,
                dto.getReceiverId(), dto.getReceiverType(), dto.getContent()));
    }

    private void validateType(String type) {
        if (!ChatService.STAFF.equals(type) && !ChatService.PATIENT.equals(type)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid receiverType: " + type);
        }
    }
}
