package com.example.TradeShift.service;

import com.example.TradeShift.model.PortfolioAsset;
import com.example.TradeShift.repository.PortfolioAssetRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {

    private final PortfolioAssetRepository repo;
    private final MarketDataService marketDataService;

    public PortfolioService(PortfolioAssetRepository repo, MarketDataService marketDataService) {
        this.repo = repo;
        this.marketDataService = marketDataService;
    }

    public List<Map<String,Object>> getPortfolioWithPrices(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        List<PortfolioAsset> assets = repo.findByUserId(userId);
        List<Map<String,Object>> out = new ArrayList<>();
        for (PortfolioAsset a : assets) {
            if (a == null || a.getSymbol() == null || a.getSymbol().isEmpty()) {
                continue;
            }
            try {
                double marketPrice = marketDataService.getCurrentPrice(a.getSymbol());
                

                double displayPrice = (marketPrice > 0) ? marketPrice : 
                                     (a.getAvgBuyPrice() != null && a.getAvgBuyPrice() > 0 ? a.getAvgBuyPrice() : 0.0);
                
                Map<String,Object> row = new HashMap<>();
                row.put("id", a.getId());
                row.put("symbol", a.getSymbol());
                row.put("quantity", a.getQuantity() != null ? a.getQuantity() : 0.0);
                row.put("avgBuyPrice", a.getAvgBuyPrice() != null ? a.getAvgBuyPrice() : 0.0);
                row.put("currentPrice", displayPrice);
                row.put("marketPrice", marketPrice > 0 ? marketPrice : displayPrice);
                out.add(row);
            } catch (Exception e) {

                double fallbackPrice = a.getAvgBuyPrice() != null && a.getAvgBuyPrice() > 0 ? a.getAvgBuyPrice() : 0.0;
                Map<String,Object> row = new HashMap<>();
                row.put("id", a.getId());
                row.put("symbol", a.getSymbol());
                row.put("quantity", a.getQuantity() != null ? a.getQuantity() : 0.0);
                row.put("avgBuyPrice", a.getAvgBuyPrice() != null ? a.getAvgBuyPrice() : 0.0);
                row.put("currentPrice", fallbackPrice);
                row.put("marketPrice", fallbackPrice);
                out.add(row);
            }
        }
        return out;
    }

    public PortfolioAsset addOrUpdate(String userId, String symbol, double quantity, double avgBuyPrice) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        if (avgBuyPrice < 0) {
            throw new IllegalArgumentException("Average buy price cannot be negative");
        }
        
        String normalizedSymbol = symbol.trim().toUpperCase();
        PortfolioAsset result = repo.findByUserIdAndSymbol(userId, normalizedSymbol)
                .map(existing -> {
                    double prevQty = existing.getQuantity() == null ? 0.0 : existing.getQuantity();
                    double prevAvg = existing.getAvgBuyPrice() == null ? 0.0 : existing.getAvgBuyPrice();
                    double newQty = prevQty + quantity;
                    

                    double newAvg;
                    if (newQty <= 0) {

                        repo.delete(existing);
                        return null;
                    } else if (quantity > 0) {

                        newAvg = ((prevAvg * prevQty) + (avgBuyPrice * quantity)) / newQty;
                    } else {

                        newAvg = prevAvg;
                    }
                    
                    existing.setQuantity(newQty);
                    existing.setAvgBuyPrice(newAvg);
                    return repo.save(existing);
                })
                .orElseGet(() -> {
                    if (quantity > 0) {
                        return repo.save(new PortfolioAsset(userId, normalizedSymbol, quantity, avgBuyPrice));
                    } else {
                        throw new IllegalArgumentException("Cannot add asset with zero or negative quantity");
                    }
                });
        

        if (result == null && quantity > 0) {
            return repo.save(new PortfolioAsset(userId, normalizedSymbol, quantity, avgBuyPrice));
        }
        
        return result;
    }

    public void remove(String userId, Long id) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid asset ID");
        }
        PortfolioAsset asset = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        
        if (!userId.equals(asset.getUserId())) {
            throw new IllegalArgumentException("Asset does not belong to user");
        }
        
        repo.deleteById(id);
    }

    public java.util.Optional<PortfolioAsset> getPortfolioAsset(String userId, String symbol) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        return repo.findByUserIdAndSymbol(userId, normalizedSymbol);
    }

    public List<PortfolioAsset> getPortfolio(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        return repo.findByUserId(userId);
    }
}


