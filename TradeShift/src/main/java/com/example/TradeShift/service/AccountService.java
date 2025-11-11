package com.example.TradeShift.service;

import com.example.TradeShift.model.Account;
import com.example.TradeShift.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final RealTimeNotificationService notificationService;

    public AccountService(AccountRepository accountRepository, RealTimeNotificationService notificationService) {
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Account getOrCreate(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(new Account(userId, 0.0)));
    }

    @Transactional
    public double getBalance(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        Account account = getOrCreate(userId);
        return account.getCashBalance() != null ? account.getCashBalance() : 0.0;
    }

    @Transactional
    public void credit(String userId, double amount) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Credit amount cannot be negative");
        }
        Account a = getOrCreate(userId);
        double currentBalance = a.getCashBalance() != null ? a.getCashBalance() : 0.0;
        a.setCashBalance(currentBalance + amount);
        accountRepository.save(a);

        notificationService.notifyBalanceUpdate(userId, a.getCashBalance(), amount);
    }

    @Transactional
    public void debit(String userId, double amount) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Debit amount cannot be negative");
        }
        Account a = getOrCreate(userId);
        double bal = a.getCashBalance() != null ? a.getCashBalance() : 0.0;
        if (amount > bal) {
            throw new IllegalArgumentException("Insufficient funds. Current balance: " + bal + ", Required: " + amount);
        }
        a.setCashBalance(bal - amount);
        accountRepository.save(a);

        notificationService.notifyBalanceUpdate(userId, a.getCashBalance(), -amount);
    }
}


