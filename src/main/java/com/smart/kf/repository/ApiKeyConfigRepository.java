package com.smart.kf.repository;

import com.smart.kf.model.ApiKeyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyConfigRepository extends JpaRepository<ApiKeyConfig, Long> {

    /** 查找当前激活的配置 */
    Optional<ApiKeyConfig> findByActiveTrue();

    /** 根据提供商查询 */
    List<ApiKeyConfig> findByProvider(String provider);

    /** 将所有配置设置为未激活 */
    @Modifying
    @Query("UPDATE ApiKeyConfig a SET a.active = false")
    void deactivateAll();
}
