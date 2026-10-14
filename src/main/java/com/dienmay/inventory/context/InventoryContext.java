package com.dienmay.inventory.context;


public interface InventoryContext {
    String refType();
    Long refId();
    ActorType actorType();
    Long actorId();
    String note();
}
