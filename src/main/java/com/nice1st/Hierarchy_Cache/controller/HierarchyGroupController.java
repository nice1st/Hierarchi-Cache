package com.nice1st.Hierarchy_Cache.controller;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nice1st.Hierarchy_Cache.service.HierarchyGroupCommandService;
import com.nice1st.Hierarchy_Cache.service.HierarchyGroupReadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class HierarchyGroupController {

    private final HierarchyGroupCommandService commandService;
    private final HierarchyGroupReadService readService;

    @GetMapping("/{groupId}/children")
    public Set<String> getChildren(@PathVariable String groupId) {
        return readService.read(groupId);
    }
}
