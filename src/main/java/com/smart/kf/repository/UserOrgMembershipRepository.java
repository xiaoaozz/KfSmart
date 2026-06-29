package com.smart.kf.repository;

import com.smart.kf.model.UserOrgMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrgMembershipRepository extends JpaRepository<UserOrgMembership, Long> {

    List<UserOrgMembership> findByUserId(Long userId);

    List<UserOrgMembership> findByOrgTag(String orgTag);

    Optional<UserOrgMembership> findByUserIdAndOrgTag(Long userId, String orgTag);

    boolean existsByUserIdAndOrgTag(Long userId, String orgTag);

    Optional<UserOrgMembership> findByUserIdAndIsPrimaryTrue(Long userId);

    void deleteByUserIdAndOrgTag(Long userId, String orgTag);
}
