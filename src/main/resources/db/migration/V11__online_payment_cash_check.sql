-- V9 đã cho method ONLINE; bổ sung CHECK tiền mặt để khoản thu online không có
-- cash_received/change_given như CARD/TRANSFER, không nới điều kiện của CASH.
ALTER TABLE payment DROP CHECK ck_payment_cash;
ALTER TABLE payment ADD CONSTRAINT ck_payment_cash CHECK (
    (CAST(method AS BINARY)='CASH' AND cash_received IS NOT NULL AND change_given IS NOT NULL
        AND cash_received>=amount AND change_given=cash_received-amount)
    OR (CAST(method AS BINARY) IN ('CARD','TRANSFER','ONLINE') AND cash_received IS NULL AND change_given IS NULL)
);
