package com.nice1st.Hierarchy_Cache.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroupEvent;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupEventRepository;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HierarchyGroupService {

	private final HierarchyGroupRepository hierarchyGroupRepository;
	private final HierarchyGroupEventRepository hierarchyGroupEventRepository;

	public List<HierarchyGroup> findByTenantId(String tenantId) {
		return hierarchyGroupRepository.findByTenantId(tenantId);
	}

	public Set<String> recursiveIds(String parentId) {
		Set<String> ids = new HashSet<>();

		hierarchyGroupRepository.findById(parentId)
		  .ifPresent(hierarchyGroup -> {
			  ids.add(hierarchyGroup.getId());
			  recursiveIds(ids, hierarchyGroup);
		  });

		return ids;
	}

	private void recursiveIds(Set<String> ids, HierarchyGroup parent) {
		List<HierarchyGroup> byParentId = hierarchyGroupRepository.findByParent(parent);
		for (HierarchyGroup child : byParentId) {
			ids.add(child.getId());
			recursiveIds(ids, child);
		}
	}

	public Set<String> reBuild(String parentId) {
		Set<String> ids = new HashSet<>();

		hierarchyGroupRepository.findById(parentId)
		  .ifPresent(hierarchyGroup -> {
			  Map<String, List<HierarchyGroup>> map = getGroupedByParent(hierarchyGroup.getTenantId());

			  ids.add(parentId);
			  reBuild(map, ids, parentId);
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
			hierarchyGroup.getParent() != null ? hierarchyGroup.getParent().getId() : CacheService.ROOT_GROUP)
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
		HierarchyGroup parent = hierarchyGroupRepository.findById(parentId)
		  .orElseThrow(() -> new IllegalArgumentException("Parent not found"));

		HierarchyGroup hierarchyGroup = hierarchyGroupRepository.save(HierarchyGroup.newInstance(parent));
		hierarchyGroupEventRepository.save(
		  HierarchyGroupEvent.builder()
			.targetId(hierarchyGroup.getId())
			.toId(hierarchyGroup.getParentId())
			.build()
		);

		return hierarchyGroup;
	}

	@Transactional
	public void remove(String id) {
		HierarchyGroup hierarchyGroup = hierarchyGroupRepository.findById(id)
		  .orElseThrow(() -> new IllegalArgumentException("Not found"));

		hierarchyGroupRepository.delete(hierarchyGroup);
		hierarchyGroupEventRepository.save(
		  HierarchyGroupEvent.builder()
			.targetId(hierarchyGroup.getId())
			.fromId(hierarchyGroup.getParentId())
			.build()
		);
	}

	@Transactional
	public HierarchyGroup move(String id, String parentId) {
		HierarchyGroup hierarchyGroup = hierarchyGroupRepository.findById(id)
		  .orElseThrow(() -> new IllegalArgumentException("Not found"));
		HierarchyGroup parent = hierarchyGroupRepository.findById(parentId)
		  .orElseThrow(() -> new IllegalArgumentException("Parent not found"));

		String fromId = hierarchyGroup.getParentId();
		hierarchyGroup.move(parent);
		hierarchyGroupEventRepository.save(
		  HierarchyGroupEvent.builder()
			.targetId(hierarchyGroup.getId())
			.fromId(fromId)
			.toId(hierarchyGroup.getParentId())
			.build()
		);
		return hierarchyGroup;
	}

	public Set<String> cache(String parentId) {
		HierarchyGroup parent = hierarchyGroupRepository.findById(parentId)
		  .orElseThrow(() -> new IllegalArgumentException("Parent not found"));

		return null;
	}
}
