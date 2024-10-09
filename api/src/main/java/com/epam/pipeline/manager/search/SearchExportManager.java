/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.search.FacetedSearchExportRequest;
import com.epam.pipeline.controller.vo.search.FacetedSearchRequest;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.search.FacetedSearchResult;
import com.epam.pipeline.entity.search.SearchTemplateExportConfig;
import com.epam.pipeline.entity.search.SearchTemplateExportInfo;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchExportManager {

    private static final String TEMPLATE_ID_PLACEHOLDER = "{Template_ID}";
    private static final String EXPORT_DATE = "Export_Date";
    private static final String EXPORT_DATE_PLACEHOLDER = String.format("{%s}", EXPORT_DATE);
    private static final String EXPORT_DATE_REGEX = String.format("\\{%s:([^}]+)\\}", EXPORT_DATE);
    private static final String S3_SCHEMA = "s3://";
    private static final String CP_SCHEMA = "cp://";
    private static final String AZ_SCHEMA = "az://";
    private static final String GS_SCHEMA= "gs://";
    private static final String NFS_SCHEMA = "nfs://";
    private static final int NFS_SCHEMA_LENGTH = 6;
    private static final int CLOUD_SCHEMA_LENGTH = 5;

    private final SearchManager searchManager;
    private final DataStorageManager storageManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final SearchResultExportManager resultExportManager;

    public byte[] export(final FacetedSearchExportRequest request) {
        final FacetedSearchRequest facetedSearchRequest = request.getFacetedSearchRequest();
        final FacetedSearchResult facetedSearchResult = searchManager.getFacetedSearchResult(facetedSearchRequest);
        return resultExportManager.export(request, facetedSearchResult);
    }

    public byte[] templateExport(final FacetedSearchRequest facetedSearchRequest, final String templateId) {
        final SearchTemplateExportConfig templateConfig = getAndValidateTemplateConfig(templateId);
        final FacetedSearchResult facetedSearchResult = searchManager.getFacetedSearchResult(facetedSearchRequest);
        return resultExportManager.templateExport(facetedSearchResult, templateConfig);
    }

    public SearchTemplateExportInfo saveTemplateExport(final FacetedSearchRequest facetedSearchRequest,
                                                       final String templateId) {
        final SearchTemplateExportConfig templateConfig = getAndValidateTemplateConfig(templateId);
        final String cloudExportPath = getCloudExportPath(templateConfig.getSaveTo(), templateId);
        final String storagePath = trimSchema(cloudExportPath);
        final AbstractDataStorage storage = storageManager.loadByPathOrId(storagePath);
        final String storageFilePath = storagePath.substring(storage.getPath().length() + 1);

        final FacetedSearchResult facetedSearchResult = searchManager.getFacetedSearchResult(facetedSearchRequest);
        final byte[] content = resultExportManager.templateExport(facetedSearchResult, templateConfig);
        final DataStorageFile storageFile = storageManager.createDataStorageFile(storage.getId(), storageFilePath,
                content);
        log.debug("Search export saved storage by path '{}'", cloudExportPath);
        return SearchTemplateExportInfo.builder()
                .fullPath(cloudExportPath)
                .storageId(storage.getId())
                .storagePath(storageFile.getPath())
                .build();
    }

    private String getCloudExportPath(final String pathToSave, final String templateId) {
        Assert.state(StringUtils.isNotBlank(pathToSave), messageHelper.getMessage(
                MessageConstants.ERROR_SEARCH_TEMPLATE_EXPORT_PATH_TO_SAVE_EMPTY));
        final List<String> supportedSchemas = Arrays.asList(S3_SCHEMA, CP_SCHEMA, AZ_SCHEMA, GS_SCHEMA, NFS_SCHEMA);
        Assert.state(supportedSchemas.stream().anyMatch(schema ->
                        pathToSave.toLowerCase(Locale.ROOT).startsWith(schema)),
                messageHelper.getMessage(MessageConstants.ERROR_SEARCH_TEMPLATE_EXPORT_PATH_TO_SAVE_WRONG_SCHEMA));

        String resolvedPath = pathToSave;
        if (pathToSave.contains(TEMPLATE_ID_PLACEHOLDER)) {
            resolvedPath = resolvedPath.replace(TEMPLATE_ID_PLACEHOLDER, templateId);
        }
        if (pathToSave.contains(EXPORT_DATE_PLACEHOLDER)) {
            resolvedPath = resolvedPath.replace(EXPORT_DATE_PLACEHOLDER, LocalDateTime.now()
                    .format(DateTimeFormatter.ISO_DATE_TIME));
        } else {
            final Pattern pattern = Pattern.compile(EXPORT_DATE_REGEX);
            final Matcher matcher = pattern.matcher(pathToSave);
            if (matcher.find()) {
                final String dateTimePattern = matcher.group(1);
                resolvedPath = resolvedPath.replace(
                        String.format("{%s:%s}", EXPORT_DATE, dateTimePattern),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(dateTimePattern)));
            }
        }
        return resolvedPath;
    }

    private String trimSchema(final String storagePath) {
        return storagePath.toLowerCase(Locale.ROOT).startsWith(NFS_SCHEMA)
                ? storagePath.substring(NFS_SCHEMA_LENGTH)
                : storagePath.substring(CLOUD_SCHEMA_LENGTH);
    }

    private SearchTemplateExportConfig getAndValidateTemplateConfig(final String templateId) {
        final Map<String, SearchTemplateExportConfig> templateConfigs = preferenceManager.getPreference(
                SystemPreferences.SEARCH_EXPORT_TEMPLATE_MAPPING);
        Assert.state(templateConfigs.containsKey(templateId), messageHelper.getMessage(
                MessageConstants.ERROR_SEARCH_TEMPLATE_EXPORT_NOT_FOUND, templateId));
        final SearchTemplateExportConfig config = templateConfigs.get(templateId);
        final Path templatePath = Paths.get(config.getTemplatePath());
        Assert.state(Files.exists(templatePath) && Files.isRegularFile(templatePath),
                messageHelper.getMessage(MessageConstants.ERROR_SEARCH_TEMPLATE_EXPORT_FILE_NOT_FOUND, templateId));
        return config;
    }

}
