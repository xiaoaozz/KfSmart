package com.smart.kf.service;

import com.smart.kf.model.ResourcePermission;
import com.smart.kf.model.User;
import com.smart.kf.repository.ResourcePermissionRepository;
import com.smart.kf.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RbacServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResourcePermissionRepository resourcePermissionRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RbacService rbacService;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRole(User.Role.ADMIN);
        adminUser.setRoles(new HashSet<>());
        adminUser.setOrgTags(null);

        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setUsername("alice");
        regularUser.setRole(User.Role.USER);
        regularUser.setRoles(new HashSet<>());
        regularUser.setOrgTags(null);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // hasPermission

    @Test
    void hasPermission_nullUsername_returnsFalse() {
        assertFalse(rbacService.hasPermission(null, "kb:read"));
    }

    @Test
    void hasPermission_nullPermCode_returnsFalse() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        assertFalse(rbacService.hasPermission("alice", null));
    }

    @Test
    void hasPermission_cacheHit_hasPermission_returnsTrue() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read,kb:write,chat:use");

        assertTrue(rbacService.hasPermission("alice", "kb:read"));
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void hasPermission_cacheHit_noPermission_returnsFalse() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read,chat:use");

        assertFalse(rbacService.hasPermission("alice", "system:admin"));
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void hasPermission_cacheMiss_adminUser_hasSystemAdmin() {
        when(valueOps.get("rbac:user_perms:admin")).thenReturn(null);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        assertTrue(rbacService.hasPermission("admin", "system:admin"));
    }

    @Test
    void hasPermission_cacheMiss_regularUser_hasKbRead() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));

        assertTrue(rbacService.hasPermission("alice", "kb:read"));
    }

    @Test
    void hasPermission_userNotFound_returnsFalse() {
        when(valueOps.get("rbac:user_perms:ghost")).thenReturn(null);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertFalse(rbacService.hasPermission("ghost", "kb:read"));
    }

    // getUserPermissions - cache flow

    @Test
    void getUserPermissions_cacheMiss_writesBackToRedis() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));

        rbacService.getUserPermissions("alice");

        verify(valueOps).set(eq("rbac:user_perms:alice"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    // evictUserPermissionCache

    @Test
    void evictUserPermissionCache_deletesRedisKey() {
        rbacService.evictUserPermissionCache("alice");
        verify(redisTemplate).delete("rbac:user_perms:alice");
    }

    // hasResourcePermission

    @Test
    void hasResourcePermission_nullUsername_returnsFalse() {
        assertFalse(rbacService.hasResourcePermission(null, "kb", "kb_1", "read", null, null, false));
    }

    @Test
    void hasResourcePermission_systemAdmin_returnsTrue() {
        when(valueOps.get("rbac:user_perms:admin")).thenReturn("system:admin,kb:admin");

        assertTrue(rbacService.hasResourcePermission("admin", "kb", "kb_1", "read", null, null, false));
    }

    @Test
    void hasResourcePermission_publicResource_read_returnsTrue() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read");

        assertTrue(rbacService.hasResourcePermission("alice", "kb", "kb_1", "read", "other", "org1", true));
    }

    @Test
    void hasResourcePermission_publicResource_write_doesNotAutoAllow() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(resourcePermissionRepository.findByResourceTypeAndResourceId("kb", "kb_1"))
            .thenReturn(List.of());

        assertFalse(rbacService.hasResourcePermission("alice", "kb", "kb_1", "write", "other", "org1", true));
    }

    @Test
    void hasResourcePermission_isOwner_returnsTrue() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read");

        assertTrue(rbacService.hasResourcePermission("alice", "kb", "kb_1", "write", "alice", "org1", false));
    }

    @Test
    void hasResourcePermission_directUserGrant_returnsTrue() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));

        ResourcePermission grant = new ResourcePermission();
        grant.setResourceType("kb");
        grant.setResourceId("kb_1");
        grant.setGranteeType("user");
        grant.setGranteeId("2"); // regularUser.id = 2
        grant.setPermission("write");

        when(resourcePermissionRepository.findByResourceTypeAndResourceId("kb", "kb_1"))
            .thenReturn(List.of(grant));

        assertTrue(rbacService.hasResourcePermission("alice", "kb", "kb_1", "read", "other", null, false));
    }

    @Test
    void hasResourcePermission_adminPermImpliesRead_returnsTrue() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));

        ResourcePermission grant = new ResourcePermission();
        grant.setResourceType("kb");
        grant.setResourceId("kb_1");
        grant.setGranteeType("user");
        grant.setGranteeId("2");
        grant.setPermission("admin"); // admin implies read

        when(resourcePermissionRepository.findByResourceTypeAndResourceId("kb", "kb_1"))
            .thenReturn(List.of(grant));

        assertTrue(rbacService.hasResourcePermission("alice", "kb", "kb_1", "read", "other", null, false));
    }

    @Test
    void hasResourcePermission_noGrant_returnsFalse() {
        when(valueOps.get("rbac:user_perms:alice")).thenReturn("kb:read");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(regularUser));
        when(resourcePermissionRepository.findByResourceTypeAndResourceId("kb", "kb_1"))
            .thenReturn(List.of());

        assertFalse(rbacService.hasResourcePermission("alice", "kb", "kb_1", "write", "other", null, false));
    }

    // grantResourcePermission

    @Test
    void grantResourcePermission_newGrant_savesNewRecord() {
        when(resourcePermissionRepository.findByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
            "kb", "kb_1", "user", "2")).thenReturn(Optional.empty());
        when(resourcePermissionRepository.save(any(ResourcePermission.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        ResourcePermission result = rbacService.grantResourcePermission("kb", "kb_1", "user", "2", "read", 1L);

        assertEquals("read", result.getPermission());
        assertEquals("user", result.getGranteeType());
        verify(resourcePermissionRepository).save(any());
    }

    @Test
    void grantResourcePermission_existingGrant_updatesPermission() {
        ResourcePermission existing = new ResourcePermission();
        existing.setPermission("read");
        when(resourcePermissionRepository.findByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
            "kb", "kb_1", "user", "2")).thenReturn(Optional.of(existing));
        when(resourcePermissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rbacService.grantResourcePermission("kb", "kb_1", "user", "2", "write", 1L);

        assertEquals("write", existing.getPermission());
    }

    // revokeResourcePermission

    @Test
    void revokeResourcePermission_callsDelete() {
        rbacService.revokeResourcePermission("kb", "kb_1", "user", "2");

        verify(resourcePermissionRepository).deleteByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
            "kb", "kb_1", "user", "2");
    }

    // deleteAllResourcePermissions

    @Test
    void deleteAllResourcePermissions_callsDelete() {
        rbacService.deleteAllResourcePermissions("kb", "kb_1");

        verify(resourcePermissionRepository).deleteByResourceTypeAndResourceId("kb", "kb_1");
    }

    // listResourcePermissions

    @Test
    void listResourcePermissions_delegatesToRepository() {
        ResourcePermission rp = new ResourcePermission();
        when(resourcePermissionRepository.findByResourceTypeAndResourceId("kb", "kb_1"))
            .thenReturn(List.of(rp));

        List<ResourcePermission> result = rbacService.listResourcePermissions("kb", "kb_1");

        assertEquals(1, result.size());
    }
}
