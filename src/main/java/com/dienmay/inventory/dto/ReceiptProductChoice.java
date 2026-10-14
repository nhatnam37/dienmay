package com.dienmay.inventory.dto;


public record ReceiptProductChoice(Long id, String sku, String name, boolean tracksSerial, com.dienmay.catalog.entity.SerialType serialType) { }
