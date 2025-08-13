package com.nice1st.Hierarchy_Cache.service;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.nice1st.Hierarchy_Cache.cache.CacheService;

@SpringBootTest
class HierarchyGroupReadServiceTest {

    @Autowired
    HierarchyGroupReadService hierarchyGroupReadService;

    @Autowired
    CacheService cacheService;

    final String TENANT_ID = "tenant1";
    final String ROOT_ID = "e1757bb8-8568-4135-8e67-778361b3329d";
    final String DEPTH1_ID1 = "f28ff6e7-e556-4911-8271-587f6e9a2c8b";
    final String DEPTH1_ID2 = "ef9ae61b-eb88-473f-8c3b-3f7b5b2afa8e";
    final String DEPTH2_ID1 = "c851169f-1fa6-458d-9e5e-a366c8e8d187";

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