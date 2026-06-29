package com.smart.kf.repository;

import com.smart.kf.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermCode(String permCode);

    boolean existsByPermCode(String permCode);

    @SuppressWarnings("unused")
    List<Permission> findByResourceType(String resourceType);

    /**
     * 查询指定角色列表下的所有权限编码（去重）
     */
    @SuppressWarnings("unused")
    @Query("SELECT DISTINCT p.permCode FROM Permission p JOIN p.roles r WHERE r.roleCode IN :roleCodes")
    Set<String> findPermCodesByRoleCodes(@Param("roleCodes") List<String> roleCodes);

    /**
     * 通过用户ID查询其所有权限编码（跨 user_roles 和 role_permissions 联表）
     */
    @SuppressWarnings("unused")
    @Query("SELECT DISTINCT p.permCode FROM com.smart.kf.model.User u JOIN u.roles r JOIN r.permissions p WHERE u.id = :userId")
    Set<String> findPermCodesByUserId(@Param("userId") Long userId);
}
