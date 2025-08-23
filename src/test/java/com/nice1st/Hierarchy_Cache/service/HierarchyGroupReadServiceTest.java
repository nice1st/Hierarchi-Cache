package com.nice1st.Hierarchy_Cache.service;

import com.nice1st.Hierarchy_Cache.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HierarchyGroupReadServiceTest {

    @Autowired
    HierarchyGroupReadService hierarchyGroupReadService;

    @Autowired
    CacheService cacheService;

    final String ROOT_ID = "e1757bb8-8568-4135-8e67-778361b3329d";

    void assertion(Set<String> ids) {
        assertThat(ids).hasSizeGreaterThanOrEqualTo(10000);
    }

    @Test
    // @Disabled
    void recursiveIds() {
        Set<String> ids = hierarchyGroupReadService.recursiveIds(ROOT_ID);
        assertion(ids);
    }

    @Test
    void reBuild() {
        Set<String> ids = hierarchyGroupReadService.reBuild(ROOT_ID);
        assertion(ids);
    }

    @Test
    void cache() {
        Set<String> ids = hierarchyGroupReadService.read(ROOT_ID);
        ids.add(ROOT_ID);

        assertion(ids);
    }
}
