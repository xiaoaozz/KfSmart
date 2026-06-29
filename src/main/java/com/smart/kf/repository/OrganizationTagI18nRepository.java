package com.smart.kf.repository;

import com.smart.kf.model.OrganizationTagI18n;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationTagI18nRepository extends JpaRepository<OrganizationTagI18n, Long> {

    Optional<OrganizationTagI18n> findByTagIdAndLang(String tagId, String lang);

    List<OrganizationTagI18n> findByTagId(String tagId);

    void deleteByTagId(String tagId);
}
