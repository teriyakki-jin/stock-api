package com.nh.stockapi.domain.account.controller;

import com.nh.stockapi.common.response.ApiResponse;
import com.nh.stockapi.domain.account.dto.AccountResponse;
import com.nh.stockapi.domain.account.dto.DepositRequest;
import com.nh.stockapi.domain.account.service.AccountService;
import com.nh.stockapi.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "계좌", description = "계좌 개설 / 조회 / 입금 / 해지")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "계좌 개설")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountResponse> openAccount(@AuthenticationPrincipal Member member) {
        return ApiResponse.ok("계좌가 개설되었습니다.", accountService.openAccount(member));
    }

    @Operation(summary = "내 계좌 목록 조회")
    @GetMapping
    public ApiResponse<List<AccountResponse>> getMyAccounts(@AuthenticationPrincipal Member member) {
        return ApiResponse.ok(accountService.getMyAccounts(member));
    }

    @Operation(summary = "계좌 단건 조회")
    @GetMapping("/{accountId}")
    public ApiResponse<AccountResponse> getAccount(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member) {
        return ApiResponse.ok(accountService.getAccount(accountId, member));
    }

    @Operation(summary = "입금")
    @PostMapping("/{accountId}/deposit")
    public ApiResponse<AccountResponse> deposit(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody DepositRequest request) {
        return ApiResponse.ok("입금이 완료되었습니다.", accountService.deposit(accountId, member, request));
    }

    @Operation(summary = "계좌 해지")
    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> closeAccount(
            @PathVariable Long accountId,
            @AuthenticationPrincipal Member member) {
        accountService.closeAccount(accountId, member);
        return ApiResponse.ok("계좌가 해지되었습니다.", null);
    }
}
