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

package com.epam.pipeline.acl.plugin;

import com.epam.pipeline.dao.plugin.UIPluginAssignmentRepository;
import com.epam.pipeline.dao.plugin.UIPluginRepository;
import com.epam.pipeline.dto.plugin.UIPluginAssignment;
import com.epam.pipeline.entity.plugin.UIPluginAssignmentEntity;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.mapper.plugin.UIPluginAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PluginAssignmentService {
    private final UIPluginAssignmentRepository assignmentRepository;
    private final UIPluginRepository pluginRepository;
    private final UIPluginAssignmentMapper mapper;

    public List<UIPluginAssignment> getAssignments(Long toolId, Long pipelineId, String version) {
        List<UIPluginAssignmentEntity> assignments = new ArrayList<>();
        if (toolId != null) {
            assignments.addAll(version != null ? assignmentRepository.findByToolIdAndVersion(toolId, version)
                    : assignmentRepository.findByToolId(toolId));
        } else if (pipelineId != null) {
            assignments.addAll(version != null ? assignmentRepository.findByPipelineIdAndVersion(pipelineId, version)
                    : assignmentRepository.findByPipelineId(pipelineId));
        } else {
            assignments.addAll(assignmentRepository.findAll());
        }
        return mapper.toDtoList(assignments);
    }

    public UIPluginAssignment getAssignment(Long id) {
        UIPluginAssignmentEntity assignment = assignmentRepository.findOne(id);
        if (assignment == null) {
            throw new ObjectNotFoundException("Plugin assignment not found: " + id);
        }
        return mapper.toDto(assignment);
    }

    @Transactional
    public UIPluginAssignment saveAssignment(UIPluginAssignment assignment) {
        validateAssignment(assignment);
        UIPluginAssignmentEntity entity = assignmentRepository.save(mapper.toEntity(assignment));
        return mapper.toDto(entity);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        UIPluginAssignment uiPluginAssignment = getAssignment(id);
        assignmentRepository.delete(uiPluginAssignment.getId());
    }

    private void validateAssignment(UIPluginAssignment assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("Assignment cannot be null");
        }
        if (assignment.getPlugin() == null || assignment.getPlugin().getId() == null ||
                pluginRepository.findOne(assignment.getPlugin().getId()) == null) {
            throw new IllegalArgumentException("Valid plugin ID is required");
        }
        if ((assignment.getToolId() == null && assignment.getPipelineId() == null) ||
                assignment.getToolId() != null && assignment.getPipelineId() != null) {
            throw new IllegalArgumentException("Either toolId or pipelineId must be specified");
        }

        boolean hasDup = getAssignments(assignment.getToolId(), assignment.getPipelineId(), assignment.getVersion())
                .stream()
                .filter(ass -> ass.getPlugin().getId().equals(assignment.getPlugin().getId()))
                .anyMatch(ass -> !ass.getId().equals(assignment.getId())); // Exclude the current assignment if updating

        if (hasDup) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate plugin assignment for pluginId - %s, toolId - %s, pipelineId - %s, and version - %s",
                    assignment.getPlugin().getId(), assignment.getToolId(),
                    assignment.getPipelineId(), assignment.getVersion()));
        }
    }
}
