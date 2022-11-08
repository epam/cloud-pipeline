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
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import freemarker.template.TemplateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.controller.resource.StaticResourcesController.STATIC_RESOURCES;

@Service
@RequiredArgsConstructor
public class StaticResourcesService {

    private static final String DELIMITER = "/";
    private static final String TEMPLATE = "folder.ftlh";
    private static final String VERSION = "2.3.23";
    private static final String TEMPLATES_FOLDER = "/views";

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
        if (Files.isDirectory(Paths.get(path))) {
            final List<AbstractDataStorageItem> items = dataStorageManager.getDataStorageItems(storage.getId(),
                    path, false, null, null).getResults();
            final String html = buildHtml(items);
            return new DataStorageStreamingContent(new ByteArrayInputStream(html.getBytes()), filePath);
        }
        dataStorageManager.checkDataStorageObjectExists(storage, filePath, null);
        return dataStorageManager.getStreamingContent(storage.getId(), filePath, null);
    }

    public String buildHtml(final List<AbstractDataStorageItem> items) throws IOException, TemplateException {
        final Configuration cfg = new Configuration(new Version(VERSION));

        cfg.setClassForTemplateLoading(StaticResourcesService.class, TEMPLATES_FOLDER);
        cfg.setDefaultEncoding("UTF-8");

        final Template template = cfg.getTemplate(TEMPLATE);
        final Map<String, Object> templateData = new HashMap<>();
        templateData.put("items", getHtmlStorageItems(items));

        try (StringWriter out = new StringWriter()) {
            template.process(templateData, out);
            return out.getBuffer().toString();
        }
    }

    private List<HtmlStorageItem> getHtmlStorageItems(final List<AbstractDataStorageItem> items) {
        final String staticResourcesPrefix = preferenceManager.getPreference(SystemPreferences.BASE_API_HOST)
                + STATIC_RESOURCES;
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

    private String getFileSize(final AbstractDataStorageItem item) {
        return item.getType() == DataStorageItemType.File ?
                FileUtils.byteCountToDisplaySize(((DataStorageFile) item).getSize()) : "";
    }
}
