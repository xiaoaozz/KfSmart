package com.smart.kf.repository;

import com.smart.kf.model.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
    Optional<FileUpload> findByFileMd5(String fileMd5);

    Optional<FileUpload> findByFileMd5AndUserId(String fileMd5, String userId);

    Optional<FileUpload> findByFileMd5AndIsPublicTrue(String fileMd5);

    Optional<FileUpload> findByFileNameAndIsPublicTrue(String fileName);
    

    void deleteByFileMd5(String fileMd5);
    

    /**
     * 查询用户自己的文件和公开文件
     */
    List<FileUpload> findByUserIdOrIsPublicTrue(String userId);
    
    /**
     * 查询用户可访问的所有文件（考虑层级标签权限）
     * 包括：1. 用户自己上传的文件
     *      2. 公开的文件
     *      3. 用户所属组织的文件（包含层级关系）
     *
     * @param userId 用户ID
     * @param orgTagList 用户有效的组织标签列表（包含层级结构）
     * @return 用户可访问的文件列表
     */
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId OR f.isPublic = true OR (f.orgTag IN :orgTagList AND f.isPublic = false)")
    List<FileUpload> findAccessibleFilesWithTags(@Param("userId") String userId, @Param("orgTagList") List<String> orgTagList);
    

    /**
     * 查询用户自己上传的所有文件
     * 
     * @param userId 用户ID
     * @return 用户上传的文件列表
     */
    List<FileUpload> findByUserId(String userId);

    List<FileUpload> findByFileMd5In(List<String> md5List);

    // -------- 基于 ownerId (Long FK) 的新查询方法 --------

    Optional<FileUpload> findByFileMd5AndOwnerId(String fileMd5, Long ownerId);

    List<FileUpload> findByOwnerId(Long ownerId);

    List<FileUpload> findByOwnerIdOrIsPublicTrue(Long ownerId);

    @Query("SELECT f FROM FileUpload f WHERE f.ownerId = :ownerId OR f.isPublic = true OR (f.orgTag IN :orgTagList AND f.isPublic = false)")
    List<FileUpload> findAccessibleFilesByOwnerWithTags(@Param("ownerId") Long ownerId, @Param("orgTagList") List<String> orgTagList);

    @Query("SELECT f FROM FileUpload f WHERE f.ownerId = :ownerId AND LOWER(f.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<FileUpload> findByOwnerIdAndFileNameContainingIgnoreCase(@Param("ownerId") Long ownerId, @Param("keyword") String keyword);

    @Query("SELECT f FROM FileUpload f WHERE f.ownerId = :ownerId AND LOWER(f.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) AND f.kbId = :kbId")
    List<FileUpload> findByOwnerIdAndFileNameContainingIgnoreCaseAndKbId(
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("kbId") String kbId);

    /**
     * 查找指定组织标签的所有文件
     * 用于删除组织标签时重新分配文档归属
     */
    List<FileUpload> findByOrgTag(String orgTag);

    /**
     * 批量查找指定组织标签的所有文件
     * 用于筛选选项等批量查询场景，避免 N+1 查询
     */
    List<FileUpload> findByOrgTagIn(Collection<String> orgTags);

    /**
     * 查找指定知识库的所有文件
     * 用于按知识库检索文档
     */
    List<FileUpload> findByKbId(String kbId);

    /**
     * 批量查找指定知识库的所有文件
     * 用于筛选选项等批量查询场景，避免 N+1 查询
     */
    List<FileUpload> findByKbIdIn(Collection<String> kbIds);

    /**
     * 根据用户ID和文件名关键词模糊查询用户上传的文件
     *
     * @param userId  用户ID
     * @param keyword 文件名关键词（LIKE 匹配）
     * @return 匹配的文件列表
     */
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId AND LOWER(f.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<FileUpload> findByUserIdAndFileNameContainingIgnoreCase(@Param("userId") String userId, @Param("keyword") String keyword);

    /**
     * 根据用户ID、文件名关键词和知识库ID模糊查询
     */
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId AND LOWER(f.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) AND f.kbId = :kbId")
    List<FileUpload> findByUserIdAndFileNameContainingIgnoreCaseAndKbId(
            @Param("userId") String userId,
            @Param("keyword") String keyword,
            @Param("kbId") String kbId);
}
