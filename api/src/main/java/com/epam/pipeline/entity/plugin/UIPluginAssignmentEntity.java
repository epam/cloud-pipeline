/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.plugin;

import com.epam.pipeline.entity.quota.QuotaSidEntity;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

@Data
@Entity
@Table(name = "ui_plugin_assignment")
public class UIPluginAssignmentEntity implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "plugin_id", nullable = false)
    private UIPluginEntity plugin;

    @Column(name = "tool_id")
    private Long toolId;

    @Column(name = "pipeline_id")
    private Long pipelineId;

    private String version;

    @ElementCollection
    @CollectionTable(name = "ui_plugin_assignment_sids", joinColumns = @JoinColumn(name = "assignment_id"))
    private List<QuotaSidEntity> sids;
}