package com.nice1st.Hierarchy_Cache.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

@DataJpaTest
class HierarchyHierarchyGroupRepositoryTest {

    @Autowired
    private HierarchyGroupRepository hierarchyGroupRepository;

    @Test
    void getAllTest() {
        List<HierarchyGroup> allHierarchyGroups = hierarchyGroupRepository.findAll();

        assertThat(allHierarchyGroups).hasSize(30000);
    }
}