package com.smart.kf.service;

import com.smart.kf.model.FileUpload;
import com.smart.kf.model.User;
import com.smart.kf.repository.DocumentVectorRepository;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.UserRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private FileUploadRepository fileUploadRepository;
    @Mock private DocumentVectorRepository documentVectorRepository;
    @Mock private MinioClient minioClient;
    @Mock private ElasticsearchService elasticsearchService;
    @Mock private OrgTagCacheService orgTagCacheService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private DocumentService documentService;

    private User regularUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        regularUser = new User();
        regularUser.setId(1L);
        regularUser.setUsername("alice");
        regularUser.setRole(User.Role.USER);
        regularUser.setRoles(new HashSet<>());

        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");
        adminUser.setRole(User.Role.ADMIN);
        adminUser.setRoles(new HashSet<>());
    }

    // isAdminUser

    @Test
    void isAdminUser_adminRole_returnsTrue() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        assertTrue(documentService.isAdminUser("admin"));
    }

    @Test
    void isAdminUser_regularUser_returnsFalse() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        assertFalse(documentService.isAdminUser("alice"));
    }

    @Test
    void isAdminUser_unknownUser_returnsFalse() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertFalse(documentService.isAdminUser("ghost"));
    }

    // deleteDocumentWithPermission

    @Test
    void deleteDocumentWithPermission_fileNotFound_throwsIllegalArgument() {
        when(fileUploadRepository.findByFileMd5("md5_x")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> documentService.deleteDocumentWithPermission("md5_x", "alice"));
    }

    @Test
    void deleteDocumentWithPermission_unauthorized_throwsSecurityException() {
        FileUpload file = buildFile("md5_1", "1", "file.txt"); // owned by user id=1 (alice)
        when(fileUploadRepository.findByFileMd5("md5_1")).thenReturn(Optional.of(file));
        // bob (id=3) tries to delete alice's file
        User bob = new User();
        bob.setId(3L);
        bob.setUsername("bob");
        bob.setRole(User.Role.USER);
        bob.setRoles(new HashSet<>());
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser)); // owner = alice
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob)); // operator = bob

        assertThrows(SecurityException.class,
            () -> documentService.deleteDocumentWithPermission("md5_1", "bob"));
    }

    @Test
    void deleteDocumentWithPermission_admin_canDeleteOthersFile() throws Exception {
        FileUpload file = buildFile("md5_1", "1", "file.txt");
        when(fileUploadRepository.findByFileMd5("md5_1")).thenReturn(Optional.of(file));
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser)); // file owner
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        // deleteDocument sub-call
        when(fileUploadRepository.findByFileMd5AndUserId("md5_1", "1")).thenReturn(Optional.of(file));
        doNothing().when(elasticsearchService).deleteByFileMd5(anyString());
        doNothing().when(minioClient).removeObject(any());
        doNothing().when(documentVectorRepository).deleteByFileMd5(anyString());
        doNothing().when(fileUploadRepository).deleteByFileMd5(anyString());

        documentService.deleteDocumentWithPermission("md5_1", "admin");

        verify(fileUploadRepository).deleteByFileMd5("md5_1");
        verify(notificationService).sendNotification(eq("alice"), eq("admin"), eq("DELETE_DOCUMENT"), eq("md5_1"), eq("file.txt"));
    }

    @Test
    void deleteDocumentWithPermission_owner_canDeleteOwnFile() throws Exception {
        FileUpload file = buildFile("md5_1", "1", "myFile.pdf");
        when(fileUploadRepository.findByFileMd5("md5_1")).thenReturn(Optional.of(file));
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser)); // owner = alice
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(fileUploadRepository.findByFileMd5AndUserId("md5_1", "1")).thenReturn(Optional.of(file));
        doNothing().when(elasticsearchService).deleteByFileMd5(anyString());
        doNothing().when(minioClient).removeObject(any());
        doNothing().when(documentVectorRepository).deleteByFileMd5(anyString());
        doNothing().when(fileUploadRepository).deleteByFileMd5(anyString());

        documentService.deleteDocumentWithPermission("md5_1", "alice");

        verify(fileUploadRepository).deleteByFileMd5("md5_1");
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }

    // getUserUploadedFiles

    @Test
    void getUserUploadedFiles_adminGetsAll() {
        FileUpload f1 = buildFile("md5_1", "1", "a.pdf");
        FileUpload f2 = buildFile("md5_2", "3", "b.docx");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(fileUploadRepository.findAll()).thenReturn(List.of(f1, f2));

        List<FileUpload> result = documentService.getUserUploadedFiles("99", "admin");

        assertEquals(2, result.size());
        verify(fileUploadRepository, never()).findByUserId(any());
    }

    @Test
    void getUserUploadedFiles_regularUser_getsOwnFiles() {
        FileUpload f1 = buildFile("md5_1", "1", "a.pdf");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(fileUploadRepository.findByUserId("1")).thenReturn(List.of(f1));

        List<FileUpload> result = documentService.getUserUploadedFiles("1", "alice");

        assertEquals(1, result.size());
        verify(fileUploadRepository, never()).findAll();
    }

    // Helper

    private FileUpload buildFile(String md5, String userId, String fileName) {
        FileUpload f = new FileUpload();
        f.setFileMd5(md5);
        f.setUserId(userId);
        f.setFileName(fileName);
        f.setTotalSize(1024L);
        f.setStatus(1);
        return f;
    }
}
