# Đo audit thật trên MySQL Testcontainers

Dữ liệu SQL tổng hợp chỉ cho hiệu năng test, không dùng bộ nạp demo: 100.001 Serial, 120.001 history, 20.000 invoice/item/payment/warranty. Một lượt, không HTTP; EXPLAIN lưu cạnh báo cáo.

| INV | ms | Vi phạm |
|---|---:|---:|
| INV-01 | 40.461584 | 0 |
| INV-02 | 31.464625 | 0 |
| INV-03 | 147.669375 | 0 |
| INV-04 | 27.145541 | 0 |
| INV-05 | 33.203041 | 0 |
| INV-06 | 36.454084 | 0 |
| INV-07 | 1492.174125 | 0 |
| INV-08 | 34.737833 | 0 |
| INV-09 | 6.146417 | 0 |
| INV-10 | 56.687375 | 0 |
| INV-11 | 33.9295 | 0 |
| INV-12 | 39.404666 | 0 |
