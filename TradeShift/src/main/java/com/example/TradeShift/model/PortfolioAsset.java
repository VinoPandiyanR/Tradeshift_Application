package com.example.TradeShift.model;

import jakarta.persistence.*;

@Entity
@Table(name = "portfolio_assets")
public class PortfolioAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String symbol;
    private Double quantity;
    private Double avgBuyPrice;

    public PortfolioAsset() {}

    public PortfolioAsset(String userId, String symbol, Double quantity, Double avgBuyPrice) {
        this.userId = userId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getAvgBuyPrice() { return avgBuyPrice; }
    public void setAvgBuyPrice(Double avgBuyPrice) { this.avgBuyPrice = avgBuyPrice; }
}


