package com.example.TradeShift.service;

import com.example.TradeShift.model.Transaction;
import com.example.TradeShift.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingService {

    private final TransactionRepository transactionRepository;
    private final PortfolioService portfolioService;
    private final AccountService accountService;
    private final RealTimeNotificationService notificationService;

    public TradingService(TransactionRepository transactionRepository, PortfolioService portfolioService, 
                         AccountService accountService, RealTimeNotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.portfolioService = portfolioService;
        this.accountService = accountService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Transaction executeBuyOrder(String userId, String symbol, Double quantity, Double price) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (quantity == null || price == null || quantity <= 0 || price <= 0) {
            throw new IllegalArgumentException("Invalid order parameters: quantity and price must be positive");
        }
        

        String normalizedSymbol = symbol.trim().toUpperCase();
        double cost = quantity * price;
        

        accountService.debit(userId, cost);

        portfolioService.addOrUpdate(userId, normalizedSymbol, quantity, price);

        Transaction tx = new Transaction(userId, normalizedSymbol, quantity, price, "BUY");
        Transaction saved = transactionRepository.save(tx);

        double newBalance = accountService.getBalance(userId);
        notificationService.notifyTransaction(userId, "BUY", normalizedSymbol, quantity, price, cost);
        notificationService.notifyBalanceUpdate(userId, newBalance, -cost);
        
        return saved;
    }

    @Transactional
    public Transaction executeSellOrder(String userId, String symbol, Double quantity, Double price) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (quantity == null || price == null || quantity <= 0 || price <= 0) {
            throw new IllegalArgumentException("Invalid order parameters: quantity and price must be positive");
        }
        

        String normalizedSymbol = symbol.trim().toUpperCase();
        

        java.util.Optional<com.example.TradeShift.model.PortfolioAsset> assetOpt = portfolioService.getPortfolioAsset(userId, normalizedSymbol);
        
        if (!assetOpt.isPresent()) {

            java.util.List<com.example.TradeShift.model.PortfolioAsset> allAssets = portfolioService.getPortfolio(userId);
            java.util.List<String> ownedSymbols = allAssets.stream()
                    .map(a -> a.getSymbol() != null ? a.getSymbol() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            
            String errorMessage = "Asset '" + normalizedSymbol + "' not found in your portfolio.";
            if (!ownedSymbols.isEmpty()) {
                errorMessage += " You currently own: " + String.join(", ", ownedSymbols);
            } else {
                errorMessage += " Your portfolio is empty.";
            }
            throw new IllegalArgumentException(errorMessage);
        }
        
        com.example.TradeShift.model.PortfolioAsset asset = assetOpt.get();
        double currentQuantity = asset.getQuantity() != null ? asset.getQuantity() : 0.0;
        
        if (currentQuantity <= 0) {
            throw new IllegalArgumentException("You have 0 shares of '" + normalizedSymbol + "' to sell.");
        }
        
        if (currentQuantity < quantity) {
            throw new IllegalArgumentException(String.format("Insufficient quantity to sell. You have %.2f shares of '%s', but trying to sell %.2f shares.", 
                currentQuantity, normalizedSymbol, quantity));
        }
        
        double proceeds = quantity * price;
        

        accountService.credit(userId, proceeds);

        portfolioService.addOrUpdate(userId, normalizedSymbol, -quantity, price);

        Transaction tx = new Transaction(userId, normalizedSymbol, quantity, price, "SELL");
        Transaction saved = transactionRepository.save(tx);

        double newBalance = accountService.getBalance(userId);
        notificationService.notifyTransaction(userId, "SELL", normalizedSymbol, quantity, price, proceeds);
        notificationService.notifyBalanceUpdate(userId, newBalance, proceeds);
        
        return saved;
    }
}


