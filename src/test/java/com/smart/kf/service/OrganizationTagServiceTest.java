package com.smart.kf.service;

import com.smart.kf.exception.CustomException;
import com.smart.kf.model.FileUpload;
import com.smart.kf.model.OrganizationTag;
import com.smart.kf.model.OrganizationTagI18n;
import com.smart.kf.model.User;
import com.smart.kf.repository.FileUploadRepository;
import com.smart.kf.repository.LoginRecordRepository;
import com.smart.kf.repository.OrganizationTagI18nRepository;
import com.smart.kf.repository.OrganizationTagRepository;
import com.smart.kf.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 组织标签相关业务方法的单元测试
 */
@ExtendWith(MockitoExtension.class)
class OrganizationTagServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationTagRepository organizationTagRepository;
    @Mock
    private OrganizationTagI18nRepository organizationTagI18nRepository;
    @Mock
    private FileUploadRepository fileUploadRepository;
    @Mock
    private OrgTagCacheService orgTagCacheService;
    @Mock
    private LoginRecordRepository loginRecordRepository;
    @Mock
    private I18nTranslationService i18nTranslationService;

    @InjectMocks
    private UserService userService;

    private User adminUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRole(User.Role.ADMIN);

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUsername("alice");
        normalUser.setRole(User.Role.USER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createOrganizationTag
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class CreateOrganizationTag {

        @Test
        void nullTagId_throwsBadRequest() {
            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag(null, "名称", null, null, "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("Tag ID"));
            verifyNoInteractions(organizationTagRepository);
        }

        @Test
        void blankTagId_throwsBadRequest() {
            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("   ", "名称", null, null, "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            verifyNoInteractions(organizationTagRepository);
        }

        @Test
        void nullName_throwsBadRequest() {
            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("my-tag", null, null, null, "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertTrue(ex.getMessage().contains("name"));
            verifyNoInteractions(organizationTagRepository);
        }

        @Test
        void blankName_throwsBadRequest() {
            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("my-tag", "  ", null, null, "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            verifyNoInteractions(organizationTagRepository);
        }

        @Test
        void nonExistentCreator_throwsNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("my-tag", "名称", null, null, "ghost"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        void creatorNotAdmin_throwsForbidden() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(normalUser));

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("my-tag", "名称", null, null, "alice"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }

        @Test
        void duplicateTagId_throwsBadRequest() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.existsByTagId("existing")).thenReturn(true);

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("existing", "名称", null, null, "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        void parentTagNotFound_throwsNotFound() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.existsByTagId("my-tag")).thenReturn(false);
            when(organizationTagRepository.findByTagId("missing-parent")).thenReturn(Optional.empty());

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.createOrganizationTag("my-tag", "名称", null, "missing-parent", "admin"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        void success_noParent() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.existsByTagId("dept-a")).thenReturn(false);
            OrganizationTag saved = new OrganizationTag();
            saved.setTagId("dept-a");
            when(organizationTagRepository.save(any(OrganizationTag.class))).thenReturn(saved);

            OrganizationTag result = userService.createOrganizationTag("dept-a", "部门A", "描述", null, "admin");

            assertNotNull(result);
            assertEquals("dept-a", result.getTagId());
            verify(i18nTranslationService).translateOrgTagAsync("dept-a", "部门A", "描述");
            verify(orgTagCacheService).invalidateAllEffectiveTagsCache();
        }

        @Test
        void success_withParent() {
            OrganizationTag parent = new OrganizationTag();
            parent.setTagId("root");

            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.existsByTagId("child")).thenReturn(false);
            when(organizationTagRepository.findByTagId("root")).thenReturn(Optional.of(parent));
            OrganizationTag saved = new OrganizationTag();
            saved.setTagId("child");
            when(organizationTagRepository.save(any())).thenReturn(saved);

            OrganizationTag result = userService.createOrganizationTag("child", "子节点", null, "root", "admin");

            assertNotNull(result);
            ArgumentCaptor<OrganizationTag> captor = ArgumentCaptor.forClass(OrganizationTag.class);
            verify(organizationTagRepository).save(captor.capture());
            assertEquals("root", captor.getValue().getParentTag());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateOrganizationTag
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class UpdateOrganizationTag {

        private OrganizationTag existingTag;

        @BeforeEach
        void setUp() {
            existingTag = new OrganizationTag();
            existingTag.setTagId("dept-a");
            existingTag.setName("旧名称");
        }

        @Test
        void selfParent_throwsBadRequest() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(existingTag));

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.updateOrganizationTag("dept-a", "新名称", null, "dept-a", "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        void parentTagNotFound_throwsNotFound() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(existingTag));
            when(organizationTagRepository.findByTagId("ghost-parent")).thenReturn(Optional.empty());

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.updateOrganizationTag("dept-a", "新名称", null, "ghost-parent", "admin"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        void cycleDetected_throwsBadRequest() {
            // dept-a → dept-b; trying to set dept-a's parent to dept-b would create: dept-b → dept-a (cycle)
            OrganizationTag deptB = new OrganizationTag();
            deptB.setTagId("dept-b");
            deptB.setParentTag("dept-a"); // dept-b's parent is dept-a

            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(existingTag));
            when(organizationTagRepository.findByTagId("dept-b")).thenReturn(Optional.of(deptB));

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.updateOrganizationTag("dept-a", null, null, "dept-b", "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        void emptyParentTagNormalizedToNull() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(existingTag));
            when(organizationTagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            userService.updateOrganizationTag("dept-a", null, null, "", "admin");

            ArgumentCaptor<OrganizationTag> captor = ArgumentCaptor.forClass(OrganizationTag.class);
            verify(organizationTagRepository).save(captor.capture());
            assertNull(captor.getValue().getParentTag(), "empty parentTag should be normalized to null");
        }

        @Test
        void success_nameAndDescriptionUpdated() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(existingTag));
            when(organizationTagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OrganizationTag result = userService.updateOrganizationTag("dept-a", "新名称", "新描述", null, "admin");

            assertEquals("新名称", result.getName());
            assertEquals("新描述", result.getDescription());
            verify(i18nTranslationService).retranslateOrgTagAsync(anyString(), anyString(), any());
            verify(orgTagCacheService).invalidateAllEffectiveTagsCache();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteOrganizationTag
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class DeleteOrganizationTag {

        private OrganizationTag customTag;

        @BeforeEach
        void setUp() {
            customTag = new OrganizationTag();
            customTag.setTagId("custom");
            customTag.setName("自定义");
        }

        @Test
        void defaultTagProtected_throwsBadRequest() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            OrganizationTag def = new OrganizationTag();
            def.setTagId("default");
            when(organizationTagRepository.findByTagId("default")).thenReturn(Optional.of(def));

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.deleteOrganizationTag("default", "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        void adminTagProtected_throwsBadRequest() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            OrganizationTag adm = new OrganizationTag();
            adm.setTagId("admin");
            when(organizationTagRepository.findByTagId("admin")).thenReturn(Optional.of(adm));

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.deleteOrganizationTag("admin", "admin"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }

        @Test
        void privateTag_canBeDeleted() {
            // PRIVATE_ prefix is no longer system-protected; admin can delete these like any custom tag
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            OrganizationTag priv = new OrganizationTag();
            priv.setTagId("PRIVATE_alice");
            when(organizationTagRepository.findByTagId("PRIVATE_alice")).thenReturn(Optional.of(priv));
            when(organizationTagRepository.findByParentTag("PRIVATE_alice")).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(fileUploadRepository.findByOrgTag("PRIVATE_alice")).thenReturn(Collections.emptyList());

            Map<String, Object> result = userService.deleteOrganizationTag("PRIVATE_alice", "admin");

            assertEquals(0, result.get("affectedUserCount"));
            verify(organizationTagRepository).delete(priv);
        }

        @Test
        void tagNotFound_throwsNotFound() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("ghost")).thenReturn(Optional.empty());

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.deleteOrganizationTag("ghost", "admin"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        void success_noAffectedUsersOrDocs() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("custom")).thenReturn(Optional.of(customTag));
            when(organizationTagRepository.findByParentTag("custom")).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(fileUploadRepository.findByOrgTag("custom")).thenReturn(Collections.emptyList());

            Map<String, Object> result = userService.deleteOrganizationTag("custom", "admin");

            assertEquals(0, result.get("affectedUserCount"));
            assertEquals(0, result.get("affectedDocumentCount"));
            assertEquals(0, result.get("reassignedChildrenCount"));
            verify(organizationTagRepository).delete(customTag);
            verify(orgTagCacheService).invalidateAllEffectiveTagsCache();
        }

        @Test
        void success_childrenReassignedToGrandparent() {
            customTag.setParentTag("root");

            OrganizationTag child = new OrganizationTag();
            child.setTagId("child");
            child.setParentTag("custom");

            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("custom")).thenReturn(Optional.of(customTag));
            when(organizationTagRepository.findByParentTag("custom")).thenReturn(List.of(child));
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(fileUploadRepository.findByOrgTag("custom")).thenReturn(Collections.emptyList());

            Map<String, Object> result = userService.deleteOrganizationTag("custom", "admin");

            assertEquals(1, result.get("reassignedChildrenCount"));
            ArgumentCaptor<OrganizationTag> captor = ArgumentCaptor.forClass(OrganizationTag.class);
            // save called for child reassignment
            verify(organizationTagRepository, atLeastOnce()).save(captor.capture());
            captor.getAllValues().stream()
                    .filter(t -> "child".equals(t.getTagId()))
                    .findFirst()
                    .ifPresent(t -> assertEquals("root", t.getParentTag()));
        }

        @Test
        void success_affectedUserReassignedToDefault() {
            User affectedUser = new User();
            affectedUser.setUsername("bob");
            affectedUser.setOrgTags("custom,other");
            affectedUser.setPrimaryOrg("custom");

            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("custom")).thenReturn(Optional.of(customTag));
            when(organizationTagRepository.findByParentTag("custom")).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(List.of(affectedUser));
            when(fileUploadRepository.findByOrgTag("custom")).thenReturn(Collections.emptyList());

            Map<String, Object> result = userService.deleteOrganizationTag("custom", "admin");

            assertEquals(1, result.get("affectedUserCount"));
            assertEquals("default", affectedUser.getPrimaryOrg());
            assertTrue(affectedUser.getOrgTags().contains("default"));
            assertFalse(affectedUser.getOrgTags().contains("custom"));
        }

        @Test
        void success_affectedDocumentsReassignedToDefault() {
            FileUpload doc = new FileUpload();
            doc.setOrgTag("custom");

            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(organizationTagRepository.findByTagId("custom")).thenReturn(Optional.of(customTag));
            when(organizationTagRepository.findByParentTag("custom")).thenReturn(Collections.emptyList());
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(fileUploadRepository.findByOrgTag("custom")).thenReturn(List.of(doc));

            Map<String, Object> result = userService.deleteOrganizationTag("custom", "admin");

            assertEquals(1, result.get("affectedDocumentCount"));
            assertEquals("default", doc.getOrgTag());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // assignOrgTagsToUser
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class AssignOrgTagsToUser {

        @Test
        void tagNotFound_throwsNotFound() {
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
            when(organizationTagRepository.existsByTagId("nonexistent")).thenReturn(false);

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.assignOrgTagsToUser(2L, List.of("nonexistent"), "admin"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        void success_assignReplacesPreviousTags() {
            // assignOrgTagsToUser does a full replacement; previous tags (including PRIVATE_) are dropped
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            normalUser.setOrgTags("PRIVATE_alice,other");
            when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
            when(organizationTagRepository.existsByTagId("dept-a")).thenReturn(true);

            userService.assignOrgTagsToUser(2L, List.of("dept-a"), "admin");

            assertTrue(normalUser.getOrgTags().contains("dept-a"));
            assertFalse(normalUser.getOrgTags().contains("PRIVATE_alice"),
                    "previous tags are fully replaced by the assigned list");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOrganizationTagTree
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class GetOrganizationTagTree {

        @Test
        void emptyTree_returnsEmpty() {
            when(organizationTagRepository.findByParentTag(null)).thenReturn(Collections.emptyList());

            List<Map<String, Object>> tree = userService.getOrganizationTagTree();

            assertTrue(tree.isEmpty());
        }

        @Test
        void singleRootNoChildren() {
            OrganizationTag root = new OrganizationTag();
            root.setTagId("root");
            root.setName("根节点");

            when(organizationTagRepository.findByParentTag(null)).thenReturn(List.of(root));
            when(organizationTagRepository.findByParentTag("root")).thenReturn(Collections.emptyList());

            List<Map<String, Object>> tree = userService.getOrganizationTagTree();

            assertEquals(1, tree.size());
            assertEquals("root", tree.get(0).get("tagId"));
            assertEquals("根节点", tree.get(0).get("name"));
            assertFalse(tree.get(0).containsKey("children"));
        }

        @Test
        void parentChildStructure_buildsCorrectly() {
            OrganizationTag root = new OrganizationTag();
            root.setTagId("root");
            root.setName("根");

            OrganizationTag child = new OrganizationTag();
            child.setTagId("child");
            child.setName("子");
            child.setParentTag("root");

            when(organizationTagRepository.findByParentTag(null)).thenReturn(List.of(root));
            when(organizationTagRepository.findByParentTag("root")).thenReturn(List.of(child));
            when(organizationTagRepository.findByParentTag("child")).thenReturn(Collections.emptyList());

            List<Map<String, Object>> tree = userService.getOrganizationTagTree();

            assertEquals(1, tree.size());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get(0).get("children");
            assertNotNull(children);
            assertEquals(1, children.size());
            assertEquals("child", children.get(0).get("tagId"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserOrgTags
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class GetUserOrgTags {

        @Test
        void userNotFound_throwsNotFound() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            CustomException ex = assertThrows(CustomException.class,
                    () -> userService.getUserOrgTags("ghost"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }

        @Test
        void cacheMiss_loadsFromDb() {
            normalUser.setOrgTags("dept-a,default");
            normalUser.setPrimaryOrg("dept-a");

            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(normalUser));
            when(orgTagCacheService.getUserOrgTags("alice")).thenReturn(null);
            when(orgTagCacheService.getUserPrimaryOrg("alice")).thenReturn(null);

            OrganizationTag deptA = new OrganizationTag();
            deptA.setTagId("dept-a");
            deptA.setName("部门A");

            OrganizationTag def = new OrganizationTag();
            def.setTagId("default");
            def.setName("默认组织");

            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(deptA));
            when(organizationTagRepository.findByTagId("default")).thenReturn(Optional.of(def));

            Map<String, Object> result = userService.getUserOrgTags("alice");

            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) result.get("orgTags");
            assertEquals(2, tags.size());
            assertEquals("dept-a", result.get("primaryOrg"));
        }

        @Test
        void cacheHit_returnsCachedData() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(normalUser));
            when(orgTagCacheService.getUserOrgTags("alice")).thenReturn(List.of("dept-a"));
            when(orgTagCacheService.getUserPrimaryOrg("alice")).thenReturn("dept-a");

            OrganizationTag deptA = new OrganizationTag();
            deptA.setTagId("dept-a");
            deptA.setName("部门A");
            when(organizationTagRepository.findByTagId("dept-a")).thenReturn(Optional.of(deptA));

            Map<String, Object> result = userService.getUserOrgTags("alice");

            assertEquals("dept-a", result.get("primaryOrg"));
            // Repository was called only for tag details, not for the tag list
            verify(orgTagCacheService).getUserOrgTags("alice");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // upsertOrganizationTagI18n
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class UpsertOrganizationTagI18n {

        @Test
        void createsNewRecord_whenNotExists() {
            when(organizationTagI18nRepository.findByTagIdAndLang("dept-a", "en-US"))
                    .thenReturn(Optional.empty());
            when(organizationTagI18nRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OrganizationTagI18n result = userService.upsertOrganizationTagI18n("dept-a", "en-US", "Dept A", "Description");

            assertEquals("dept-a", result.getTagId());
            assertEquals("en-US", result.getLang());
            assertEquals("Dept A", result.getName());
        }

        @Test
        void updatesExistingRecord() {
            OrganizationTagI18n existing = new OrganizationTagI18n();
            existing.setTagId("dept-a");
            existing.setLang("en-US");
            existing.setName("Old Name");

            when(organizationTagI18nRepository.findByTagIdAndLang("dept-a", "en-US"))
                    .thenReturn(Optional.of(existing));
            when(organizationTagI18nRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OrganizationTagI18n result = userService.upsertOrganizationTagI18n("dept-a", "en-US", "New Name", null);

            assertEquals("New Name", result.getName());
        }
    }
}
