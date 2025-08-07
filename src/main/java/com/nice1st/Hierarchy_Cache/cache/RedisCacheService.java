package com.nice1st.Hierarchy_Cache.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class RedisCacheService implements CacheService {

	private final RedisTemplate<String, String> redisTemplate;

	private final String ROOT_GROUP = "ROOT_GROUP";

	@Override
	public String getRootGroup() {
		return ROOT_GROUP;
	}

	@Override
	public String getLockKey(String tenantId) {
		return tenantId + ":group:lock";
	}

	@Override
	public boolean tryLock(String key, Duration ttl, Duration maxWait) {
		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < maxWait.toMillis()) {
			Boolean success = redisTemplate.opsForValue().setIfAbsent(key, Thread.currentThread().getName(), ttl);
			if (Boolean.TRUE.equals(success)) {
				return true;
			}
			try {
				Thread.sleep(100); // 재시도 간격
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	@Override
	public void unlock(String key) {
		String currentValue = redisTemplate.opsForValue().get(key);
		if (Objects.equals(currentValue, Thread.currentThread().getName())) {
			redisTemplate.delete(key);
		}
	}

	private String getCursorKey(String tenantId) {
		return tenantId + ":group:cursor";
	}

	@Override
	public String getCursor(String tenantId) {
		String cursorKey = getCursorKey(tenantId);
		return Optional.ofNullable(redisTemplate.opsForValue().get(cursorKey)).orElse("0");
	}

	@Override
	public void updateCursor(String tenantId, Long tsid) {
		String cursorKey = getCursorKey(tenantId);
		redisTemplate.opsForValue().set(cursorKey, String.valueOf(tsid));
	}

	private String getPrefixKey(String tenantId) {
		return tenantId + ":group";
	}

	private String getChildrenKey(String tenantId, String groupId) {
		return getPrefixKey(tenantId) + groupId + ":children";
	}

	private String getParentsKey(String tenantId, String groupId) {
		return getPrefixKey(tenantId) + groupId + ":parents";
	}

	@Override
	public void initialize(String tenantId, Map<String, List<HierarchyGroup>> groupByParentId) {
		Map<String, VO> voMap = new HashMap<>(20_000);
		groupByParentId.get(ROOT_GROUP).forEach(rootGroup -> {
			VO rootVO = VO.builder().id(rootGroup.getId()).build();
			voMap.put(rootGroup.getId(), rootVO);
			recursiveVOs(rootVO, groupByParentId, voMap);
		});

		for (VO vo : voMap.values()) {
			delete(tenantId, vo.getId());
			add(tenantId, vo.getId(), vo.getParents(), vo.getChildren());
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

	private void add(String tenantId, String groupId, Set<String> parents, Set<String> children) {
		if (!parents.isEmpty()) {
			redisTemplate.opsForSet().add(getParentsKey(tenantId, groupId), parents.toArray(new String[0]));
		}

		if (!children.isEmpty()) {
			redisTemplate.opsForSet().add(getChildrenKey(tenantId, groupId), children.toArray(new String[0]));
		}
	}

	private void removeParents(String tenantId, String parentId, Set<String> parents) {
		redisTemplate.opsForSet().remove(getParentsKey(tenantId, parentId), parents.toArray(new Object[0]));
	}

	private void removeChildren(String tenantId, String parentId, Set<String> children) {
		redisTemplate.opsForSet().remove(getChildrenKey(tenantId, parentId), children.toArray(new Object[0]));
	}

	private void delete(String tenantId, String groupId) {
		deleteParents(tenantId, groupId);
		deleteChildren(tenantId, groupId);
	}

	private void deleteParents(String tenantId, String groupId) {
		redisTemplate.delete(getParentsKey(tenantId, groupId));
	}

	private void deleteChildren(String tenantId, String groupId) {
		redisTemplate.delete(getChildrenKey(tenantId, groupId));
	}

	private VO find(String tenantId, String groupId) {
		Set<String> parents = Optional.ofNullable(redisTemplate.opsForSet().members(getParentsKey(tenantId, groupId)))
		  .orElse(Collections.emptySet());
		Set<String> children = Optional.ofNullable(redisTemplate.opsForSet().members(getChildrenKey(tenantId, groupId)))
		  .orElse(Collections.emptySet());

		return VO.builder().id(groupId).parents(parents).children(children).build();
	}

	@Override
	public void deleteGroup(String tenantId, String groupId) {
		VO vo = find(tenantId, groupId);
		vo.getParents().forEach(parentId -> removeChildren(tenantId, parentId, Collections.singleton(groupId)));
		delete(tenantId, groupId);
	}

	@Override
	public void createGroup(String tenantId, String parentId, String id) {
		VO parent = find(tenantId, parentId);
		VO insertVO = VO.fromParent(parent, id);
		deleteParents(tenantId, id);
		add(tenantId, id, insertVO.getParents(), parent.getChildren());
		add(tenantId, parent.getId(), Collections.emptySet(), Collections.singleton(id));
		for (String grandparentId : parent.getParents()) {
			add(tenantId, grandparentId, Collections.emptySet(), Collections.singleton(id));
		}
	}

	@Override
	public void moveGroup(String tenantId, String targetId, String newParentId) {
		VO target = find(tenantId, targetId);
		VO parent = find(tenantId, newParentId);
		// 이전 parents 의 children 제거
		for (String parentId : target.getParents()) {
			removeChildren(tenantId, parentId, Collections.singleton(target.getId()));
			removeChildren(tenantId, parentId, target.getChildren());
		}
		// parents 제거
		removeParents(tenantId, targetId, target.getParents());
		// children 의 parents 제거
		for (String childId : target.getChildren()) {
			removeParents(tenantId, childId, target.getParents());
		}
		// 객체 부모 변경
		target.changeParent(parent);
		// 현 parents 추가
		add(tenantId, targetId, target.getParents(), Collections.emptySet());
		// children 에 parents 추가
		for (String childId : target.getChildren()) {
			add(tenantId, childId, target.getParents(), Collections.emptySet());
		}
		// 현 parents 에 children 추가
		for (String parentId : target.getParents()) {
			add(tenantId, parentId, Collections.emptySet(), Collections.singleton(target.getId()));
			add(tenantId, parentId, Collections.emptySet(), target.getChildren());
		}

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

	@Override
	public Set<String> getParents(String tenantId, String groupId) {
		return redisTemplate.opsForSet().members(getParentsKey(tenantId, groupId));
	}

	@Override
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

		public void changeParent(VO parent) {
			parents.clear();
			parents.add(parent.getId());
			parents.addAll(parent.getParents());
		}
	}
}
