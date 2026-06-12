package com.smart.kf.repository;

import com.smart.kf.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    /**
     * 查询用户拥有的所有角色
     */
    @SuppressWarnings("unused")
    @Query("SELECT r FROM Role r JOIN r.permissions p WHERE p.permCode = :permCode")
    List<Role> findByPermissionCode(@Param("permCode") String permCode);
}
