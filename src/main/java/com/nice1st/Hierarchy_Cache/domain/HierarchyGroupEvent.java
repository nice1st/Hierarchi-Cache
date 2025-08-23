package com.nice1st.Hierarchy_Cache.domain;

import com.github.f4b6a3.tsid.Tsid;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hierarchy_group_event")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HierarchyGroupEvent {

    @Id
    private Long id;

    private String tenantId;

    private String targetId;

    private String fromId;

    private String toId;

    @PrePersist
    public void assignId() {
        if (id == null) {
            id = Tsid.fast().toLong();
        }
    }

    public EventType getType() {
        if (fromId == null) {
            return EventType.CREATE;
        }

        if (toId == null) {
            return EventType.DELETE;
        }

        return EventType.UPDATE;
    }

    public enum EventType {
        CREATE, DELETE, UPDATE
    }
}
