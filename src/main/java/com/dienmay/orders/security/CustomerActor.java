package com.dienmay.orders.security;

// ID chỉ lấy từ principal khách đã xác thực, không nhận customerId từ form.
public record CustomerActor(Long customerId) { }
