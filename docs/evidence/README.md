# Bằng chứng kiểm thử

`automated/<UTC-run-id>/`: manifest, môi trường, trạng thái working tree, dấu vân tay source, XML Surefire/Failsafe, JaCoCo và log tổng hợp. `latest-run.properties` chỉ đến lượt mới nhất. Log đầy đủ ở đường dẫn rawLog trong manifest, có thể là thư mục tạm; XML/coverage đã lưu bền trong docs. `evidence.sha256` dùng kiểm tra các tệp đã lưu.

`manual/<ngày>-<người-chạy>/`: người chạy tự tạo khi thực hiện checklist. Hiện chưa có ảnh thủ công. Không dùng ảnh giả. Báo cáo XML chứa đường dẫn máy và fixture test; rà soát trước khi công khai repository.
