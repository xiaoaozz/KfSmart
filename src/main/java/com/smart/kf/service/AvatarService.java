package com.smart.kf.service;

import com.smart.kf.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Service
public class AvatarService {

    static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    public void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("头像文件不能为空", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new CustomException("头像文件不能超过2MB", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException("仅支持 JPG、PNG、WebP、GIF 格式头像", HttpStatus.BAD_REQUEST);
        }
    }

    public String getAvatarExtension(String contentType) {
        if (contentType == null) {
            return ".png";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    /**
     * Saves the avatar file to the given directory and returns the served URL path.
     *
     * @param file     the uploaded multipart file
     * @param userId   user ID used to name the file
     * @param baseDir  directory to write the file into
     * @return relative URL e.g. "/avatars/user-42.jpg"
     */
    public String saveAvatarFile(MultipartFile file, Long userId, Path baseDir) throws IOException {
        String extension = getAvatarExtension(file.getContentType());
        Files.createDirectories(baseDir);
        String fileName = "user-" + userId + extension;
        Path target = baseDir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return "/avatars/" + fileName;
    }
}
