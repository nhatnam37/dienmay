package com.dienmay.warranty.dto;
public record ReceiveRequest(Long warrantyId, String serialNo, String issueDescription, String overrideReason) { }
