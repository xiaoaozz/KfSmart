package com.smart.kf.service;

import com.smart.kf.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @InjectMocks
    private AvatarService avatarService;

    // -------------------------------------------------------------------------
    // validateAvatarFile
    // -------------------------------------------------------------------------

    @Test
    void validateAvatarFile_nullFile_throwsBadRequest() {
        CustomException ex = assertThrows(CustomException.class,
                () -> avatarService.validateAvatarFile(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateAvatarFile_emptyFile_throwsBadRequest() {
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);
        CustomException ex = assertThrows(CustomException.class,
                () -> avatarService.validateAvatarFile(empty));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateAvatarFile_fileTooLarge_throwsBadRequest() {
        byte[] oversized = new byte[(int) AvatarService.MAX_AVATAR_SIZE + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);
        CustomException ex = assertThrows(CustomException.class,
                () -> avatarService.validateAvatarFile(file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("2MB"));
    }

    @Test
    void validateAvatarFile_invalidContentType_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        CustomException ex = assertThrows(CustomException.class,
                () -> avatarService.validateAvatarFile(file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateAvatarFile_nullContentType_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "img.bin", null, new byte[]{1, 2, 3});
        CustomException ex = assertThrows(CustomException.class,
                () -> avatarService.validateAvatarFile(file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateAvatarFile_validJpeg_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> avatarService.validateAvatarFile(file));
    }

    @Test
    void validateAvatarFile_validPng_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", new byte[]{1});
        assertDoesNotThrow(() -> avatarService.validateAvatarFile(file));
    }

    @Test
    void validateAvatarFile_validWebp_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "img.webp", "image/webp", new byte[]{1});
        assertDoesNotThrow(() -> avatarService.validateAvatarFile(file));
    }

    @Test
    void validateAvatarFile_validGif_doesNotThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "anim.gif", "image/gif", new byte[]{1});
        assertDoesNotThrow(() -> avatarService.validateAvatarFile(file));
    }

    // -------------------------------------------------------------------------
    // getAvatarExtension
    // -------------------------------------------------------------------------

    @Test
    void getAvatarExtension_jpeg_returnsJpg() {
        assertEquals(".jpg", avatarService.getAvatarExtension("image/jpeg"));
    }

    @Test
    void getAvatarExtension_png_returnsPng() {
        assertEquals(".png", avatarService.getAvatarExtension("image/png"));
    }

    @Test
    void getAvatarExtension_webp_returnsWebp() {
        assertEquals(".webp", avatarService.getAvatarExtension("image/webp"));
    }

    @Test
    void getAvatarExtension_gif_returnsGif() {
        assertEquals(".gif", avatarService.getAvatarExtension("image/gif"));
    }

    @Test
    void getAvatarExtension_null_returnsPng() {
        assertEquals(".png", avatarService.getAvatarExtension(null));
    }

    @Test
    void getAvatarExtension_unknownType_returnsPng() {
        assertEquals(".png", avatarService.getAvatarExtension("image/bmp"));
    }

    @Test
    void getAvatarExtension_caseInsensitive_returnsCorrectExtension() {
        assertEquals(".jpg", avatarService.getAvatarExtension("IMAGE/JPEG"));
    }

    // -------------------------------------------------------------------------
    // saveAvatarFile
    // -------------------------------------------------------------------------

    @Test
    void saveAvatarFile_createsFileAndReturnsUrl(@TempDir Path tempDir) throws IOException {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);

        String url = avatarService.saveAvatarFile(file, 42L, tempDir);

        assertEquals("/avatars/user-42.jpg", url);
        Path saved = tempDir.resolve("user-42.jpg");
        assertTrue(Files.exists(saved));
        assertArrayEquals(content, Files.readAllBytes(saved));
    }

    @Test
    void saveAvatarFile_overwritesExistingFile(@TempDir Path tempDir) throws IOException {
        Path existing = tempDir.resolve("user-1.png");
        Files.write(existing, new byte[]{0x00});

        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{0x01, 0x02});
        avatarService.saveAvatarFile(file, 1L, tempDir);

        assertArrayEquals(new byte[]{0x01, 0x02}, Files.readAllBytes(existing));
    }

    @Test
    void saveAvatarFile_createsDirectoryIfAbsent(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("deep/avatars");
        MockMultipartFile file = new MockMultipartFile("file", "a.webp", "image/webp", new byte[]{0x01});

        String url = avatarService.saveAvatarFile(file, 7L, nested);

        assertEquals("/avatars/user-7.webp", url);
        assertTrue(Files.exists(nested.resolve("user-7.webp")));
    }
}
