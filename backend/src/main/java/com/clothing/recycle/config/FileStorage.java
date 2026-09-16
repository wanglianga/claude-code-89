package com.clothing.recycle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 照片本地存储：返回可通过 /uploads/** 访问的相对文件名 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.upload.dir}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir);
        Files.createDirectories(root);
    }

    public String save(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;
        String original = file.getOriginalFilename();
        String ext = ".jpg";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Files.copy(file.getInputStream(), root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public Path root() {
        return root;
    }
}
