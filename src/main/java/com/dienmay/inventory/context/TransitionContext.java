package com.dienmay.inventory.context;


public record TransitionContext(String refType, Long refId, ActorType actorType,
                                Long actorId, String note, Long toBranchId) implements InventoryContext { }
