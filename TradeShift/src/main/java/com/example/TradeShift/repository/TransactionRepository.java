package com.example.TradeShift.repository;

import com.example.TradeShift.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdOrderByIdDesc(String userId);
}


