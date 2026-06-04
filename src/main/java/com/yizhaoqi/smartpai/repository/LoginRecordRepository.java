package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.LoginRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginRecordRepository extends JpaRepository<LoginRecord, Long> {

    /**
     * 根据用户名查询登录记录（分页，按登录时间倒序）
     */
    Page<LoginRecord> findByUsernameOrderByLoginTimeDesc(String username, Pageable pageable);

    /**
     * 统计用户登录次数
     */
    long countByUserId(Long userId);

    /**
     * 统计用户成功登录次数
     */
    long countByUserIdAndStatus(Long userId, String status);

    /**
     * 查询用户最近 N 条登录记录
     */
    List<LoginRecord> findTop10ByUserIdOrderByLoginTimeDesc(Long userId);
}