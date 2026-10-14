# Catalog 3B — tầng web ADMIN và upload ảnh

## Phương án và luồng xử lý

```text
GET -> Controller -> Service -> View DTO -> Thymeleaf + layout fragment
POST -> @Valid + BindingResult -> Service -> redirect + flash -> GET
```

Controller không truy cập repository/entity. Lỗi nhập liệu và BusinessException
được đưa vào BindingResult, chuyển qua flash rồi redirect về GET form. Các select
được nạp lại ở GET. Lưu thành công redirect về danh sách, có flash message;
F5 không gửi lại POST. File phải chọn lại khi form lỗi vì trình duyệt không cho
khôi phục lựa chọn file. Không lưu nội dung MultipartFile trong form flash.

`CatalogBindingAdvice` trim chuỗi trước @Valid và chặn bind các trường hệ thống
như id, imageUrl, status. Chuẩn hóa chữ hoa SKU vẫn ở ProductService.
`ProductEditForm` kế thừa ProductForm, bổ sung MultipartFile chỉ ở tầng web.

## Các URL

| Module | GET | POST |
| --- | --- | --- |
| Danh mục | `/admin/catalog/categories`, `/new`, `/{id}/edit` | gốc: thêm; `/{id}`: sửa; `/{id}/delete`: xóa |
| Thương hiệu | `/admin/catalog/brands`, `/new`, `/{id}/edit` | gốc: thêm; `/{id}`: sửa; `/{id}/delete`: xóa |
| Sản phẩm | `/admin/catalog/products`, `/new`, `/{id}/edit`, `/{id}` | gốc: thêm; `/{id}`: sửa; `/{id}/discontinue`, `/{id}/reactivate` |

Các hậu tố ở bảng nối vào URL gốc tương ứng. Danh sách sản phẩm dùng 10 dòng/trang,
sắp ID giảm dần, tham số `page` bắt đầu từ 0. Khi nhấn Trước/Sau, giữ keyword,
categoryId, brandId, status. Link điều hướng catalog chỉ hiện cho ADMIN; rule
`/admin/**` ở SecurityConfig vẫn là lớp kiểm soát truy cập thực sự.

## Upload và transaction

- Cấu hình `app.upload-dir` mặc định `./uploads`, có thể đổi bằng biến `UPLOAD_DIR`.
  Đường dẫn tương đối tính từ thư mục chạy ứng dụng.
- JPG/JPEG, PNG, WEBP; kiểm tra phần mở rộng + content-type và chữ ký file cơ bản.
  Chữ ký cơ bản không thay thế giải mã ảnh đầy đủ hoặc quét mã độc.
- Giới hạn file 2MB ở cả Spring multipart và FileStorageService. Request tối đa
  3MB để có chỗ cho các trường form. FileStorageService còn đọc tối đa 2MB+1 byte
  để tránh tin hoàn toàn kích thước được khai báo.
- Tên trên đĩa là UUID + phần mở rộng hợp lệ; không dùng tên gốc. Tên `../x.png`
  vẫn được lưu bằng UUID bên trong upload-dir, không thể chọn thư mục đích.
- ResourceHandler ánh xạ `/uploads/**`; SecurityConfig cho public đúng pattern
  này, vẫn giữ CSRF và quyền ADMIN cho các thao tác ghi.
- ProductAdminService mở transaction bao trùm thao tác ảnh và gọi ProductService.
  Khi sửa, khóa dòng sản phẩm để hai lần thay ảnh đồng thời không đọc cùng ảnh cũ.
- Sau lưu file mới, đăng ký TransactionSynchronization trước khi gọi lưu DB:
  commit thành công -> xóa ảnh cũ; rollback -> xóa ảnh mới, giữ ảnh cũ.
  Các URL ngoài upload-dir không được xóa.
- Nếu bản thân đĩa lỗi khi dọn file, ghi log máy chủ để xử lý; không báo sai rằng
  DB chưa lưu sau khi DB đã commit. DB và filesystem không phải một transaction
  nguyên tử: mất điện/crash giữa ghi file và callback có thể cần đối soát file.
- Upload-dir đã được gitignore. Không xóa file ảnh khi ngừng kinh doanh sản phẩm.

## File mới chính

- `catalog/web/CategoryController.java`, `BrandController.java`, `ProductController.java`.
- `catalog/web/ProductEditForm.java`, `CatalogBindingAdvice.java`, `UploadExceptionHandler.java`.
- `catalog/service/FileStorageService.java`, `ProductAdminService.java`.
- `config/UploadWebConfig.java`.
- `templates/fragments/layout.html`.
- `templates/catalog/categories/{list,form}.html`.
- `templates/catalog/brands/{list,form}.html`.
- `templates/catalog/products/{list,form,detail}.html`.
- `messages.properties` có thông báo lỗi chuyển kiểu số bằng tiếng Việt.

Đường dẫn Java ở trên nằm dưới `src/main/java/com/dienmay/`; template và messages
nằm dưới `src/main/resources/`.

## Các file hiện có đã điều chỉnh

- CategoryService/BrandService: thêm getForm; hai mapper tương ứng thêm toForm.
- ProductRepository/ProductService: thêm truy vấn khóa dòng và getByIdForUpdate
  phục vụ cập nhật ảnh trong transaction ngoài của ProductAdminService.
- SecurityConfig: thêm `/uploads/**` vào danh sách public; không tắt CSRF.
- application.yml: giới hạn multipart, upload-dir, tắt stacktrace/message nội bộ
  trong trang lỗi, kể cả khi chạy DevTools.
- home.html/admin/index.html: dùng chung head/navbar, có liên kết quản lý catalog.
- SecurityRulesTest: giới hạn MVC slice vào ba controller cũ để test cũ không
  cần inject service của những controller catalog mới.
- .gitignore, README.md và tài liệu.
- Không sửa entity, schema/migration V1 hoặc V2. Không thêm Python vào project.

## Chạy

Dùng JDK 21, tại thư mục `dienmay`:

```sh
docker compose up -d
./mvnw test
./mvnw spring-boot:run
```

Nếu ứng dụng đã chạy ở 8081, không khởi động bản thứ hai. Truy cập:
http://localhost:8081/admin/catalog/products

Tài khoản dev: admin / Admin@123; cashier01 / Cashier@123.

Chạy riêng test 3B không cần DB:

```sh
./mvnw -Dtest=FileStorageServiceTest,ProductAdminServiceTest,CatalogWebTest test
```

Toàn bộ `./mvnw test` cần MySQL ở localhost:3307 vì vẫn giữ test tích hợp 3A.

## Kết quả kiểm tra thật

- `./mvnw test`: 77 test, 0 failures, 0 errors, 0 skipped.
- 30 ca mới: FileStorageServiceTest 13 (có tham số), ProductAdminServiceTest 4,
  CatalogWebTest 13; 47 ca trước đó giữ nguyên kết quả đạt.
- MockMvc render template thật, kiểm tra giá `1.000.000 đ`, link phân trang giữ
  bộ lọc, validation tiếng Việt, PRG lỗi và thành công, quyền ADMIN/CASHIER, CSRF.
- HTTP thực tại 8081: home, admin, các trang list/new, thêm sản phẩm multipart,
  public ảnh trả 200, cập nhật ảnh xóa file cũ, DB lỗi trùng SKU xóa file mới,
  SVG bị từ chối, file quá 2MB chuyển về danh sách kèm thông báo.
- Bản ghi và file có mã `WEB-PROBE-UPLOAD` tạo riêng để thử đã được dọn.
- Đã xem bằng mắt trang chi tiết trên Chrome: navbar, giá, trạng thái và bố cục.
  Chưa kiểm tra bằng mắt toàn bộ list/form, hộp xác nhận, upload qua hộp chọn file
  và responsive trên điện thoại. Không coi kiểm tra HTTP/MockMvc là kiểm tra mắt.

## Các bước kiểm tra thủ công trên trình duyệt

1. Đăng nhập admin. Mở `/admin`, kiểm tra navbar và ba liên kết catalog.
2. Vào Danh mục: thêm một danh mục gốc và một danh mục con, sửa tên; thử nhập
   tên trùng, chọn con/cháu làm cha; thử xóa cha còn con. Phải hiện thông báo,
   không có stacktrace. Xóa danh mục trống phải thành công.
3. Vào Thương hiệu: thêm/sửa/xóa một thương hiệu thử. Thử tên trùng khác hoa
   thường. Sau khi thương hiệu có sản phẩm, xóa phải bị chặn.
4. Thêm sản phẩm: SKU chữ thường, giá 1000000, danh mục/thương hiệu, ảnh JPG/PNG/WEBP
   dưới 2MB. Sau lưu, kiểm tra SKU viết hoa, giá `1.000.000 đ`, ảnh thu nhỏ và detail.
5. Tạo SKU trùng có kèm ảnh: phải trở lại form, giữ các trường đã nhập và thông
   báo lỗi; mở uploads để kiểm tra không thêm file mồ côi. Chọn lại file khi sửa lỗi.
6. Sửa sản phẩm, thay ảnh: ảnh mới hiện, file cũ bị xóa. Sửa không chọn ảnh:
   ảnh hiện tại vẫn giữ nguyên. Thử file .svg/.txt, MIME giả và ảnh >2MB.
7. Thử dữ liệu không hợp lệ: tên rỗng, giá âm, giá không phải số, bảo hành >60;
   kiểm tra lỗi từng trường. F5 sau lưu không được tạo thêm dòng.
8. Thêm ít nhất 11 sản phẩm cùng bộ lọc, tìm keyword và chọn các bộ lọc;
   chuyển trang Sau/Trước, kiểm tra URL và bộ lọc vẫn giữ nguyên.
9. Bấm Sửa/Xóa để kiểm tra hộp xác nhận và Hủy. Bấm Ngừng kinh doanh:
   badge phải xám; Kinh doanh lại: badge xanh. Dòng sản phẩm không bị xóa.
10. Đăng xuất (POST), đăng nhập cashier01, gõ trực tiếp URL catalog: nhận 403.
    Mở URL ảnh trong cửa sổ chưa đăng nhập: vẫn xem được.
11. Thu nhỏ cửa sổ hoặc dùng chế độ mobile để kiểm tra navbar, bảng cuộn ngang,
    form và nút thao tác không bị che khuất.

## Ba lỗi thường gặp

1. **403 khi gửi form:** thiếu CSRF hoặc dùng tài khoản không có ADMIN.
   Các form phải giữ `th:action` và POST; không giải quyết bằng cách tắt CSRF.
2. **Ảnh 404/lưu ảnh thất bại:** sai UPLOAD_DIR, chạy từ thư mục khác, hoặc không
   có quyền ghi thư mục. DB chỉ lưu URL `/uploads/UUID.ext`, không lưu đường dẫn
   máy cá nhân; không đặt ảnh trong src/main/resources.
3. **Form mất ảnh sau lỗi / file quá 2MB:** phải chọn lại ảnh theo cơ chế trình
   duyệt. Giới hạn multipart có thể bắt request trước controller, khi đó hệ thống
   redirect danh sách kèm flash và yêu cầu mở lại form.

## Danh sách test mới

### `src/test/java/com/dienmay/catalog/service/FileStorageServiceTest.java`

- `store_invalidExtension_rejectsFile`
- `store_mismatchedContentType_rejectsFile`
- `store_fakeImageContent_rejectsFile`
- `store_fileAboveTwoMegabytes_rejectsFile`
- `store_originalName_usesUuidInsteadOfOriginalName`
- `store_traversalOriginalName_staysInsideUploadDirectory`
- `delete_traversalUrl_doesNotTouchOutsideFile`
- `delete_storedImage_removesFile`
- `store_supportedFormats_acceptsFile`
- `store_emptyFile_rejectsFile`

### `src/test/java/com/dienmay/catalog/service/ProductAdminServiceTest.java`

- `create_databaseFailure_deletesNewImageAfterRollback`
- `update_committedChange_deletesOldImageOnlyAfterCommit`
- `update_rollback_preservesOldImageAndDeletesNewImage`
- `update_noImage_preservesCurrentUrlAndIgnoresForgedUrl`

### `src/test/java/com/dienmay/catalog/web/CatalogWebTest.java`

- `products_cashier_isForbidden`
- `products_admin_rendersPageWithFiltersAndPrice`
- `createProduct_missingCsrf_isForbidden`
- `createProduct_invalidForm_redirectsWithFieldErrors`
- `createProduct_validForm_redirectsWithSuccess`
- `createCategory_duplicateName_redirectsWithBusinessError`
- `deleteBrand_hasProducts_redirectsWithFailureFlash`
- `detail_existingProduct_rendersDetails`
- `edit_existingProduct_rendersMultipartForm`
- `categories_admin_rendersList`
- `editCategoryAndBrand_existingItems_renderForms`
- `createProduct_invalidNumericInput_rendersVietnameseFieldError`
- `brands_admin_rendersListAndNewForm`
