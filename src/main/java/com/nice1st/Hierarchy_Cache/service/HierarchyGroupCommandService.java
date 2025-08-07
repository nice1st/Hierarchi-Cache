package com.nice1st.Hierarchy_Cache.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroupEvent;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupEventRepository;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HierarchyGroupCommandService {

	private final HierarchyGroupRepository repository;
	private final HierarchyGroupEventRepository eventRepository;

	@Transactional
	public HierarchyGroup create(String parentId) {
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
}
