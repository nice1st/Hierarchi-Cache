package com.nice1st.Hierarchy_Cache.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroupEvent;

@Repository
public interface HierarchyGroupEventRepository extends JpaRepository<HierarchyGroupEvent, String> {

}
