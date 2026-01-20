/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster.pool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

public class NodeSchedule {
    private Long id;
    private String name;
    private LocalDateTime created;
    private List<ScheduleEntry> scheduleEntries;

    public NodeSchedule() {
    }

    @JsonIgnore
    public boolean isActive(final LocalDateTime timestamp) {
        if (CollectionUtils.isEmpty(scheduleEntries)) {
            return true;
        }
        return scheduleEntries.stream()
                .anyMatch(s -> s.isActive(timestamp));
    }

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public LocalDateTime getCreated() {
        return this.created;
    }

    public List<ScheduleEntry> getScheduleEntries() {
        return this.scheduleEntries;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public void setScheduleEntries(List<ScheduleEntry> scheduleEntries) {
        this.scheduleEntries = scheduleEntries;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof NodeSchedule)) return false;
        final NodeSchedule other = (NodeSchedule) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$id = this.getId();
        final Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$created = this.getCreated();
        final Object other$created = other.getCreated();
        if (this$created == null ? other$created != null : !this$created.equals(other$created)) return false;
        final Object this$scheduleEntries = this.getScheduleEntries();
        final Object other$scheduleEntries = other.getScheduleEntries();
        if (this$scheduleEntries == null ? other$scheduleEntries != null : !this$scheduleEntries.equals(other$scheduleEntries))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NodeSchedule;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $created = this.getCreated();
        result = result * PRIME + ($created == null ? 43 : $created.hashCode());
        final Object $scheduleEntries = this.getScheduleEntries();
        result = result * PRIME + ($scheduleEntries == null ? 43 : $scheduleEntries.hashCode());
        return result;
    }

    public String toString() {
        return "NodeSchedule(id=" + this.getId() + ", name=" + this.getName() + ", created=" + this.getCreated() + ", scheduleEntries=" + this.getScheduleEntries() + ")";
    }
}
