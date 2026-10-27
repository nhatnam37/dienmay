package com.dienmay.inventory.dto;

public record StocktakeDetail(StocktakeSummary summary, String closedBy, java.time.LocalDateTime closedAt, java.util.List<StocktakeLineView> lines, boolean canClose, boolean counted) {  }
