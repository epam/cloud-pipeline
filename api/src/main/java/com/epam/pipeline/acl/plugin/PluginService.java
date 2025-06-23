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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.plugin.UIPluginRepository;
import com.epam.pipeline.dto.plugin.PluginType;
import com.epam.pipeline.dto.plugin.UIPlugin;
import com.epam.pipeline.entity.plugin.UIPluginEntity;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.plugin.UIPluginMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PluginService {
    private final UIPluginRepository pluginRepository;
    private final PreferenceManager preferenceManager;
    private final UIPluginMapper uiPluginMapper;
    private final MessageHelper messageHelper;

    public List<UIPlugin> getPlugins(PluginType type) {
        return uiPluginMapper.toDtoList(type != null ? pluginRepository.findByType(type) : pluginRepository.findAll());
    }

    public UIPlugin getPlugin(Long id) {
        UIPluginEntity plugin = pluginRepository.findOne(id);
        if (Objects.isNull(plugin)) {
            throw new ObjectNotFoundException(messageHelper.getMessage(
                    MessageConstants.ERROR_ENTITY_NOT_FOUND, id, UIPluginEntity.class.getSimpleName()));
        }
        return uiPluginMapper.toDto(plugin);
    }

    @Transactional
    public UIPlugin savePlugin(UIPlugin plugin) {
        validatePlugin(plugin);
        UIPluginEntity entity = uiPluginMapper.toEntity(plugin);
        return uiPluginMapper.toDto(pluginRepository.save(entity));
    }

    @Transactional
    public void deletePlugin(Long id) {
        UIPlugin entity = getPlugin(id);
        pluginRepository.delete(entity.getId());
    }

    public byte[] getPluginFileContent(Long id, String filePath) {
        getPlugin(id);
        String pluginsFolder = preferenceManager.getPreference(SystemPreferences.UI_PLUGIN_ROOT_FOLDER_PATH);

        Path fullPath = Paths.get(pluginsFolder, filePath).normalize();
        //preventing traversal attacks
        if (!fullPath.startsWith(pluginsFolder)) {
            throw new IllegalArgumentException("Invalid file path: " + filePath);
        }

        if (!fullPath.toFile().exists()) {
            throw new ObjectNotFoundException(filePath);
        }

        try {
            return Files.readAllBytes(fullPath);
        } catch (IOException e) {
            throw new PipelineException(e);
        }
    }

    private void validatePlugin(UIPlugin plugin) {
        if (StringUtils.isBlank(plugin.getName()) ||
                plugin.getType() == null || StringUtils.isBlank(plugin.getPath())) {
            throw new IllegalArgumentException("Plugin name, type, and path are required");
        }
        boolean hasDup = pluginRepository.findByPath(plugin.getPath())
                .stream()
                .anyMatch(p -> !p.getId().equals(plugin.getId()));
        if (hasDup) {
            throw new IllegalArgumentException("Plugin path must be unique");
        }
    }
}