package com.smart.kf.repository;

import com.smart.kf.model.OrganizationTag;
import com.smart.kf.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationTagRepository extends JpaRepository<OrganizationTag, String> {
    Optional<OrganizationTag> findByTagId(String tagId);
    List<OrganizationTag> findByParentTag(String parentTag);
    boolean existsByTagId(String tagId);
    List<OrganizationTag> findByCreatedBy(User createdBy);
} 