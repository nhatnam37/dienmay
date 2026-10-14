package com.dienmay.inventory.dto;


public record ReceiptSerialView(Long productId, String serialNo, com.dienmay.inventory.serial.SerialStatus status) { }
