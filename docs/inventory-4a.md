# Inventory 4A — nền tảng kho

## Phương án và luồng

Chưa thêm controller/giao diện inventory. Chỉ cập nhật form sản phẩm hiện có
để chọn SerialType khi tracksSerial bật. Không dùng Lombok; không thêm Python.
V1/V2 giữ nguyên SHA-256, mọi thay đổi schema nằm trong V3.

- Hàng theo Serial: mỗi thiết bị là một SerialItem; đổi trạng thái qua
  SerialStateService, ghi SerialHistory trong cùng transaction.
- Hàng không theo Serial: StockQuantity giữ tổng tồn và lượng giữ; StockService
  thay đổi dưới khóa PESSIMISTIC_WRITE và ghi StockMovement cùng transaction.
- Tổng tồn = quantity; tồn khả dụng = quantity - reservedQty. decrease chỉ trừ
  phần khả dụng. Chưa có nghiệp vụ giữ/nhả hàng, scheduler hoặc controller kho.
- GoodsReceipt/GoodsReceiptItem/Supplier hiện là entity + repository nền tảng;
  chưa phải module lập phiếu nhập hoàn chỉnh.

## Package và trách nhiệm

- `catalog.entity.SerialType`: IMEI, GENERIC. Catalog không import inventory.
- `catalog.service.ProductInventoryGuard`: cổng kiểm tra dữ liệu kho.
- `inventory.service.InventoryProductGuard`: triển khai bằng ba repository;
  true khi có serial_item bất kỳ trạng thái, stock_quantity.quantity > 0,
  hoặc goods_receipt_item. Dòng tồn 0 đơn thuần không khóa đổi cấu hình.
- `inventory.serial`: SerialItem, SerialHistory, SerialStatus, SerialNumberRules,
  InvalidSerialTransitionException và SerialStateService. Đặt service cùng
  aggregate để gọi được setStatus package-private; code ngoài package không
  thể tự đổi trạng thái Serial bằng setter.
- `inventory.entity`: Supplier, GoodsReceipt, GoodsReceiptItem, StockQuantity,
  StockMovement. Quan hệ đều LAZY, không cascade xóa lịch sử.
- `inventory.repository`: repository cho bảy entity; có truy vấn IN cả lô,
  khóa theo danh sách serial có thứ tự, count theo branch/product/status.
- `inventory.context`: các record bất biến chứa tham chiếu, actor và ghi chú.
  Record là cú pháp Java 21, không phải Lombok; accessor có dạng actorType().
- `inventory.service`: StockService, StockRowInitializer, InventoryContextValidator,
  InventoryProductGuard.

## Các bất biến quan trọng

### Serial và lịch sử

SerialStatus chứa duy nhất bảng chuyển trạng thái. Chuyển về chính nó bị từ chối.
DAMAGED là trạng thái cuối. transition nạp lại SerialItem có khóa theo ID, không
sử dụng trạng thái cũ từ entity detached; caller dùng entity được trả về.

register nhận productId, branchId, receiptId, serialNo, costPrice, TransitionContext.
Nó kiểm tra sản phẩm có tracksSerial, loại serial hợp lệ, giá vốn không âm,
phiếu nhập tồn tại và cùng chi nhánh. Serial được chuẩn hóa trước kiểm tra trùng
và lưu; bản ghi đầu IN_STOCK có history.fromStatus = null.

Chỉ IN_TRANSFER -> IN_STOCK được đổi chi nhánh thực sự. History giữ cả chi nhánh
trước/sau, refType/refId, actor và note. Khi rời RESERVED, reservedUntil được xóa;
đặt thời hạn giữ và job tự nhả sẽ triển khai ở bước sau.

serial_no unique toàn hệ thống vì cùng thiết bị không được đăng ký lại dưới
sản phẩm/chi nhánh khác. @Version bổ sung phát hiện cập nhật trên dữ liệu cũ;
khóa bi quan vẫn cần để kiểm tra nghiệp vụ trên trạng thái hiện tại.

Version entity mới để Long null; Hibernate gán 0 khi persist. Không gán sẵn 0
trong Java vì Spring Data dùng version để xác định entity mới/cũ. Database vẫn
có DEFAULT 0 cho INSERT SQL trực tiếp.

### Actor

Context gồm refType/refId, actorType/actorId, note; TransitionContext thêm
toBranchId tùy chọn. refId phải dương và refType không rỗng, tối đa 30 ký tự.

| actorType | actorId | Kiểm tra tồn tại |
| --- | --- | --- |
| EMPLOYEE | Bắt buộc, dương | EmployeeRepository.existsById |
| CUSTOMER | Bắt buộc, dương | TODO khi có bảng customer |
| SYSTEM | Phải null | Không truy vấn employee |

DB CHECK bảo vệ hình dạng actor ở cả serial_history và stock_movement.
actor_id không có FK vì trỏ tới nhiều bảng theo actor_type. Riêng
GoodsReceipt.created_by vẫn là FK employee theo thiết kế phiếu nhập ban đầu.
Context phải do lớp nghiệp vụ/tầng xác thực tin cậy tạo; không coi actorId do
request tự gửi lên là bằng chứng người dùng đã được xác thực.

### Tồn kho và cạnh tranh

StockService.increase/decrease nhận branchId, productId, amount > 0 và StockContext.
Hàng có Serial bị từ chối. getQuantity/getAvailableQuantity đọc không thay đổi DB.

Khi chưa có dòng tồn, StockRowInitializer tạo dòng 0 trong transaction REQUIRES_NEW.
Unique(branch_id,product_id) quyết định luồng thắng; nếu trùng, transaction tạo
bị rollback rồi thử lại bằng transaction mới (tối đa 3 lần). Không bắt lỗi unique
và tiếp tục trong cùng transaction đã rollback-only.

Chỉ việc tạo dòng 0 tách transaction. Thay đổi quantity + ghi movement vẫn tham
gia transaction nghiệp vụ ngoài, để bước lập phiếu nhập có thể rollback cùng nhau.
Một dòng tồn 0 có thể còn sau nghiệp vụ thất bại; không phát sinh movement và
không được guard coi là dữ liệu tồn dương. Product/branch cần tồn tại đã commit
trước khi gọi nghiệp vụ kho; không tạo mới catalog và nhập kho trong cùng transaction.

Trước khi khởi tạo chỉ đọc scalar tracksSerial, không cache Product cũ. Sau
khởi tạo, khóa product rồi khóa stock và kiểm tra lại loại theo dõi. Catalog
update cũng khóa product trước guard, giúp ngăn đổi chế độ đồng thời lúc nhập kho.
Kiểm tra số lượng bằng long để không tràn INT khi tăng tồn.

### Loại Serial trong catalog

V3 thêm serial_type; backfill GENERIC cho sản phẩm cũ tracks_serial=1 rồi mới
thêm CHECK. tracksSerial=true phải có IMEI/GENERIC; false phải có serialType=null.
Service/Bean Validation và CHECK cùng bảo vệ quy tắc này.

ProductService.update gọi guard nếu tracksSerial HOẶC serialType thay đổi.
Đã có dữ liệu kho thì chặn cả hai; vẫn cho đổi tên/giá khi cấu hình không đổi.
Form chỉ hiện loại Serial khi bật theo dõi; khi tắt, select bị disabled và xóa
lựa chọn. Server vẫn kiểm tra độc lập, không tin JavaScript.

## IMEI/Luhn và GENERIC

- normalize: strip khoảng trắng ngoài, chuyển chữ hoa theo Locale.ROOT;
  từ chối khoảng trắng bên trong (kể cả tab và khoảng trắng Unicode).
- IMEI: đúng 15 chữ số ASCII, kiểm tra Luhn từ phải sang trái.
- GENERIC: 5–30 ký tự thuộc [A-Z0-9-].
- validate trả Result(valid, normalized, reason), reason tiếng Việt khi lỗi.
- Giá trị IMEI test: 490154203237518 và 356938035643809, đều kiểm tra trực tiếp
  bằng isValidLuhn rồi mới assert validate thành công. 490154203237519 sai Luhn.

## Hạ tầng test tích hợp dùng lại

`src/test/java/com/dienmay/support/MySqlIntegrationTestBase.java` khởi động
MySQL 8 Testcontainers từ database trống, cổng ngẫu nhiên; DynamicPropertySource
cấp URL/tài khoản cho Spring và Flyway chạy toàn bộ migration. Container singleton
trong JVM, Ryuk dọn sau khi test kết thúc. Không bật reuse, không skip nếu thiếu Docker.
Profile test tránh tạo tài khoản mẫu dev.

CatalogPersistenceTest và DienmayApplicationTests đã chuyển sang lớp cơ sở này.
Lý do: test catalog cũ phụ thuộc danh mục seed trong DB dev mà người dùng có thể
đã sửa/xóa. Test mới độc lập dữ liệu dev và không sửa DB đang dùng trên cổng 3307.
Unit test JUnit/Mockito vẫn không cần DB hoặc Docker.

## Chạy và kiểm tra

Dùng JDK 21; Docker Desktop phải đang hoạt động:

```sh
./mvnw test
```

Chạy riêng unit test inventory:

```sh
./mvnw -Dtest=SerialStatusTest,SerialNumberRulesTest,SerialStateServiceTest,StockServiceTest,StockRowInitializerTest,InventoryContextValidatorTest,InventoryProductGuardTest test
```

Chạy riêng Testcontainers:

```sh
./mvnw -Dtest=InventoryPersistenceTest test
```

Chạy ứng dụng dev:

```sh
docker compose up -d
./mvnw spring-boot:run
```

MySQL dev ở localhost:3307, ứng dụng localhost:8081. Không chạy hai bản ứng dụng
trên cùng cổng. Đăng nhập admin, sửa/thêm sản phẩm để thử ô chọn Loại Serial.

Kiểm tra bằng SQL trên DB dev:

```sql
USE dienmay;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, sku, tracks_serial, serial_type FROM product;
SHOW CREATE TABLE product;
SHOW CREATE TABLE serial_item;
SHOW CREATE TABLE stock_quantity;
SHOW CREATE TABLE serial_history;
SHOW CREATE TABLE stock_movement;
```

V3 phải có version=3, description='inventory core', success=1.
Không sửa V1/V2/V3 sau khi đã áp dụng; thay đổi tiếp theo dùng V4.

## Kiểm tra chưa thực hiện / phạm vi còn lại

- Chưa kiểm tra bằng mắt hành vi ẩn/hiện select Loại Serial trên trình duyệt;
  template/binding và các quy tắc phía server được test tự động.
- Chưa kiểm tra tải lớn/nhiều JVM hoặc mọi kịch bản lỗi mạng/deadlock;
  đã thử hai luồng cùng khởi tạo/tăng tồn và retry unique có kiểm soát.
- CUSTOMER chưa kiểm tra bảng customer vì chưa có module đó, có TODO rõ ràng.
- Chưa làm controller/UI inventory, lập phiếu nhập, giữ/nhả tồn, job hết hạn,
  bán hàng/chuyển kho/bảo hành hoàn chỉnh. Đây là nền tảng cho các bước sau.

## File cũ cần sửa và lý do

- Product, ProductForm, ProductView, ProductMapper: thêm SerialType và mapping/validation.
- ProductService: guard thay TODO, chặn đổi cả tracksSerial/serialType, khóa product khi update.
- ProductRepository: query scalar tracksSerial để kiểm tra trước khởi tạo tồn mà không cache entity cũ.
- ProductController, ProductEditForm, product form/detail, messages: truyền loại Serial,
  hiển thị/chọn và thông báo lỗi tiếng Việt; không thêm inventory controller.
- pom.xml: thêm testcontainers junit-jupiter/mysql, phiên bản do Spring Boot BOM quản lý.
- CatalogTestData, ProductServiceTest, CatalogWebTest: bổ sung kiểu Serial và regression guard.
- CatalogPersistenceTest, DienmayApplicationTests: dùng MySqlIntegrationTestBase.
- README và tài liệu. V1/V2 không đổi.

## Ba lỗi thường gặp

1. **Không có Docker environment:** mở Docker Desktop, kiểm tra `docker info`.
   Testcontainers dùng cổng ngẫu nhiên, không cần đổi DB dev. Không skip test để
   tuyên bố đã kiểm chứng MySQL nếu container chưa chạy.
2. **CHECK serial_type hoặc validation lỗi:** bật tracksSerial phải chọn IMEI/GENERIC;
   tắt thì serialType phải null. Dữ liệu trước V3 được backfill tự động; không sửa V2.
3. **Rollback-only sau tranh chấp unique:** không retry insert dòng tồn trong chính
   transaction đã lỗi. Dùng StockRowInitializer; quantity và movement phải vẫn
   cùng transaction nghiệp vụ, không tự REQUIRES_NEW toàn bộ nghiệp vụ kho.

## Danh sách test mới và test hồi quy

### `src/test/java/com/dienmay/inventory/InventoryPersistenceTest.java`

- `migration_emptyDatabase_appliesV1V2V3Successfully`
- `insert_duplicateGlobalSerial_databaseRejectsDuplicate`
- `insert_invalidStockQuantities_databaseCheckRejectsRow`
- `increase_twoConcurrentInitializations_keepsOneRowAndBothMovements`
- `decrease_insufficientStock_rollsBackWithoutExtraMovement`
- `transition_registeredSerial_persistsHistoryAndIncrementsVersion`
- `update_productHasStock_blocksChangingTrackingMode`
- `insert_invalidActorShape_databaseCheckRejectsRow`
- `update_invalidSerialConfiguration_databaseCheckRejectsChange`
- `insert_invalidHistoryActor_databaseCheckRejectsRow`

### `src/test/java/com/dienmay/inventory/serial/SerialNumberRulesTest.java`

- `validate_knownValidImei_passesLuhnAndValidation`
- `validate_wrongCheckDigit_reportsLuhnError`
- `validate_wrongImeiLengthOrLetters_rejectsInput`
- `validate_internalWhitespace_rejectsInput`
- `normalize_lowercaseAndOuterSpaces_returnsUppercase`
- `validate_invalidGeneric_rejectsInput`

### `src/test/java/com/dienmay/inventory/serial/SerialStateServiceTest.java`

- `transition_validTransition_writesExactlyOneHistory`
- `transition_invalidTransition_doesNotWriteHistory`
- `transition_damagedSerial_cannotLeaveFinalState`
- `transition_transferCompleted_changesBranchAndRecordsBothBranches`
- `transition_branchChangeOutsideTransfer_rejectsRequest`
- `register_validSerial_createsInStockWithInitialHistory`

### `src/test/java/com/dienmay/inventory/serial/SerialStatusTest.java`

- `canTransitionTo_everyPair_matchesSpecifiedMatrix`

### `src/test/java/com/dienmay/inventory/service/InventoryContextValidatorTest.java`

- `validate_existingEmployee_acceptsActor`
- `validate_missingEmployee_rejectsActor`
- `validate_systemWithoutId_acceptsActor`
- `validate_systemWithId_rejectsActor`
- `validate_customerWithId_defersExistenceCheck`
- `validate_customerWithoutId_rejectsActor`

### `src/test/java/com/dienmay/inventory/service/InventoryProductGuardTest.java`

- `hasInventoryData_existingSerial_returnsTrue`
- `hasInventoryData_positiveStock_returnsTrue`
- `hasInventoryData_receiptItemEvenWithoutStock_returnsTrue`
- `hasInventoryData_noSerialPositiveStockOrReceipt_returnsFalse`

### `src/test/java/com/dienmay/inventory/service/StockRowInitializerTest.java`

- `ensureExists_concurrentUniqueConflict_retriesInNewTransaction`

### `src/test/java/com/dienmay/inventory/service/StockServiceTest.java`

- `decrease_moreThanAvailable_rejectsWithoutMovement`
- `increase_serialProduct_rejectsQuantityStock`
- `decrease_validAmount_recordsNegativeDelta`
- `increase_validAmount_recordsPositiveDelta`
- `getAvailableQuantity_reservedStock_returnsQuantityMinusReserved`
- `increase_nonPositiveAmount_rejectsRequest`

ProductServiceTest còn bổ sung: đổi tracksSerial/serialType có dữ liệu kho bị chặn;
thiếu serialType bị từ chối; không đổi cấu hình thì vẫn sửa thông tin khác.

## Kết quả kiểm tra cuối cùng

`./mvnw -B test`: **169 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**.

| Lớp test inventory | Số ca |
| --- | ---: |
| InventoryPersistenceTest | 14 |
| SerialNumberRulesTest | 15 |
| SerialStateServiceTest | 6 |
| SerialStatusTest | 36 |
| InventoryContextValidatorTest | 6 |
| InventoryProductGuardTest | 4 |
| StockRowInitializerTest | 1 |
| StockServiceTest | 6 |

Tổng 88 ca inventory mới, 4 ca hồi quy mới của ProductService, 77 ca trước đó.

- Testcontainers MySQL 8.0.46 từ DB trống: Flyway áp dụng sạch V1/V2/V3.
- Serial trùng bị unique constraint từ chối; stock âm/reserved âm/reserved vượt
  quantity bị CHECK từ chối; actor sai hình dạng ở cả hai bảng bị CHECK từ chối.
- Với CHECK, test xác nhận SQLException có mã MySQL 3819; không chỉ kiểm tra
  một loại RuntimeException chung. JDBC có thể ánh xạ lỗi này là UncategorizedSQLException.
- Hai luồng tăng tồn 3 và 4: tồn cuối 7, đúng 1 dòng stock, 2 movement, version=2.
- Serial register rồi RESERVED: 2 history, version=1; chuyển sai không thêm history.
- DB dev: V1/V2/V3 success=1; một sản phẩm cũ tracks_serial=1 được backfill GENERIC.
- Ứng dụng khởi động lại trên cổng 8081, log xác nhận current version=3 và
  Started DienmayApplication, không có ERROR khởi động.
- V1/V2 checksum giữ nguyên. Chưa kiểm tra bằng mắt JavaScript ẩn/hiện Loại Serial.
