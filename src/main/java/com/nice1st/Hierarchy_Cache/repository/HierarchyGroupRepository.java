package com.nice1st.Hierarchy_Cache.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

@Repository
public interface HierarchyGroupRepository extends JpaRepository<HierarchyGroup, String> {

	HierarchyGroup findByTenantIdAndParentIsNull(String tenantId);

	List<HierarchyGroup> findByTenantId(String tenantId);

	List<HierarchyGroup> findByParent(HierarchyGroup parent);

	int countByTenantId(String tenantId);
}
