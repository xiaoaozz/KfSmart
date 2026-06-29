package com.smart.kf.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeValidationServiceTest {

    private FileTypeValidationService service;

    @BeforeEach
    void setUp() {
        service = new FileTypeValidationService();
    }

    @Test
    void validateFileType_nullFileName_returnsFalse() {
        var result = service.validateFileType(null);
        assertFalse(result.isValid());
        assertEquals("文件名不能为空", result.getMessage());
    }

    @Test
    void validateFileType_emptyFileName_returnsFalse() {
        var result = service.validateFileType("  ");
        assertFalse(result.isValid());
        assertEquals("文件名不能为空", result.getMessage());
    }

    @Test
    void validateFileType_noExtension_returnsFalse() {
        var result = service.validateFileType("noextension");
        assertFalse(result.isValid());
        assertEquals("文件必须有扩展名", result.getMessage());
    }

    @Test
    void validateFileType_trailingDot_returnsFalse() {
        var result = service.validateFileType("file.");
        assertFalse(result.isValid());
        assertEquals("文件必须有扩展名", result.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"document.pdf", "report.docx", "data.xlsx", "slides.pptx", "readme.txt",
                            "article.md", "index.html", "data.json", "table.csv", "book.epub"})
    void validateFileType_supportedDocumentTypes_returnsTrue(String fileName) {
        var result = service.validateFileType(fileName);
        assertTrue(result.isValid(), "Expected " + fileName + " to be valid");
        assertNotNull(result.getExtension());
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "image.png", "animation.gif", "video.mp4", "audio.mp3",
                            "archive.zip", "archive.rar", "program.exe", "font.ttf"})
    void validateFileType_unsupportedBinaryTypes_returnsFalse(String fileName) {
        var result = service.validateFileType(fileName);
        assertFalse(result.isValid(), "Expected " + fileName + " to be invalid");
        assertTrue(result.getMessage().contains("不支持的文件类型"));
    }

    @Test
    void validateFileType_unknownExtension_returnsFalse() {
        var result = service.validateFileType("file.xyz123");
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("未知的文件类型"));
    }

    @Test
    void validateFileType_caseInsensitive_pdf_returnsTrue() {
        var result = service.validateFileType("REPORT.PDF");
        assertTrue(result.isValid());
        assertEquals("pdf", result.getExtension());
    }

    @Test
    void validateFileType_docxFileType_isWordDocument() {
        var result = service.validateFileType("contract.docx");
        assertTrue(result.isValid());
        assertEquals("Word文档", result.getFileType());
    }

    @Test
    void getSupportedExtensions_notEmpty() {
        Set<String> extensions = service.getSupportedExtensions();
        assertFalse(extensions.isEmpty());
        assertTrue(extensions.contains("pdf"));
        assertTrue(extensions.contains("docx"));
        assertTrue(extensions.contains("txt"));
    }

    @Test
    void getSupportedFileTypes_notEmpty() {
        Set<String> types = service.getSupportedFileTypes();
        assertFalse(types.isEmpty());
    }

    @Test
    void validateFileType_result_toStringContainsFields() {
        var result = service.validateFileType("test.pdf");
        String str = result.toString();
        assertTrue(str.contains("valid=true"));
    }
}
