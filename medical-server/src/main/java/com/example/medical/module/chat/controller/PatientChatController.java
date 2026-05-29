package com.example.medical.module.chat.controller;

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
@RequestMapping("/api/v1/patient/me/messages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientChatController {

    private final ChatService chatService;

    @GetMapping("/conversations")
    public Result<PageResult<ConversationVO>> conversations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(chatService.getConversations(loginUser.getUserId(), page, size));
    }

    @GetMapping("/{partnerId}")
    public Result<PageResult<MessageVO>> conversation(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long partnerId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size) {
        return Result.ok(chatService.getConversation(loginUser.getUserId(), partnerId, page, size));
    }

    @PostMapping
    public Result<MessageVO> send(@AuthenticationPrincipal LoginUser loginUser,
                                   @Valid @RequestBody MessageFormDTO dto) {
        return Result.ok(chatService.sendMessage(loginUser.getUserId(), dto.getReceiverId(), dto.getContent()));
    }
}
