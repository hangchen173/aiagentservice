package com.intern.chat;

import com.intern.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageStorageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif");

    private final Path storageRoot;
    private final long maxBytes;

    public ImageStorageService(
            @Value("${nexusmind.chat.image-storage-path:./data/chat-images}") String storagePath,
            @Value("${nexusmind.chat.image-max-bytes:8388608}") long maxBytes) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要识别的图片");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("图片不能超过 " + (maxBytes / 1024 / 1024) + " MB");
        }
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException("仅支持 JPG、PNG 或 GIF 图片");
        }

        try {
            byte[] data = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(data)) == null) {
                throw new BusinessException("上传的文件不是有效图片");
            }
            Files.createDirectories(storageRoot);
            String key = UUID.randomUUID() + extension;
            Files.write(storageRoot.resolve(key), data, StandardOpenOption.CREATE_NEW);
            String submittedName = file.getOriginalFilename();
            String originalName = submittedName == null || submittedName.isBlank() ? "image" + extension
                    : submittedName.replace('\\', '/').substring(submittedName.replace('\\', '/').lastIndexOf('/') + 1);
            return new StoredImage(key, contentType, originalName, data);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException("图片保存失败，请稍后重试");
        }
    }

    public byte[] read(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]+\\.(jpg|png|gif)")) {
            throw new BusinessException("图片不存在");
        }
        try {
            Path image = storageRoot.resolve(key).normalize();
            if (!image.startsWith(storageRoot) || !Files.isRegularFile(image)) {
                throw new BusinessException("图片不存在");
            }
            return Files.readAllBytes(image);
        } catch (IOException ex) {
            throw new BusinessException("图片读取失败");
        }
    }
}
