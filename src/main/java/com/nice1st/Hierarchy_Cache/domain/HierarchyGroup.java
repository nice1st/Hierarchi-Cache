package com.nice1st.Hierarchy_Cache.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "hierarchy_group")
@NoArgsConstructor
public class HierarchyGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column
	private String tenantId;

	@ManyToOne
	@JoinColumn(name = "parent_id")
	private HierarchyGroup parent;

	@OneToMany(mappedBy = "parent")
	private List<HierarchyGroup> children = new ArrayList<>();

	public void move(HierarchyGroup parent) {
		this.parent = parent;
	}

	public static HierarchyGroup newInstance(HierarchyGroup parent) {
		HierarchyGroup hierarchyGroup = new HierarchyGroup();
		hierarchyGroup.tenantId = parent.getTenantId();
		hierarchyGroup.parent = parent;
		return hierarchyGroup;
	}

	public String getParentId() {
		return parent != null ? parent.getId() : null;
	}
}
