package com.smart.kf.repository;

import com.smart.kf.model.AdminOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {

    /** 按操作类型分页查询审计记录（按时间倒序） */
    Page<AdminOperationLog> findByOperationOrderByCreatedAtDesc(String operation, Pageable pageable);

    /** 按管理员用户名分页查询审计记录（按时间倒序） */
    Page<AdminOperationLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
}
