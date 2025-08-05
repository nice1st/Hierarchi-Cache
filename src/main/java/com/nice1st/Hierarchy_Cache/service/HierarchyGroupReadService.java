package com.nice1st.Hierarchy_Cache.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nice1st.Hierarchy_Cache.domain.HierarchyGroup;
import com.nice1st.Hierarchy_Cache.domain.HierarchyGroupEvent;
import com.nice1st.Hierarchy_Cache.helper.RedisLockHelper;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupEventRepository;
import com.nice1st.Hierarchy_Cache.repository.HierarchyGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HierarchyGroupReadService {

	private final HierarchyGroupRepository groupRepository;
	private final HierarchyGroupEventRepository eventRepository;
	private final RedisTemplate<String, String> redisTemplate;
	private final RedisLockHelper redisLockHelper;
	private final CacheService cacheService;

	@Transactional(readOnly = true)
	public Set<String> read(String groupId) {
		HierarchyGroup group = groupRepository.findById(groupId)
		  .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

		String tenantId = group.getTenantId();
		String lockKey = RedisLockHelper.getLockKey(tenantId);
		boolean locked = redisLockHelper.tryLock(lockKey, Duration.ofMinutes(1), Duration.ofSeconds(30));
		if (!locked) {
			// todo legacy
			throw new IllegalStateException("Another sync in progress for tenant: " + tenantId);
		}

		try {
			String cursor = getCursor(tenantId);
			List<HierarchyGroupEvent> events = eventRepository.findByIdGreaterThanOrderById(Long.parseLong(cursor));

			if (events.isEmpty()) {
				if (!validateCount(tenantId)) {
					// todo cacheService.initialize();
				}
			} else {
				processEvents(events, tenantId);
				updateCursor(tenantId, events.getLast().getId());
			}

			return cacheService.getChildren(tenantId, groupId);
		} finally {
			redisLockHelper.unlock(lockKey);
		}
	}

	private String getCursor(String tenantId) {
		String cursorKey = cacheService.getCursorKey(tenantId);
		return Optional.ofNullable(redisTemplate.opsForValue().get(cursorKey)).orElse("0");
	}

	private boolean validateCount(String tenantId) {
		int countWithRoot = groupRepository.countByTenantId(tenantId);
		HierarchyGroup rootGroup = groupRepository.findByTenantIdAndParentIsNull(tenantId);
		int cachedCount = cacheService.getChildren(tenantId, rootGroup.getId()).size();

		return (countWithRoot - 1) == cachedCount;
	}

	private void processEvents(List<HierarchyGroupEvent> events, String tenantId) {
		for (HierarchyGroupEvent event : events) {
			Set<String> parents = cacheService.getParents(tenantId, event.getTargetId());
			boolean isValid = validationParent(parents, event);
			if (!isValid) {
				// todo cacheService.initialize();
				break;
			}

			boolean isCached = compareParent(parents, event);
			if (isCached) {
				continue;
			}

			applyEventToCache(tenantId, event);
		}
	}

	private void updateCursor(String tenantId, Long tsid) {
		String cursorKey = cacheService.getCursorKey(tenantId);
		redisTemplate.opsForValue().set(cursorKey, String.valueOf(tsid));
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
