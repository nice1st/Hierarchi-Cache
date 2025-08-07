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
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroupEvent;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupEventRepository;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HierarchyGroupService {

	private final HierarchyGroupRepository repository;
	private final HierarchyGroupEventRepository eventRepository;
	private final CacheService cacheService;

	public List<HierarchyGroup> findByTenantId(String tenantId) {
		return repository.findByTenantId(tenantId);
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
		List<HierarchyGroup> allHierarchyGroups = findByTenantId(tenantId);
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

	@Transactional
	public HierarchyGroup generate(String parentId) {
		HierarchyGroup parent = repository.findById(parentId)
		  .orElseThrow(() -> new IllegalArgumentException("Parent not found"));

		HierarchyGroup hierarchyGroup = repository.save(HierarchyGroup.newInstance(parent));
		eventRepository.save(
		  HierarchyGroupEvent.builder()
			.targetId(hierarchyGroup.getId())
			.toId(hierarchyGroup.getParentId())
			.build()
		);

		return hierarchyGroup;
	}

	@Transactional
	public void remove(String id) {
		HierarchyGroup hierarchyGroup = repository.findById(id)
		  .orElseThrow(() -> new IllegalArgumentException("Not found"));

		repository.delete(hierarchyGroup);
		eventRepository.save(
		  HierarchyGroupEvent.builder()
			.targetId(hierarchyGroup.getId())
			.fromId(hierarchyGroup.getParentId())
			.build()
		);
	}

	@Transactional
	public HierarchyGroup move(String id, String parentId) {
		HierarchyGroup hierarchyGroup = repository.findById(id)
		  .orElseThrow(() -> new IllegalArgumentException("Not found"));
		HierarchyGroup parent = repository.findById(parentId)
		  .orElseThrow(() -> new IllegalArgumentException("Parent not found"));

		String fromId = hierarchyGroup.getParentId();
		hierarchyGroup.move(parent);
		eventRepository.save(
		  HierarchyGroupEvent.builder()
			.targetId(hierarchyGroup.getId())
			.fromId(fromId)
			.toId(hierarchyGroup.getParentId())
			.build()
		);
		return hierarchyGroup;
	}

	public Set<String> cache(String parentId) {
		HierarchyGroup parent = repository.findById(parentId)
		  .orElseThrow(() -> new IllegalArgumentException("Parent not found"));

		return null;
	}

	@Transactional(readOnly = true)
	public Set<String> read(String groupId) {
		HierarchyGroup group = repository.findById(groupId)
		  .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

		String tenantId = group.getTenantId();
		String lockKey = cacheService.getLockKey(tenantId);
		boolean locked = cacheService.tryLock(lockKey, Duration.ofMinutes(1), Duration.ofSeconds(30));
		if (!locked) {
			return reBuild(groupId);
		}

		try {
			String cursor = cacheService.getCursor(tenantId);
			List<HierarchyGroupEvent> events = eventRepository.findByIdGreaterThanOrderById(Long.parseLong(cursor));

			if (events.isEmpty()) {
				if (!validateCount(tenantId)) {
					cacheInitialize(tenantId);
				}
			} else {
				processEvents(events, tenantId);
				cacheService.updateCursor(tenantId, events.getLast().getId());
			}

			return cacheService.getChildren(tenantId, groupId);
		} finally {
			cacheService.unlock(lockKey);
		}
	}

	private boolean validateCount(String tenantId) {
		int countWithRoot = repository.countByTenantId(tenantId);
		HierarchyGroup rootGroup = repository.findByTenantIdAndParentIsNull(tenantId);
		int cachedCount = cacheService.getChildren(tenantId, rootGroup.getId()).size();

		return (countWithRoot - 1) == cachedCount;
	}

	private void cacheInitialize(String tenantId) {
		cacheService.initialize(tenantId, getGroupedByParent(tenantId));
	}

	private void processEvents(List<HierarchyGroupEvent> events, String tenantId) {
		for (HierarchyGroupEvent event : events) {
			Set<String> parents = cacheService.getParents(tenantId, event.getTargetId());
			boolean isValid = validationParent(parents, event);
			if (!isValid) {
				cacheInitialize(tenantId);
				break;
			}

			boolean isCached = compareParent(parents, event);
			if (isCached) {
				continue;
			}

			applyEventToCache(tenantId, event);
		}
	}

	private boolean validationParent(Set<String> parents, HierarchyGroupEvent event) {
		HierarchyGroupEvent.EventType type = event.getType();
		return switch (type) {
			case CREATE -> parents.isEmpty();
			case UPDATE, DELETE -> parents.contains(event.getFromId());
		};
	}

	private boolean compareParent(Set<String> parents, HierarchyGroupEvent event) {
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
				cacheService.createGroup(tenantId, event.getTargetId(), event.getToId());
				break;
			case UPDATE:
				cacheService.moveGroup(tenantId, event.getTargetId(), event.getToId());
				break;
			case DELETE:
				cacheService.deleteGroup(tenantId, event.getTargetId());
				break;
		}
	}
}
