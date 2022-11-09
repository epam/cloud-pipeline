/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.resource;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.controller.resource.StaticResourcesController.STATIC_RESOURCES;

@Service
@RequiredArgsConstructor
public class StaticResourcesService {

    private static final String DELIMITER = "/";

    private final DataStorageManager dataStorageManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    @SneakyThrows
    public DataStorageStreamingContent getContent(final String path) {
        Assert.isTrue(StringUtils.isNotBlank(path) && path.contains(DELIMITER),
                messageHelper.getMessage(MessageConstants.ERROR_STATIC_RESOURCES_INVALID_PATH));
        final String[] split = path.split(DELIMITER, 2);
        final String bucketName = split[0];
        final String filePath = split[1];
        Assert.isTrue(StringUtils.isNotBlank(filePath),
                messageHelper.getMessage(MessageConstants.ERROR_STATIC_RESOURCES_INVALID_PATH));
        final AbstractDataStorage storage = dataStorageManager.loadByNameOrId(bucketName);
        final DataStorageItemType itemType = dataStorageManager.getItemType(storage, filePath, null);
        if (itemType == DataStorageItemType.Folder) {
            final List<AbstractDataStorageItem> items = dataStorageManager.getDataStorageItems(storage.getId(),
                    path, false, null, null).getResults();
            final String tmplPath = preferenceManager.getPreference(
                    SystemPreferences.STATIC_RESOURCES_FOLDER_TEMPLATE_PATH);
            final String tmplName = preferenceManager.getPreference(SystemPreferences.STATIC_RESOURCES_FOLDER_TEMPLATE);
            final String staticResourcesPrefix = preferenceManager.getPreference(SystemPreferences.BASE_API_HOST)
                    + STATIC_RESOURCES;
            final String html = buildHtml(items, tmplPath, tmplName, staticResourcesPrefix);
            return new DataStorageStreamingContent(new ByteArrayInputStream(html.getBytes()), filePath);
        }
        return dataStorageManager.getStreamingContent(storage.getId(), filePath, null);
    }

    public static String buildHtml(final List<AbstractDataStorageItem> items,
                            final String templatePath,
                            final String templateName,
                            final String staticResourcesPrefix) throws IOException{
        final VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templatePath);
        engine.init();
        final Template template = engine.getTemplate(templateName);
        final VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("items", getHtmlStorageItems(items, staticResourcesPrefix));

        try (StringWriter out = new StringWriter()) {
            template.merge(velocityContext, out);
            return out.getBuffer().toString();
        }
    }

    private static List<HtmlStorageItem> getHtmlStorageItems(final List<AbstractDataStorageItem> items,
                                                      final String staticResourcesPrefix) {
        return items.stream()
                .map(i -> HtmlStorageItem.builder()
                    .name(i.getName())
                    .path(staticResourcesPrefix + i.getPath())
                    .size(getFileSize(i))
                    .type(i.getType())
                    .changed(i.getType() == DataStorageItemType.File ? ((DataStorageFile) i).getChanged() : "")
                    .build())
                .sorted(Comparator.comparing(HtmlStorageItem::getType).thenComparing(HtmlStorageItem::getName))
                .collect(Collectors.toList());
    }

    private static String getFileSize(final AbstractDataStorageItem item) {
        return item.getType() == DataStorageItemType.File ?
                FileUtils.byteCountToDisplaySize(((DataStorageFile) item).getSize()) : "";
    }
}
