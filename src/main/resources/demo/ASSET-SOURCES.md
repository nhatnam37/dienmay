# Nguồn ảnh và dữ liệu catalog demo

80 ảnh PNG trong images/ là hình minh họa nguyên gốc do GenerateDemoImages.java tự vẽ bằng Java2D: hình học đơn giản, không lấy ảnh/biểu trưng của hãng hoặc nguồn Internet. Không dùng ảnh sản phẩm có bản quyền. Mỗi ảnh có nhãn DEMO và SKU; không khẳng định giống ngoại hình sản phẩm thật. Có thể tái tạo bằng JDK 21: `java -Djava.awt.headless=true src/main/resources/demo/GenerateDemoImages.java src/main/resources/demo`.

products.csv gồm tên thương hiệu/model/dòng máy quen thuộc để trình diễn. Giá nguyên đồng, dung lượng mô tả và số tháng bảo hành là dữ liệu cấu hình demo, không phải báo giá hoặc cam kết/thông số chính thức của hãng. Không dùng làm tư vấn mua sắm. Không có dữ liệu cá nhân trong catalog.
