/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.acl.plugin;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.plugin.UIPluginRepository;
import com.epam.pipeline.dto.plugin.PluginType;
import com.epam.pipeline.dto.plugin.UIPlugin;
import com.epam.pipeline.entity.plugin.UIPluginEntity;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.plugin.UIPluginMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.common.MessageConstants.ERROR_ENTITY_NOT_FOUND;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class PluginServiceTest {

    private static final Long PLUGIN_ID = 1L;
    private static final String PLUGIN_NAME = "Plugin-1";
    private static final String PLUGIN_PATH = "/plugin-1/main.js";
    private static final String FILE_PATH = "main.js";
    private static final String PLUGINS_FOLDER = "/path/to/plugins";
    private static final String ERROR_MESSAGE = "Plugin not found: 1";

    @Mock
    private UIPluginRepository pluginRepository;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private UIPluginMapper uiPluginMapper;

    @Mock
    private MessageHelper messageHelper;

    @InjectMocks
    private PluginService pluginService;

    private UIPlugin dto;
    private UIPluginEntity entity;

    @BeforeEach
    public void setUp() {
        // Initialize test data
        dto = new UIPlugin();
        dto.setId(PLUGIN_ID);
        dto.setName(PLUGIN_NAME);
        dto.setType(PluginType.LaunchForm);
        dto.setPath(PLUGIN_PATH);

        entity = new UIPluginEntity();
        entity.setId(PLUGIN_ID);
        entity.setName(PLUGIN_NAME);
        entity.setType(PluginType.LaunchForm);
        entity.setPath(PLUGIN_PATH);
    }

    private void mockToDtoListMethod() {
        when(uiPluginMapper.toDtoList(anyList())).thenAnswer(invocation -> {
            List<UIPluginEntity> entities = invocation.getArgument(0, List.class);
            return entities.stream().map(e -> {
                UIPlugin d = new UIPlugin();
                d.setId(e.getId());
                d.setName(e.getName());
                d.setType(e.getType());
                d.setPath(e.getPath());
                return d;
            }).collect(Collectors.toList());
        });
    }

    @Test
    public void testGetPluginsByTypeSuccess() {
        mockToDtoListMethod();
        List<UIPluginEntity> entities = Collections.singletonList(entity);
        when(pluginRepository.findByType(PluginType.LaunchForm)).thenReturn(entities);

        List<UIPlugin> result = pluginService.getPlugins(PluginType.LaunchForm);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(dto);
        verify(pluginRepository).findByType(PluginType.LaunchForm);
        verify(uiPluginMapper).toDtoList(entities);
    }

    @Test
    public void testGetAllPluginsSuccess() {
        mockToDtoListMethod();
        List<UIPluginEntity> entities = Arrays.asList(entity, new UIPluginEntity());
        when(pluginRepository.findAll()).thenReturn(entities);

        List<UIPlugin> result = pluginService.getPlugins(null);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(dto);
        verify(pluginRepository).findAll();
        verify(uiPluginMapper).toDtoList(entities);
    }

    @Test
    public void testGetPluginSuccess() {
        when(uiPluginMapper.toDto(entity)).thenReturn(dto);
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(entity));

        UIPlugin result = pluginService.getPlugin(PLUGIN_ID);

        // Assert
        assertEquals(dto, result);
        verify(pluginRepository).findById(PLUGIN_ID);
        verify(uiPluginMapper).toDto(entity);
    }

    @Test
    public void testGetPluginNotFound() {
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.empty());
        when(messageHelper.getMessage(ERROR_ENTITY_NOT_FOUND, PLUGIN_ID, UIPluginEntity.class.getSimpleName()))
                .thenReturn(ERROR_MESSAGE);

        assertThrows(ERROR_MESSAGE, ObjectNotFoundException.class, () -> pluginService.getPlugin(PLUGIN_ID));
    }

    @Test
    public void testSavePluginSuccess() {
        when(pluginRepository.save(entity)).thenReturn(entity);
        when(pluginRepository.findByPath(entity.getPath())).thenReturn(Collections.emptyList());
        when(uiPluginMapper.toDto(entity)).thenReturn(dto);
        when(uiPluginMapper.toEntity(dto)).thenReturn(entity);

        dto.setId(null);
        UIPlugin result = pluginService.savePlugin(dto);

        // Assert
        assertEquals(dto, result);
        verify(uiPluginMapper).toEntity(dto);
        verify(pluginRepository).save(entity);
        verify(uiPluginMapper).toDto(entity);
        verify(pluginRepository).findByPath(entity.getPath());
    }

    @Test
    public void testSavePluginDuplicatePath() {
        UIPluginEntity newEntity = new UIPluginEntity();
        newEntity.setId(2L);
        newEntity.setPath(PLUGIN_PATH);

        when(pluginRepository.findByPath(PLUGIN_PATH)).thenReturn(Collections.singletonList(newEntity));

        dto.setId(null);
        assertThrows(IllegalArgumentException.class, () -> pluginService.savePlugin(dto));
    }

    @Test
    public void testSavePluginInvalidData() {
        UIPlugin invalidDto = new UIPlugin();
        invalidDto.setName("");
        invalidDto.setType(null);
        invalidDto.setPath("");

        assertThrows(IllegalArgumentException.class, () -> pluginService.savePlugin(invalidDto));
    }

    @Test
    public void testUpdatePluginSuccess() {
        when(pluginRepository.findByPath(entity.getPath())).thenReturn(Collections.singletonList(entity));
        when(uiPluginMapper.toEntity(dto)).thenReturn(entity);
        when(pluginRepository.save(entity)).thenReturn(entity);
        when(uiPluginMapper.toDto(entity)).thenReturn(dto);

        UIPlugin result = pluginService.savePlugin(dto);

        // Assert
        assertEquals(dto, result);
        verify(uiPluginMapper).toEntity(dto);
        verify(pluginRepository).save(entity);
        verify(uiPluginMapper).toDto(entity);
        verify(pluginRepository).findByPath(entity.getPath());
    }

    @Test
    public void testUpdatePluginDuplicatePath() {
        UIPluginEntity newEntity = new UIPluginEntity();
        newEntity.setId(2L);
        newEntity.setPath(PLUGIN_PATH);
        when(pluginRepository.findByPath(entity.getPath())).thenReturn(Collections.singletonList(newEntity));

        assertThrows(IllegalArgumentException.class, () -> pluginService.savePlugin(dto));
    }

    @Test
    public void testDeletePluginSuccess() {
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(entity));
        when(uiPluginMapper.toDto(entity)).thenReturn(dto);

        pluginService.deletePlugin(PLUGIN_ID);

        // Assert
        verify(pluginRepository).findById(PLUGIN_ID);
        verify(pluginRepository).deleteById(PLUGIN_ID);
    }

    @Test
    public void testDeletePluginNotFound() {
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.empty());
        when(messageHelper.getMessage(ERROR_ENTITY_NOT_FOUND, PLUGIN_ID, UIPluginEntity.class.getSimpleName()))
                .thenReturn(ERROR_MESSAGE);

        assertThrows("", ObjectNotFoundException.class,
            () -> pluginService.deletePlugin(PLUGIN_ID));
    }

    @Test
    public void testGetPluginFileContentInvalidPluginId() {
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.empty());

        assertThrows("", ObjectNotFoundException.class,
            () -> pluginService.getPluginFileContent(PLUGIN_ID, FILE_PATH));
    }

    @Test
    public void testGetPluginFileContentPathTraversal() {
        String maliciousPath = "../../etc/passwd";
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(entity));
        when(preferenceManager.getPreference(SystemPreferences.UI_PLUGIN_ROOT_FOLDER_PATH)).thenReturn(PLUGINS_FOLDER);

        assertThrows("", IllegalArgumentException.class,
            () -> pluginService.getPluginFileContent(PLUGIN_ID, maliciousPath));
    }

    @Test
    public void testGetPluginFileContentNotFound() {
        String filePath = "/not/existent/path/main.js";
        when(pluginRepository.findById(PLUGIN_ID)).thenReturn(Optional.of(entity));
        when(preferenceManager.getPreference(SystemPreferences.UI_PLUGIN_ROOT_FOLDER_PATH)).thenReturn(PLUGINS_FOLDER);

        assertThrows("", ObjectNotFoundException.class,
            () -> pluginService.getPluginFileContent(PLUGIN_ID, filePath));
    }

}