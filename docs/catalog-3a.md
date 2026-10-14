# Module catalog — bước 3A

## Phương án và luồng

Chưa thêm controller, route hoặc giao diện catalog. Các module organization,
auth và SecurityConfig giữ nguyên; migration V1 giữ nguyên checksum.

Luồng ở bước tiếp theo:

```text
Controller nhận Form -> Service chuẩn hóa, kiểm tra và mở transaction
                    -> Repository truy vấn Entity trong transaction
                    -> Mapper tạo View DTO -> Controller/Thymeleaf
```

- Entity ánh xạ DB. Form nhận dữ liệu đầu vào với Bean Validation. View chỉ
  chứa giá trị và ID, không chứa entity hoặc lazy proxy.
- Mapper viết tay chỉ sao chép dữ liệu, không truy vấn DB hoặc quyết định nghiệp vụ.
- Service chuẩn hóa SKU bằng strip + `toUpperCase(Locale.ROOT)` trước validation,
  kiểm tra trùng và lưu. Các Form mutable được service chuẩn hóa tại chỗ.
- Tên category/brand được strip, giữ nguyên dấu và cách viết. `existsByName`
  và `existsByNameAndIdNot` so sánh trực tiếp trên cột `utf8mb4_0900_as_ci`.
  MySQL dùng cùng collation đó cho unique index: phân biệt dấu, không phân biệt
  hoa thường. Không dùng `equalsIgnoreCase`, lower/unaccent trong Java để tránh
  hai bên xử lý Unicode khác nhau.
- `ProductRepository.search` dùng `@EntityGraph(category, brand)` trên quan hệ
  to-one: lấy tên liên quan bằng JOIN, không tạo thêm SELECT cho từng sản phẩm,
  vẫn limit/offset tại DB. Count query tách riêng. Keyword khớp name hoặc sku,
  không phân biệt hoa thường; dấu `%`, `_`, `!` được escape để tìm theo ký tự thật.
- `CategoryRepository.findAll` fetch parent để map tên cha không bị N+1.
- Category update đi từ cha dự kiến lên gốc để chặn chọn chính mình/con/cháu
  làm cha. Khóa cây theo thứ tự ID khi update để hai request A -> B và B -> A
  không cùng vượt kiểm tra. Cách khóa toàn cây phù hợp quy mô danh mục nhỏ;
  nếu cây lớn cần thiết kế lại chiến lược khóa.
- Category/Brand chỉ xóa khi không bị tham chiếu. FK và unique constraint vẫn
  bảo vệ DB khi có request đồng thời. `saveAndFlush`/`flush` giúp dịch lỗi ngay
  trong service thành BusinessException tiếng Việt.
- Product không có API xóa cứng trong service. discontinue/reactivate đổi status,
  giữ dòng sản phẩm; update thông thường không đổi status.
- `assertTracksSerialChangeAllowed` hiện cho phép đổi, có TODO chặn khi đã có
  serial/tồn kho ở Bước 4. Đây chưa phải kiểm tra tồn kho đã hoàn thiện.
- `@Transactional(readOnly = true)` mặc định cho đọc; thao tác ghi dùng
  `@Transactional`. Mapper chạy bên trong transaction vì open-in-view đã tắt.
- createdAt/updatedAt dùng lifecycle callback JPA để View sau save có thời gian;
  default/tự cập nhật trong MySQL cũng hỗ trợ các lệnh SQL trực tiếp.

## Vị trí file

- `src/main/resources/db/migration/V2__catalog.sql`: schema, index, dữ liệu nền.
- `src/main/java/com/dienmay/catalog/entity`: Category, Brand, Product, ProductStatus.
- `src/main/java/com/dienmay/catalog/repository`: ba repository.
- `src/main/java/com/dienmay/catalog/dto`: ba Form, ba View.
- `src/main/java/com/dienmay/catalog/mapper`: ba mapper viết tay.
- `src/main/java/com/dienmay/catalog/service`: ba service và CatalogFormValidator.
- `src/main/java/com/dienmay/common/exception`: BusinessException, ResourceNotFoundException.
- `src/main/java/com/dienmay/common/web/GlobalExceptionHandler.java`.
- `src/test/java/com/dienmay/catalog`: test catalog.

## Xử lý lỗi ở bước tiếp theo

ResourceNotFoundException được GlobalExceptionHandler đưa về view `error/404`
với HTTP 404. Chưa thay template cũ; template vẫn hiện thông báo 404 chung.
BusinessException không được advice bắt toàn cục vì chưa biết form nào cần
hiển thị lại. Controller ở bước tiếp theo cần catch BusinessException,
`bindingResult.reject("business", exception.getMessage())`, nạp lại dữ liệu
cho các select rồi trả về đúng template form. Không thêm catch-all nuốt lỗi.

## Chạy và kiểm tra

Trong thư mục gốc `dienmay`, dùng JDK 21:

```sh
docker compose up -d
./mvnw test
./mvnw spring-boot:run
```

MySQL ở localhost:3307; ứng dụng ở http://localhost:8081. Nếu ứng dụng đang
chạy, không khởi động thêm bản thứ hai trên cùng cổng.

Chạy riêng unit test catalog, không cần MySQL:

```sh
./mvnw -Dtest=CategoryServiceTest,BrandServiceTest,ProductServiceTest test
```

Chạy riêng test tích hợp, cần MySQL:

```sh
./mvnw -Dtest=CatalogPersistenceTest test
```

Xem kết quả tại `target/surefire-reports/*.txt` hoặc Run Test trong VS Code.

Kiểm tra Flyway bằng MySQL Workbench/Database Client trên cổng 3307:

```sql
USE dienmay;
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM category; -- 6
SELECT COUNT(*) FROM brand;    -- 5
SHOW FULL COLUMNS FROM category;
SHOW FULL COLUMNS FROM brand;
SHOW INDEX FROM product;
```

V2 phải có `version=2`, `description=catalog`, `success=1`. Khi áp dụng lần
đầu log có `Migrating ... 2 - catalog` và `Successfully applied ... v2`;
lần sau chỉ báo schema up to date, không chạy lại INSERT.

## Kết quả đã kiểm chứng

- `./mvnw test`: 47 test, 0 failures, 0 errors, 0 skipped.
- 35 unit test catalog; 4 test tích hợp catalog; 8 test cũ giữ nguyên.
- MySQL: V1/V2 success=1; 6 category, 5 brand, 0 product sau rollback test.
- Cả hai cột name dùng `utf8mb4_0900_as_ci`; unique index trên name tồn tại.
- Bảng tạm LIKE category/brand giữ collation và unique index thật:
  INSERT Tivi thành công; INSERT TIVI bị từ chối; INSERT Tívi thành công.
  Bảng tạm được DROP trong finally; dữ liệu seed không bị đổi.
- Search có 2 kết quả ACTIVE, page size 1: đúng 2 SQL (content + count),
  map tên category/brand không phát sinh SELECT phụ. Thử cả keyword name và sku.
- Khởi động lại thành công, Flyway current version=2, cổng 8081;
  GET / trả 302 sang /login, GET /login trả 200.
- Migration V1 giữ nguyên SHA-256; không sửa mã module cũ hoặc thêm Python.
- V2 lần đầu có warning MySQL về display width của TINYINT(1), không phải lỗi;
  giữ TINYINT(1) theo yêu cầu. Startup hoàn chỉnh không có ERROR.

## Ba lỗi thường gặp

1. **Flyway checksum mismatch:** sửa migration đã áp dụng. Không sửa V1/V2
   trên DB đã dùng; thay đổi schema tiếp theo phải tạo V3. Không tự repair
   hoặc xóa lịch sử để che lỗi.
2. **MySQL chưa sẵn sàng / sai cổng:** ứng dụng chạy trên máy dùng cổng 3307,
   còn MySQL bên trong container là 3306. Kiểm tra `docker compose ps` và log
   `ready for connections` trước khi chạy toàn bộ test hoặc ứng dụng.
3. **LazyInitializationException / N+1:** đọc quan hệ sau khi đóng transaction,
   hoặc map nhiều sản phẩm nhưng không fetch category/brand. Giữ map DTO trong
   service, dùng EntityGraph cho search và không trả entity sang view.

## Danh sách test mới

### `src/test/java/com/dienmay/catalog/CatalogPersistenceTest.java`

- `categoryName_caseInsensitiveAccentSensitive_enforcesUniqueIndex`
- `brandName_caseInsensitiveAccentSensitive_enforcesUniqueIndex`
- `search_filtersAndPagination_fetchesNamesWithoutNPlusOne`
- `updateCategory_descendantAsParent_rejectsCycleOnRealTree`

### `src/test/java/com/dienmay/catalog/service/BrandServiceTest.java`

- `delete_brandHasProducts_rejectsRequest`
- `create_duplicateName_rejectsRequest`
- `create_validName_trimsAndReturnsView`
- `update_sameNameOnSameBrand_allowsUpdate`
- `delete_emptyBrand_deletesSuccessfully`
- `list_brands_returnsViews`

### `src/test/java/com/dienmay/catalog/service/CategoryServiceTest.java`

- `create_duplicateName_rejectsRequest`
- `update_selfAsParent_rejectsRequest`
- `update_descendantAsParent_rejectsRequest`
- `delete_categoryHasProducts_rejectsRequest`
- `delete_categoryHasChildren_rejectsRequest`
- `delete_emptyCategory_deletesSuccessfully`
- `create_validParent_returnsViewWithParentName`
- `update_sameNameOnSameCategory_allowsUpdate`
- `create_blankName_rejectsRequest`
- `delete_missingCategory_throwsNotFound`
- `list_categories_mapsParentWithoutExposingEntity`

### `src/test/java/com/dienmay/catalog/service/ProductServiceTest.java`

- `create_duplicateNormalizedSku_rejectsRequest`
- `create_missingCategory_throwsNotFound`
- `create_missingBrand_throwsNotFound`
- `create_lowercasePaddedSku_savesUppercaseAndReturnsNames`
- `update_serialFlagChanged_checksGuardAndPreservesStatus`
- `discontinue_existingProduct_onlyChangesStatusWithoutDelete`
- `reactivate_discontinuedProduct_changesToActive`
- `getById_missingProduct_throwsNotFound`
- `search_optionalFilters_mapsPageAndEscapesLikeCharacters`
- `search_blankKeyword_passesNullFilter`
- `create_invalidSku_rejectsRequest`
- `create_negativePrice_rejectsRequest`
- `create_warrantyOverSixty_rejectsRequest`

`create_invalidSku_rejectsRequest` chạy với 6 bộ dữ liệu sai.
