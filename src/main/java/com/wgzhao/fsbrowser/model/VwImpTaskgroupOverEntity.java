package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TASKGROUP_OVER", schema = "STG01", catalog = "")
public class VwImpTaskgroupOverEntity {
    @Basic
    @Column(name = "TASK_GROUP", nullable = true, length = 4000)
    private String taskGroup;

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpTaskgroupOverEntity that = (VwImpTaskgroupOverEntity) o;
        return Objects.equals(taskGroup, that.taskGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskGroup);
    }
}
