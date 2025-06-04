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
import com.epam.pipeline.dto.plugin.UIPlugin;
import com.epam.pipeline.dto.plugin.UIPluginAssignment;
import com.epam.pipeline.entity.plugin.UIPluginAssignmentEntity;
import com.epam.pipeline.entity.plugin.UIPluginEntity;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.mapper.plugin.UIPluginAssignmentMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PluginAssignmentServiceTest {

    private static final Long PLUGIN_ID = 1L;
    private static final Long ASSIGNMENT_ID = 1L;
    private static final Long TOOL_ID = 2L;
    private static final Long PIPELINE_ID = 3L;
    private static final String VERSION = "v1.0";

    @Mock
    private UIPluginAssignmentRepository assignmentRepository;

    @Mock
    private UIPluginRepository pluginRepository;

    @Mock
    private UIPluginAssignmentMapper mapper;

    @InjectMocks
    private PluginAssignmentService pluginAssignmentService;

    private UIPlugin pluginDto;
    private UIPluginEntity pluginEntity;
    private UIPluginAssignment assignmentDto;
    private UIPluginAssignmentEntity assignmentEntity;

    @Before
    public void setUp() {
        pluginDto = new UIPlugin();
        pluginDto.setId(PLUGIN_ID);

        pluginEntity = new UIPluginEntity();
        pluginEntity.setId(PLUGIN_ID);

        assignmentDto = new UIPluginAssignment();
        assignmentDto.setId(ASSIGNMENT_ID);
        assignmentDto.setPlugin(pluginDto);
        assignmentDto.setToolId(TOOL_ID);
        assignmentDto.setVersion(VERSION);

        assignmentEntity = new UIPluginAssignmentEntity();
        assignmentEntity.setId(ASSIGNMENT_ID);
        assignmentEntity.setPlugin(pluginEntity);
        assignmentEntity.setToolId(TOOL_ID);
        assignmentEntity.setVersion(VERSION);
    }

    @Test
    public void shouldGetAssignmentsByToolIdAndVersion() {
        when(assignmentRepository.findByToolIdAndVersion(TOOL_ID, VERSION))
                .thenReturn(Collections.singletonList(assignmentEntity));
        when(mapper.toDtoList(Collections.singletonList(assignmentEntity)))
                .thenReturn(Collections.singletonList(assignmentDto));

        List<UIPluginAssignment> result = pluginAssignmentService.getAssignments(TOOL_ID, null, VERSION);

        // Assert
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(assignmentDto));
        verify(assignmentRepository).findByToolIdAndVersion(TOOL_ID, VERSION);
        verify(mapper).toDtoList(Collections.singletonList(assignmentEntity));
        verifyNoMoreInteractions(assignmentRepository);
        verifyZeroInteractions(pluginRepository);
    }

    @Test
    public void shouldGetAssignmentsByPipelineId() {
        when(assignmentRepository.findByPipelineId(PIPELINE_ID))
                .thenReturn(Collections.singletonList(assignmentEntity));
        when(mapper.toDtoList(Collections.singletonList(assignmentEntity)))
                .thenReturn(Collections.singletonList(assignmentDto));

        List<UIPluginAssignment> result = pluginAssignmentService.getAssignments(null, PIPELINE_ID, null);

        // Assert
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(assignmentDto));
        verify(assignmentRepository).findByPipelineId(PIPELINE_ID);
        verify(mapper).toDtoList(Collections.singletonList(assignmentEntity));
        verifyNoMoreInteractions(assignmentRepository);
        verifyNoMoreInteractions(mapper);
        verifyZeroInteractions(pluginRepository);
    }

    @Test
    public void shouldGetAssignmentsAll() {
        when(assignmentRepository.findAll()).thenReturn(Collections.singletonList(assignmentEntity));
        when(mapper.toDtoList(Collections.singletonList(assignmentEntity))).
                thenReturn(Collections.singletonList(assignmentDto));

        List<UIPluginAssignment> result = pluginAssignmentService.getAssignments(null, null, null);

        // Assert
        assertThat(result, hasSize(1));
        assertThat(result.get(0), is(assignmentDto));
        verify(assignmentRepository).findAll();
        verify(mapper).toDtoList(Collections.singletonList(assignmentEntity));
        verifyNoMoreInteractions(assignmentRepository);
        verifyNoMoreInteractions(mapper);
        verifyZeroInteractions(pluginRepository);
    }

    @Test
    public void shouldGetAssignmentByIdSuccess() {
        when(assignmentRepository.findOne(ASSIGNMENT_ID)).thenReturn(assignmentEntity);
        when(mapper.toDto(assignmentEntity)).thenReturn(assignmentDto);

        UIPluginAssignment result = pluginAssignmentService.getAssignment(ASSIGNMENT_ID);

        // Assert
        assertThat(result, is(assignmentDto));
        verify(assignmentRepository).findOne(ASSIGNMENT_ID);
        verify(mapper).toDto(assignmentEntity);
        verifyNoMoreInteractions(assignmentRepository);
        verifyNoMoreInteractions(mapper);
        verifyZeroInteractions(pluginRepository);
    }

    @Test(expected = ObjectNotFoundException.class)
    public void shouldGetAssignmentNotFound() {
        when(assignmentRepository.findOne(ASSIGNMENT_ID)).thenReturn(null);

        pluginAssignmentService.getAssignment(ASSIGNMENT_ID);
    }

    @Test
    public void shouldSaveAssignmentNew() {
        assignmentDto.setId(null);
        when(pluginRepository.findOne(PLUGIN_ID)).thenReturn(pluginEntity);
        when(mapper.toEntity(assignmentDto)).thenReturn(assignmentEntity);
        when(assignmentRepository.save(assignmentEntity)).thenReturn(assignmentEntity);
        when(mapper.toDto(assignmentEntity)).thenReturn(assignmentDto);
        when(assignmentRepository.findByToolIdAndVersion(TOOL_ID, VERSION)).thenReturn(Collections.emptyList());
        when(mapper.toDtoList(Collections.emptyList())).thenReturn(Collections.emptyList());

        UIPluginAssignment result = pluginAssignmentService.saveAssignment(assignmentDto);

        // Assert
        assertThat(result, is(assignmentDto));
        verify(assignmentRepository).findByToolIdAndVersion(TOOL_ID, VERSION);
        verify(mapper).toEntity(assignmentDto);
        verify(pluginRepository).findOne(PLUGIN_ID);
        verify(assignmentRepository).save(assignmentEntity);
        verify(mapper).toDto(assignmentEntity);
        verify(mapper).toDtoList(Collections.emptyList());
        verifyNoMoreInteractions(assignmentRepository);
        verifyNoMoreInteractions(mapper);
        verifyNoMoreInteractions(pluginRepository);
    }

    @Test
    public void shouldSaveAssignmentUpdate() {
        when(pluginRepository.findOne(PLUGIN_ID)).thenReturn(pluginEntity);
        when(mapper.toEntity(assignmentDto)).thenReturn(assignmentEntity);
        when(assignmentRepository.save(assignmentEntity)).thenReturn(assignmentEntity);
        when(assignmentRepository.findByToolIdAndVersion(TOOL_ID, VERSION))
                .thenReturn(Collections.singletonList(assignmentEntity));
        when(mapper.toDto(assignmentEntity)).thenReturn(assignmentDto);

        UIPluginAssignment result = pluginAssignmentService.saveAssignment(assignmentDto);

        // Assert
        assertThat(result, is(assignmentDto));
        verify(pluginRepository).findOne(PLUGIN_ID);
        verify(assignmentRepository).findByToolIdAndVersion(TOOL_ID, VERSION);
        verify(mapper).toEntity(assignmentDto);
        verify(assignmentRepository).save(assignmentEntity);
        verify(mapper).toDto(assignmentEntity);
        verify(mapper).toDtoList(anyList());
        verifyNoMoreInteractions(assignmentRepository);
        verifyNoMoreInteractions(mapper);
        verifyNoMoreInteractions(pluginRepository);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldSaveAssignmentDuplicate() {
        UIPluginAssignmentEntity existingEntity = new UIPluginAssignmentEntity();
        existingEntity.setId(2L); // Different ID
        existingEntity.setPlugin(pluginEntity);
        existingEntity.setToolId(TOOL_ID);
        existingEntity.setVersion(VERSION);

        UIPluginAssignment existingDto = new UIPluginAssignment();
        existingDto.setId(2L); // Different ID
        existingDto.setPlugin(pluginDto);
        existingDto.setToolId(TOOL_ID);
        existingDto.setVersion(VERSION);

        when(pluginRepository.findOne(PLUGIN_ID)).thenReturn(pluginEntity);
        when(assignmentRepository.findByToolIdAndVersion(TOOL_ID, VERSION))
                .thenReturn(Collections.singletonList(existingEntity));
        when(mapper.toDtoList(Collections.singletonList(existingEntity))).
                thenReturn(Collections.singletonList(existingDto));

        pluginAssignmentService.saveAssignment(assignmentDto);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldSaveAssignmentInvalidPluginId() {
        when(pluginRepository.findOne(PLUGIN_ID)).thenReturn(null);

        pluginAssignmentService.saveAssignment(assignmentDto);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldSaveAssignmentMissingToolAndPipelineId() {
        assignmentDto.setToolId(null);
        assignmentDto.setPipelineId(null);
        when(pluginRepository.findOne(PLUGIN_ID)).thenReturn(pluginEntity);

        pluginAssignmentService.saveAssignment(assignmentDto);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldSaveAssignmentBothToolAndPipelineId() {
        assignmentDto.setToolId(TOOL_ID);
        assignmentDto.setPipelineId(PIPELINE_ID);
        when(pluginRepository.findOne(PLUGIN_ID)).thenReturn(pluginEntity);

        pluginAssignmentService.saveAssignment(assignmentDto);
    }

    @Test
    public void shouldDeleteAssignmentSuccess() {
        when(assignmentRepository.findOne(ASSIGNMENT_ID)).thenReturn(assignmentEntity);
        when(mapper.toDto(assignmentEntity)).thenReturn(assignmentDto);

        pluginAssignmentService.deleteAssignment(ASSIGNMENT_ID);

        // Assert
        verify(assignmentRepository).findOne(ASSIGNMENT_ID);
        verify(mapper).toDto(assignmentEntity);
        verify(assignmentRepository).delete(ASSIGNMENT_ID);
        verifyNoMoreInteractions(assignmentRepository);
        verifyNoMoreInteractions(mapper);
        verifyZeroInteractions(pluginRepository);
    }

    @Test(expected = ObjectNotFoundException.class)
    public void shouldDeleteAssignmentNotFound() {
        when(assignmentRepository.findOne(ASSIGNMENT_ID)).thenReturn(null);

        pluginAssignmentService.deleteAssignment(ASSIGNMENT_ID);
    }
}