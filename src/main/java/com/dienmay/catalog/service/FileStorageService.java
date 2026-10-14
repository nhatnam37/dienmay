package com.dienmay.catalog.service;

import com.dienmay.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        try {
            Path directory = Path.of(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            this.root = directory.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể mở thư mục lưu ảnh.", exception);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ảnh.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Ảnh tối đa 2MB.");
        }
        String original = file.getOriginalFilename();
        int dot = original == null ? -1 : original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(extension);
        if (expectedType == null || !expectedType.equalsIgnoreCase(file.getContentType())) {
            throw new BusinessException("Chỉ nhận ảnh JPG, PNG hoặc WEBP có định dạng và content-type phù hợp.");
        }
        // Không dùng bất kỳ thành phần đường dẫn/tên gốc nào để tạo tên lưu.
        Path target = root.resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!target.getParent().equals(root)) {
            throw new BusinessException("Đường dẫn ảnh không hợp lệ.");
        }
        try (InputStream input = file.getInputStream()) {
            // Đọc có giới hạn cả khi MultipartFile báo sai size; kiểm tra chữ ký ảnh cơ bản.
            byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new BusinessException("Ảnh tối đa 2MB.");
            }
            if (!validSignature(bytes, expectedType)) {
                throw new BusinessException("Nội dung file không đúng định dạng ảnh đã chọn.");
            }
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return "/uploads/" + target.getFileName();
        } catch (IOException exception) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw new BusinessException("Không thể lưu ảnh. Vui lòng thử lại.");
        }
    }

    private boolean validSignature(byte[] data, String type) {
        if (type.equals("image/jpeg")) {
            return data.length >= 3 && (data[0] & 255) == 255 && (data[1] & 255) == 216 && (data[2] & 255) == 255;
        }
        if (type.equals("image/png")) {
            byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            return data.length >= 8 && java.util.Arrays.equals(signature, java.util.Arrays.copyOf(data, 8));
        }
        return data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || !imageUrl.matches("/uploads/[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp)")) {
            return; // Không xóa URL bên ngoài hoặc đường dẫn do người dùng tự tạo.
        }
        Path target = root.resolve(imageUrl.substring("/uploads/".length())).normalize();
        if (!target.getParent().equals(root)) {
            throw new BusinessException("Đường dẫn ảnh không hợp lệ.");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new BusinessException("Không thể xóa ảnh cũ trên đĩa.");
        }
    }

    public Path getRoot() { return root; }
}
