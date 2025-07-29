package com.nice1st.Hierarchy_Cache.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CacheService {

	private final RedisTemplate<String, String> redisTemplate;

	public static final String ROOT_GROUP = "ROOT_GROUP";

	private String getPrefixKey(String tenantId) {
		return tenantId + ":group";
	}

	public String getChildrenKey(String tenantId, String groupId) {
		return getPrefixKey(tenantId) + groupId + ":children";
	}

	public String getParentsKey(String tenantId, String groupId) {
		return getPrefixKey(tenantId) + groupId + ":parents";
	}

	public void initialize(String tenantId, Map<String, List<HierarchyGroup>> groupByParentId) {
		Map<String, VO> voMap = new HashMap<>(20_000);
		groupByParentId.get(ROOT_GROUP).forEach(rootGroup -> {
			VO rootVO = VO.builder().id(rootGroup.getId()).build();
			voMap.put(rootGroup.getId(), rootVO);
			recursiveVOs(rootVO, groupByParentId, voMap);
		});

		for (VO vo : voMap.values()) {
			delete(tenantId, vo.getId());
			append(tenantId, vo.getId(), vo.getParents(), vo.getChildren());
		}
	}

	private void recursiveVOs(VO parentVO, Map<String, List<HierarchyGroup>> groupByParentId, Map<String, VO> voMap) {
		groupByParentId.getOrDefault(parentVO.getId(), Collections.emptyList())
		  .forEach(child -> {
			  VO vo = VO.fromParent(parentVO, child.getId());
			  voMap.put(child.getId(), vo);
			  parentVO.addChild(child.getId(), voMap::get);
			  recursiveVOs(vo, groupByParentId, voMap);
		  });
	}

	private void append(String tenantId, String groupId, Set<String> parents, Set<String> children) {
		if (!parents.isEmpty()) {
			redisTemplate.opsForSet().add(getParentsKey(tenantId, groupId), parents.toArray(new String[0]));
		}

		if (!children.isEmpty()) {
			redisTemplate.opsForSet().add(getChildrenKey(tenantId, groupId), children.toArray(new String[0]));
		}
	}

	private void remove(String tenantId, String parentId, String groupId) {
		redisTemplate.opsForSet().remove(getChildrenKey(tenantId, parentId), groupId);
	}

	private void delete(String tenantId, String groupId) {
		redisTemplate.delete(getParentsKey(tenantId, groupId));
		redisTemplate.delete(getChildrenKey(tenantId, groupId));
	}

	private VO find(String tenantId, String groupId) {
		Set<String> parents = Optional.ofNullable(redisTemplate.opsForSet().members(getParentsKey(tenantId, groupId)))
		  .orElse(Collections.emptySet());
		Set<String> children = Optional.ofNullable(redisTemplate.opsForSet().members(getChildrenKey(tenantId, groupId)))
		  .orElse(Collections.emptySet());

		return VO.builder().id(groupId).parents(parents).children(children).build();
	}

	public void deleteGroup(String tenantId, String groupId) {
		VO vo = find(tenantId, groupId);
		vo.getParents().forEach(parentId -> remove(tenantId, parentId, groupId));
		delete(tenantId, groupId);
	}

	public void insertGroup(String tenantId, String parentId, String id) {
		VO parent = find(tenantId, parentId);
		VO insertVO = VO.fromParent(parent, id);
		append(tenantId, id, insertVO.getParents(), parent.getChildren());
		append(tenantId, parent.getId(), Collections.emptySet(), Collections.singleton(id));
		for (String grandparentId : parent.getParents()) {
			append(tenantId, grandparentId, Collections.emptySet(), Collections.singleton(id));
		}
	}

	public void update(String tenantId, String targetId, String newParentId) {
		Set<String> oldParents = redisTemplate.opsForSet().members(getParentsKey(tenantId, targetId));
		if (oldParents != null) {
			for (String oldParentId : oldParents) {
				redisTemplate.opsForSet().remove(getChildrenKey(tenantId, oldParentId), targetId);
			}
		}

		redisTemplate.delete(getParentsKey(tenantId, targetId));
		redisTemplate.opsForSet().add(getParentsKey(tenantId, targetId), newParentId);
		redisTemplate.opsForSet().add(getChildrenKey(tenantId, newParentId), targetId);
	}

	public Set<String> getParents(String tenantId, String groupId) {
		return redisTemplate.opsForSet().members(getParentsKey(tenantId, groupId));
	}

	public Set<String> getChildren(String tenantId, String groupId) {
		return redisTemplate.opsForSet().members(getChildrenKey(tenantId, groupId));
	}

	@Getter
	@Builder
	@AllArgsConstructor
	static class VO {

		private String id;

		@Builder.Default
		private Set<String> parents = new HashSet<>();

		@Builder.Default
		private Set<String> children = new HashSet<>();

		public static VO fromParent(VO parentVO, String id) {
			VO vo = VO.builder().id(id).build();
			vo.parents.add(parentVO.getId());
			vo.parents.addAll(parentVO.getParents());
			return vo;
		}

		public void addChild(String childId, Function<String, VO> voFunction) {
			children.add(childId);
			for (String parent : parents) {
				voFunction.apply(parent).addChild(childId, voFunction);
			}
		}
	}
}
