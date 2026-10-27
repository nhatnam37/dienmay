package com.dienmay.reports;
import java.math.BigDecimal;
import java.time.LocalDate;
public final class ReportDtos {
    private ReportDtos() { }
    public enum Granularity { DAY, MONTH }
    public enum ProductOrder { QUANTITY, REVENUE }
    public record Totals(BigDecimal gross,BigDecimal refunds,BigDecimal net,long invoiceCount,long cancelledCount,long netQuantity) {
        public static Totals zero(){return new Totals(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0,0,0);}
    }
    public record RevenuePoint(LocalDate period,Totals totals) { }
    public record BranchRevenue(Long branchId,String branchName,Totals totals) { }
    public record ChannelRevenue(String channel,Totals totals) { }
    public record TopProduct(Long productId,String sku,String productName,long quantity,BigDecimal revenue,String note) { }
    public record Delta(BigDecimal absolute,BigDecimal percent) { }
    public record Comparison(ReportRange range,ReportRange previousRange,Totals current,Totals previous,
            Delta gross,Delta refunds,Delta net,Delta invoiceCount,Delta cancelledCount,Delta netQuantity) { }
    public record Dashboard(Comparison last30Days,Totals today,long pendingOnlineOrders,long openWarrantyTickets,long lowStockCount) { }
    public record SerialCounts(long inStock,long reserved,long sold,long inTransfer,long inWarrantyRepair,long damaged) { }
    public record InventoryRow(Long branchId,String branchName,Long productId,String sku,String productName,
            String categoryName,boolean tracksSerial,SerialCounts serialCounts,Long quantity,Long reservedQty,long available) { }
    public record LowStock(Long branchId,String branchName,Long productId,String sku,String productName,
            boolean tracksSerial,long available,long threshold,long shortage) { }
}
