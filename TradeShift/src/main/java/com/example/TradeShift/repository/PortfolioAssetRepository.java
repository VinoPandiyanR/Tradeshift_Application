package com.example.TradeShift.repository;

import com.example.TradeShift.model.PortfolioAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, Long> {
    List<PortfolioAsset> findByUserId(String userId);
    Optional<PortfolioAsset> findByUserIdAndSymbol(String userId, String symbol);
}


