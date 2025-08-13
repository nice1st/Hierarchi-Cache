package com.nice1st.Hierarchy_Cache.service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nice1st.Hierarchy_Cache.cache.CacheService;
import com.nice1st.Hierarchy_Cache.cache.LockService;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroupEvent;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupEventRepository;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HierarchyGroupReadService {

    private final HierarchyGroupRepository repository;
    private final HierarchyGroupEventRepository eventRepository;
    private final CacheService cacheService;
    private final LockService lockService;

    public long countByTenantId(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    public Set<String> recursiveIds(String groupId) {
        Set<String> ids = new HashSet<>();

        repository.findById(groupId)
          .ifPresent(hierarchyGroup -> {
              ids.add(hierarchyGroup.getId());
              recursiveIds(ids, hierarchyGroup);
          });

        return ids;
    }

    private void recursiveIds(Set<String> ids, HierarchyGroup parent) {
        List<HierarchyGroup> byParentId = repository.findByParent(parent);
        for (HierarchyGroup child : byParentId) {
            ids.add(child.getId());
            recursiveIds(ids, child);
        }
    }

    public Set<String> reBuild(String groupId) {
        Set<String> ids = new HashSet<>();

        repository.findById(groupId)
          .ifPresent(hierarchyGroup -> {
              Map<String, List<HierarchyGroup>> map = getGroupedByParent(hierarchyGroup.getTenantId());

              ids.add(groupId);
              reBuild(map, ids, groupId);
          });

        return ids;
    }

    public Map<String, List<HierarchyGroup>> getGroupedByParent(String tenantId) {
        List<HierarchyGroup> allHierarchyGroups = repository.findByTenantId(tenantId);
        return groupByParent(allHierarchyGroups);
    }

    private Map<String, List<HierarchyGroup>> groupByParent(List<HierarchyGroup> allHierarchyGroups) {
        return allHierarchyGroups.stream()
          .collect(Collectors.groupingBy(hierarchyGroup ->
            hierarchyGroup.getParent() != null ? hierarchyGroup.getParent().getId() : cacheService.getRootGroup())
          );
    }

    private void reBuild(Map<String, List<HierarchyGroup>> map, Set<String> ids, String parentId) {
        map.getOrDefault(parentId, Collections.emptyList()).forEach(child -> {
            ids.add(child.getId());
            reBuild(map, ids, child.getId());
        });
    }

    @Transactional(readOnly = true)
    public Set<String> read(String groupId) {
        HierarchyGroup group = repository.findById(groupId)
          .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        String tenantId = group.getTenantId();
        String lockKey = lockService.getLockKey(tenantId);
        boolean locked = lockService.tryLock(lockKey, Duration.ofMinutes(1), Duration.ofSeconds(30));

        try {
            if (!locked) {
                throw new RuntimeException("lock 획득 실패");
            }

            String cursor = cacheService.getCursor(tenantId);
            List<HierarchyGroupEvent> events = eventRepository.findByTenantIdAndIdGreaterThanOrderById(tenantId, Long.parseLong(cursor));
            if (!hasInitialized(tenantId)) {
                cacheInitialize(tenantId);
            } else {
                processEvents(events, tenantId);
            }
            updateCursor(tenantId, events);

            return cacheService.getChildren(tenantId, groupId);
        } catch (Exception e) {
            e.printStackTrace();
            return reBuild(groupId);
        } finally {
            lockService.unlock(lockKey);
        }
    }

    private boolean hasInitialized(String tenantId) {
        HierarchyGroup rootGroup = repository.findByTenantIdAndParentIsNull(tenantId);
        return cacheService.hasCached(tenantId, rootGroup.getId());
    }

    private void cacheInitialize(String tenantId) {
        cacheService.initialize(tenantId, getGroupedByParent(tenantId));
    }

    private void processEvents(List<HierarchyGroupEvent> events, String tenantId) {
        for (HierarchyGroupEvent event : events) {
            boolean hasParent = cacheService.hasCached(tenantId, event.getToId());
            if (!hasParent) {
                cacheInitialize(tenantId);
                break;
            }

            boolean isCached = compareParent(tenantId, event);
            if (isCached) {
                continue;
            }

            applyEventToCache(tenantId, event);
        }
    }

    private void updateCursor(String tenantId, List<HierarchyGroupEvent> events) {
        if (!events.isEmpty()) {
            cacheService.updateCursor(tenantId, events.getLast().getId());
        }
    }

    private boolean compareParent(String tenantId, HierarchyGroupEvent event) {
        Set<String> parents = cacheService.getParents(tenantId, event.getTargetId());
        HierarchyGroupEvent.EventType type = event.getType();
        return switch (type) {
            case CREATE, UPDATE -> parents.contains(event.getToId());
            case DELETE -> parents.isEmpty();
        };
    }

    private void applyEventToCache(String tenantId, HierarchyGroupEvent event) {
        HierarchyGroupEvent.EventType type = event.getType();
        switch (type) {
            case CREATE:
                cacheService.createGroup(tenantId, event.getToId(), event.getTargetId());
                break;
            case UPDATE:
                cacheService.moveGroup(tenantId, event.getToId(), event.getTargetId());
                break;
            case DELETE:
                cacheService.deleteGroup(tenantId, event.getTargetId());
                break;
        }
    }
}
