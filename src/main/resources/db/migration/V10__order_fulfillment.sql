-- Nullable cho hóa đơn POS cũ; UNIQUE nhiều NULL nhưng tối đa một hóa đơn cho mỗi đơn online.
-- FK giữ liên kết chứng từ. Không cộng order_payment vào doanh thu cùng invoice/payment:
-- order_payment là khoản tạm thu, invoice/payment là chứng từ bán sau khi đã giao/nhận.
ALTER TABLE invoice
    ADD COLUMN order_id BIGINT NULL,
    ADD CONSTRAINT uk_invoice_order UNIQUE(order_id),
    ADD CONSTRAINT fk_invoice_order FOREIGN KEY(order_id) REFERENCES orders(id);
