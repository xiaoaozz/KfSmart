package com.smart.kf.repository;

import com.smart.kf.model.ResourcePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, Long> {

    /**
     * 查询某资源的所有权限记录
     */
    List<ResourcePermission> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    /**
     * 查询某被授权对象的所有权限记录
     */
    @SuppressWarnings("unused")
    List<ResourcePermission> findByGranteeTypeAndGranteeId(String granteeType, String granteeId);

    /**
     * 查询某被授权对象对某资源的权限
     */
    Optional<ResourcePermission> findByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
        String resourceType, String resourceId, String granteeType, String granteeId);

    /**
     * 检查被授权对象是否对某资源拥有指定权限
     */
    @SuppressWarnings("unused")
    boolean existsByResourceTypeAndResourceIdAndGranteeTypeAndGranteeIdAndPermission(
        String resourceType, String resourceId, String granteeType, String granteeId, String permission);

    /**
     * 查询某被授权对象可访问的所有资源ID（指定资源类型和最低权限）
     * 用于数据行级过滤
     */
    @Query("SELECT rp.resourceId FROM ResourcePermission rp "
         + "WHERE rp.resourceType = :resourceType "
         + "AND rp.granteeType = :granteeType "
         + "AND rp.granteeId IN :granteeIds "
         + "AND rp.permission IN :permissions")
    List<String> findAccessibleResourceIds(
        @Param("resourceType") String resourceType,
        @Param("granteeType") String granteeType,
        @Param("granteeIds") List<String> granteeIds,
        @Param("permissions") List<String> permissions);

    /**
     * 删除某资源的所有权限记录（删除资源时调用）
     */
    void deleteByResourceTypeAndResourceId(String resourceType, String resourceId);

    /**
     * 删除特定授权记录
     */
    void deleteByResourceTypeAndResourceIdAndGranteeTypeAndGranteeId(
        String resourceType, String resourceId, String granteeType, String granteeId);
}
