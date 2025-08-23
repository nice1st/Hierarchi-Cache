package com.nice1st.Hierarchy_Cache.cache.local;

import com.nice1st.Hierarchy_Cache.cache.CacheService;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

import java.util.*;

public class InMemoryCacheService implements CacheService {

    private static final String ROOT_GROUP = "ROOT_GROUP";

    private final Map<String, Set<String>> parentsByGroup = new HashMap<>();
    private final Map<String, Set<String>> childrenByGroup = new HashMap<>();
    private final Map<String, String> cursorByTenant = new HashMap<>();

    @Override
    public String getRootGroup() {
        return ROOT_GROUP;
    }

    @Override
    public String getCursor(String tenantId) {
        return cursorByTenant.getOrDefault(tenantId, "0");
    }

    @Override
    public void updateCursor(String tenantId, Long tsid) {
        cursorByTenant.put(tenantId, String.valueOf(tsid));
    }

    @Override
    public void initialize(String tenantId, Map<String, List<HierarchyGroup>> groupByParentId) {
        // clear tenant space: since we do not namespace by tenant in maps, clear all
        parentsByGroup.clear();
        childrenByGroup.clear();

        for (HierarchyGroup root : groupByParentId.getOrDefault(ROOT_GROUP, Collections.emptyList())) {
            buildRecursively(root.getId(), groupByParentId);
        }
    }

    private void buildRecursively(String parentId, Map<String, List<HierarchyGroup>> groupByParentId) {
        for (HierarchyGroup child : groupByParentId.getOrDefault(parentId, Collections.emptyList())) {
            // ensure maps
            parentsByGroup.computeIfAbsent(child.getId(), k -> new HashSet<>());
            childrenByGroup.computeIfAbsent(child.getId(), k -> new HashSet<>());
            parentsByGroup.computeIfAbsent(parentId, k -> new HashSet<>());
            childrenByGroup.computeIfAbsent(parentId, k -> new HashSet<>());

            // add parent and inherit ancestors
            parentsByGroup.get(child.getId()).add(parentId);
            parentsByGroup.get(child.getId()).addAll(parentsByGroup.get(parentId));

            // add child to all ancestors including parent
            childrenByGroup.get(parentId).add(child.getId());
            for (String ancestor : parentsByGroup.get(parentId)) {
                childrenByGroup.get(ancestor).add(child.getId());
            }

            buildRecursively(child.getId(), groupByParentId);
        }
    }

    @Override
    public void deleteGroup(String tenantId, String groupId) {
        Set<String> parents = parentsByGroup.getOrDefault(groupId, Collections.emptySet());
        for (String p : new HashSet<>(parents)) {
            childrenByGroup.getOrDefault(p, Collections.emptySet()).remove(groupId);
        }
        // remove this group's links
        parentsByGroup.remove(groupId);
        childrenByGroup.remove(groupId);
    }

    @Override
    public void createGroup(String tenantId, String parentId, String id) {
        parentsByGroup.computeIfAbsent(id, k -> new HashSet<>());
        childrenByGroup.computeIfAbsent(id, k -> new HashSet<>());
        parentsByGroup.computeIfAbsent(parentId, k -> new HashSet<>());
        childrenByGroup.computeIfAbsent(parentId, k -> new HashSet<>());

        // set parents = parent + parent's ancestors
        parentsByGroup.get(id).clear();
        parentsByGroup.get(id).add(parentId);
        parentsByGroup.get(id).addAll(parentsByGroup.get(parentId));

        // add child to parent and all ancestors
        childrenByGroup.get(parentId).add(id);
        for (String ancestor : parentsByGroup.get(parentId)) {
            childrenByGroup.get(ancestor).add(id);
        }
    }

    @Override
    public void moveGroup(String tenantId, String newParentId, String targetId) {
        // remove references from old parents
        for (String oldParent : new HashSet<>(parentsByGroup.getOrDefault(targetId, Collections.emptySet()))) {
            childrenByGroup.getOrDefault(oldParent, Collections.emptySet()).remove(targetId);
            for (String child : new HashSet<>(childrenByGroup.getOrDefault(targetId, Collections.emptySet()))) {
                childrenByGroup.getOrDefault(oldParent, Collections.emptySet()).remove(child);
            }
        }
        // remove old parent links from target and its children
        Set<String> oldParents = new HashSet<>(parentsByGroup.getOrDefault(targetId, Collections.emptySet()));
        parentsByGroup.put(targetId, new HashSet<>());
        for (String child : new HashSet<>(childrenByGroup.getOrDefault(targetId, Collections.emptySet()))) {
            parentsByGroup.getOrDefault(child, new HashSet<>()).removeAll(oldParents);
        }

        // set new parents (newParent + its ancestors)
        parentsByGroup.computeIfAbsent(newParentId, k -> new HashSet<>());
        parentsByGroup.computeIfAbsent(targetId, k -> new HashSet<>());
        parentsByGroup.get(targetId).add(newParentId);
        parentsByGroup.get(targetId).addAll(parentsByGroup.get(newParentId));

        // propagate new parents to target's children
        for (String child : new HashSet<>(childrenByGroup.getOrDefault(targetId, Collections.emptySet()))) {
            parentsByGroup.computeIfAbsent(child, k -> new HashSet<>());
            parentsByGroup.get(child).addAll(parentsByGroup.get(targetId));
        }

        // add target and its children to new parents' children sets
        childrenByGroup.computeIfAbsent(newParentId, k -> new HashSet<>());
        childrenByGroup.get(newParentId).add(targetId);
        for (String ancestor : parentsByGroup.get(newParentId)) {
            childrenByGroup.computeIfAbsent(ancestor, k -> new HashSet<>());
            childrenByGroup.get(ancestor).add(targetId);
        }
        for (String child : new HashSet<>(childrenByGroup.getOrDefault(targetId, Collections.emptySet()))) {
            childrenByGroup.get(newParentId).add(child);
            for (String ancestor : parentsByGroup.get(newParentId)) {
                childrenByGroup.get(ancestor).add(child);
            }
        }
    }

    @Override
    public Set<String> getParents(String tenantId, String groupId) {
        return parentsByGroup.getOrDefault(groupId, Collections.emptySet());
    }

    @Override
    public Set<String> getChildren(String tenantId, String groupId) {
        return childrenByGroup.getOrDefault(groupId, Collections.emptySet());
    }

    @Override
    public boolean hasCached(String tenantId, String groupId) {
        return parentsByGroup.containsKey(groupId) || childrenByGroup.containsKey(groupId);
    }
}
