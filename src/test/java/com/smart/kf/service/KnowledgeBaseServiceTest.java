package com.smart.kf.service;

import com.smart.kf.model.KnowledgeBase;
import com.smart.kf.model.User;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.KnowledgeBaseI18nRepository;
import com.smart.kf.repository.KnowledgeBaseRepository;
import com.smart.kf.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeBaseServiceTest {

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock private FileUploadRepository fileUploadRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrgTagCacheService orgTagCacheService;
    @Mock private DocumentService documentService;
    @Mock private NotificationService notificationService;
    @Mock private RbacService rbacService;
    @Mock private KnowledgeBaseI18nRepository knowledgeBaseI18nRepository;
    @Mock private I18nTranslationService i18nTranslationService;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    private User regularUser;
    private User adminUser;
    private User otherUser;

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

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setUsername("bob");
        otherUser.setRole(User.Role.USER);
        otherUser.setRoles(new HashSet<>());
    }

    // ---- createKnowledgeBase ----

    @Test
    void createKnowledgeBase_success() {
        when(knowledgeBaseRepository.existsByName("TestKB")).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase result = knowledgeBaseService.createKnowledgeBase(
            "TestKB", "description", "default", false, null, "alice");

        assertNotNull(result);
        assertEquals("TestKB", result.getName());
        assertTrue(result.getKbId().startsWith("kb_"));
        assertEquals(regularUser, result.getCreatedBy());
        assertEquals("folder", result.getIcon());
        verify(i18nTranslationService).translateKbAsync(anyString(), eq("TestKB"), eq("description"));
    }

    @Test
    void createKnowledgeBase_customIcon_stored() {
        when(knowledgeBaseRepository.existsByName("KB")).thenReturn(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase result = knowledgeBaseService.createKnowledgeBase(
            "KB", null, "org1", true, "book", "alice");

        assertEquals("book", result.getIcon());
        assertTrue(result.isPublic());
    }

    @Test
    void createKnowledgeBase_duplicateName_throwsIllegalArgument() {
        when(knowledgeBaseRepository.existsByName("TestKB")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> knowledgeBaseService.createKnowledgeBase("TestKB", "desc", "default", false, null, "alice"));
        assertTrue(ex.getMessage().contains("TestKB"));
    }

    @Test
    void createKnowledgeBase_userNotFound_throwsIllegalArgument() {
        when(knowledgeBaseRepository.existsByName("NewKB")).thenReturn(false);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> knowledgeBaseService.createKnowledgeBase("NewKB", "desc", "default", false, null, "ghost"));
    }

    // ---- getAccessibleKnowledgeBases ----

    @Test
    void getAccessibleKnowledgeBases_adminSeesAll() {
        KnowledgeBase kb1 = makeKb("kb_1", "KB1", regularUser, false, "org1");
        KnowledgeBase kb2 = makeKb("kb_2", "KB2", otherUser, false, "org2");
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(kb1, kb2));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("admin")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        List<Map<String, Object>> result = knowledgeBaseService.getAccessibleKnowledgeBases("admin", null);

        assertEquals(2, result.size());
    }

    @Test
    void getAccessibleKnowledgeBases_regularUser_seesOwnAndPublic() {
        // alice owns kb_1; kb_2 is public (owned by bob); kb_3 is private (owned by bob, org2)
        KnowledgeBase kb1 = makeKb("kb_1", "OwnKB", regularUser, false, "org1");
        KnowledgeBase kb2 = makeKb("kb_2", "PublicKB", otherUser, true, "org1");
        KnowledgeBase kb3 = makeKb("kb_3", "PrivateKB", otherUser, false, "org2");

        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(kb1, kb2, kb3));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(rbacService.hasResourcePermission(anyString(), anyString(), anyString(), anyString(),
            any(), any(), anyBoolean())).thenReturn(false);
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        List<Map<String, Object>> result = knowledgeBaseService.getAccessibleKnowledgeBases("alice", null);

        assertEquals(2, result.size()); // own + public
    }

    @Test
    void getAccessibleKnowledgeBases_orgTagGrant_allowsAccess() {
        KnowledgeBase kb = makeKb("kb_1", "OrgKB", otherUser, false, "org1");

        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of("org1"));
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        List<Map<String, Object>> result = knowledgeBaseService.getAccessibleKnowledgeBases("alice", null);

        assertEquals(1, result.size());
    }

    @Test
    void getAccessibleKnowledgeBases_keywordFilter_matchesName() {
        KnowledgeBase kb1 = makeKb("kb_1", "Documentation", regularUser, true, "org1");
        KnowledgeBase kb2 = makeKb("kb_2", "Reports", regularUser, true, "org1");

        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(kb1, kb2));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        List<Map<String, Object>> result = knowledgeBaseService.getAccessibleKnowledgeBases(
            "alice", null, "Doc", null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Documentation", result.get(0).get("name"));
    }

    @Test
    void getAccessibleKnowledgeBases_publicFilter_returnsOnlyPublic() {
        KnowledgeBase publicKb = makeKb("kb_1", "Public", regularUser, true, "org1");
        KnowledgeBase privateKb = makeKb("kb_2", "Private", regularUser, false, "org1");

        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(publicKb, privateKb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        List<Map<String, Object>> result = knowledgeBaseService.getAccessibleKnowledgeBases(
            "alice", null, null, null, true, null, null);

        assertEquals(1, result.size());
        assertEquals(true, result.get(0).get("isPublic"));
    }

    @Test
    void getAccessibleKnowledgeBases_orgTagFilter() {
        KnowledgeBase kb1 = makeKb("kb_1", "OrgA KB", regularUser, true, "orgA");
        KnowledgeBase kb2 = makeKb("kb_2", "OrgB KB", regularUser, true, "orgB");

        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(kb1, kb2));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        List<Map<String, Object>> result = knowledgeBaseService.getAccessibleKnowledgeBases(
            "alice", null, null, "orgA", null, null, null);

        assertEquals(1, result.size());
        assertEquals("orgA", result.get(0).get("orgTag"));
    }

    // ---- getKnowledgeBaseStats ----

    @Test
    void getKnowledgeBaseStats_returnsAggregatedData() {
        KnowledgeBase kb = makeKb("kb_1", "KB1", regularUser, true, "org1");
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId(anyString())).thenReturn(List.of());

        Map<String, Object> stats = knowledgeBaseService.getKnowledgeBaseStats("alice", null);

        assertEquals(1, stats.get("knowledgeBaseCount"));
        assertNotNull(stats.get("documentCount"));
        assertNotNull(stats.get("totalSize"));
    }

    // ---- updateKnowledgeBase ----

    @Test
    void updateKnowledgeBase_success_byOwner() {
        KnowledgeBase kb = makeKb("kb_1", "OldName", regularUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase result = knowledgeBaseService.updateKnowledgeBase(
            "kb_1", "NewName", "new desc", null, null, null, "alice", null);

        assertEquals("NewName", result.getName());
        assertEquals("new desc", result.getDescription());
        verify(i18nTranslationService).retranslateKbAsync(eq("kb_1"), eq("NewName"), eq("new desc"));
    }

    @Test
    void updateKnowledgeBase_success_byAdmin() {
        KnowledgeBase kb = makeKb("kb_1", "OldName", regularUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("admin")).thenReturn(List.of());
        when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        knowledgeBaseService.updateKnowledgeBase("kb_1", "Updated", null, null, null, null, "admin", null);

        verify(knowledgeBaseRepository).save(any());
        // Admin notifies resource owner
        verify(notificationService).sendNotification(eq("alice"), eq("admin"), eq("UPDATE_KB"), eq("kb_1"), any());
    }

    @Test
    void updateKnowledgeBase_notFound_throwsIllegalArgument() {
        when(knowledgeBaseRepository.findByKbId("kb_missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> knowledgeBaseService.updateKnowledgeBase("kb_missing", "New", null, null, null, null, "alice", null));
    }

    @Test
    void updateKnowledgeBase_notOwnerNotAdmin_throwsSecurityException() {
        KnowledgeBase kb = makeKb("kb_1", "OldName", otherUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());

        assertThrows(SecurityException.class,
            () -> knowledgeBaseService.updateKnowledgeBase("kb_1", "New", null, null, null, null, "alice", null));
    }

    // ---- deleteKnowledgeBase ----

    @Test
    void deleteKnowledgeBase_success_byOwner_noFiles() {
        KnowledgeBase kb = makeKb("kb_1", "TestKB", regularUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId("kb_1")).thenReturn(List.of());

        knowledgeBaseService.deleteKnowledgeBase("kb_1", "alice", null);

        verify(knowledgeBaseRepository).delete(kb);
        verify(rbacService).deleteAllResourcePermissions("kb", "kb_1");
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void deleteKnowledgeBase_success_byAdmin_sendsNotification() {
        KnowledgeBase kb = makeKb("kb_1", "AliceKB", regularUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("admin")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId("kb_1")).thenReturn(List.of());

        knowledgeBaseService.deleteKnowledgeBase("kb_1", "admin", null);

        verify(notificationService).sendNotification(eq("alice"), eq("admin"), eq("DELETE_KB"), eq("kb_1"), eq("AliceKB"));
        verify(knowledgeBaseRepository).delete(kb);
    }

    @Test
    void deleteKnowledgeBase_notFound_throwsIllegalArgument() {
        when(knowledgeBaseRepository.findByKbId("kb_missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> knowledgeBaseService.deleteKnowledgeBase("kb_missing", "alice", null));
    }

    @Test
    void deleteKnowledgeBase_unauthorized_throwsSecurityException() {
        KnowledgeBase kb = makeKb("kb_1", "BobKB", otherUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());

        assertThrows(SecurityException.class,
            () -> knowledgeBaseService.deleteKnowledgeBase("kb_1", "alice", null));
    }

    // ---- getKnowledgeBaseDetail ----

    @Test
    void getKnowledgeBaseDetail_success_asOwner() {
        KnowledgeBase kb = makeKb("kb_1", "TestKB", regularUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId("kb_1")).thenReturn(List.of());

        Map<String, Object> result = knowledgeBaseService.getKnowledgeBaseDetail("kb_1", "alice", null);

        assertEquals("kb_1", result.get("kbId"));
        assertEquals("TestKB", result.get("name"));
        assertEquals("alice", result.get("createdBy"));
    }

    @Test
    void getKnowledgeBaseDetail_notFound_throwsIllegalArgument() {
        when(knowledgeBaseRepository.findByKbId("kb_missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> knowledgeBaseService.getKnowledgeBaseDetail("kb_missing", "alice", null));
    }

    @Test
    void getKnowledgeBaseDetail_unauthorized_throwsSecurityException() {
        KnowledgeBase kb = makeKb("kb_1", "BobKB", otherUser, false, "org_other");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());
        when(rbacService.hasResourcePermission(anyString(), anyString(), anyString(), anyString(),
            any(), any(), anyBoolean())).thenReturn(false);

        assertThrows(SecurityException.class,
            () -> knowledgeBaseService.getKnowledgeBaseDetail("kb_1", "alice", null));
    }

    @Test
    void getKnowledgeBaseDetail_admin_seesAnyKb() {
        KnowledgeBase kb = makeKb("kb_1", "SomeKB", regularUser, false, "private_org");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("admin")).thenReturn(List.of());
        when(fileUploadRepository.findByKbId("kb_1")).thenReturn(List.of());

        Map<String, Object> result = knowledgeBaseService.getKnowledgeBaseDetail("kb_1", "admin", null);

        assertEquals("kb_1", result.get("kbId"));
    }

    // ---- getKnowledgeBaseDocuments ----

    @Test
    void getKnowledgeBaseDocuments_notFound_throwsIllegalArgument() {
        when(knowledgeBaseRepository.findByKbId("kb_missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> knowledgeBaseService.getKnowledgeBaseDocuments("kb_missing", "alice", null));
    }

    @Test
    void getKnowledgeBaseDocuments_owner_returnsFiles() {
        KnowledgeBase kb = makeKb("kb_1", "KB1", regularUser, false, "org1");
        when(knowledgeBaseRepository.findByKbId("kb_1")).thenReturn(Optional.of(kb));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("alice")).thenReturn(List.of());

        com.smart.kf.model.FileUpload f = new com.smart.kf.model.FileUpload();
        f.setFileMd5("md5_1");
        f.setFileName("test.pdf");
        f.setUserId("1");
        f.setTotalSize(512L);
        f.setStatus(1);
        f.setKbId("kb_1");
        when(fileUploadRepository.findByKbId("kb_1")).thenReturn(List.of(f));

        List<Map<String, Object>> result = knowledgeBaseService.getKnowledgeBaseDocuments("kb_1", "alice", null);

        assertEquals(1, result.size());
        assertEquals("md5_1", result.get(0).get("fileMd5"));
    }

    // ---- Helper ----

    private KnowledgeBase makeKb(String kbId, String name, User owner, boolean isPublic, String orgTag) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbId(kbId);
        kb.setName(name);
        kb.setCreatedBy(owner);
        kb.setPublic(isPublic);
        kb.setOrgTag(orgTag);
        return kb;
    }
}
