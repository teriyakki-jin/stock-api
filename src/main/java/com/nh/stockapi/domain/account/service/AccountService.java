package com.nh.stockapi.domain.account.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.dto.AccountResponse;
import com.nh.stockapi.domain.account.dto.DepositRequest;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.repository.AccountRepository;
import com.nh.stockapi.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponse openAccount(Member member) {
        if (accountRepository.existsByMember(member)) {
            throw new CustomException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        Account account = Account.open(member);
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(Member member) {
        return accountRepository.findByMember(member).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId, Member member) {
        Account account = findAccountOfMember(accountId, member);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse deposit(Long accountId, Member member, DepositRequest request) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
        validateOwner(account, member);
        account.deposit(request.amount());
        return AccountResponse.from(account);
    }

    @Transactional
    public void closeAccount(Long accountId, Member member) {
        Account account = findAccountOfMember(accountId, member);
        account.close();
    }

    // 주문 서비스에서 사용하는 락 버전
    public Account findWithLock(Long accountId) {
        return accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Account findAccountOfMember(Long accountId, Member member) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
        validateOwner(account, member);
        return account;
    }

    private void validateOwner(Account account, Member member) {
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
