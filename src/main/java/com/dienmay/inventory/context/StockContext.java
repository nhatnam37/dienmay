package com.dienmay.inventory.context;


// Context bất biến giúp mọi movement mang đủ thông tin truy vết.
public record StockContext(String refType, Long refId, ActorType actorType,
                           Long actorId, String note) implements InventoryContext { }
