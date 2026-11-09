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

package com.epam.pipeline.acl.async;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskConsumable;
import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveConsumable;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.dto.async.DownloadArchiveResult;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ArchiveTasksConfig;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DownloadArchiveConfig;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Packing a selection of a data storage into one archive.
 *
 * <p>The selection is not measured here: walking it takes minutes on many files and the platform's
 * path size call gives up on a time limit, so the executor counts it while listing.</p>
 */
@Service
@RequiredArgsConstructor
public class DownloadArchiveTaskProcessor implements AsyncTaskProcessor {

    private static final Pattern SAFE_ARCHIVE_NAME = Pattern.compile("^[\\w\\-. ]+$");
    private static final String DEFAULT_ARCHIVE_NAME = "archive.zip";
    private static final String DEFAULT_ARCHIVE_PREFIX = "archives/";
    private static final String STORAGE_ID = "storageId";
    private static final String PATHS = "paths";
    private static final String ARCHIVE_EXTENSION = ".zip";
    private static final String SLASH = "/";

    private final DataStorageManager dataStorageManager;
    private final AsyncTaskParamsValidator validator;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final StorageProviderManager storageProviderManager;

    @Override
    public AsyncTaskType getType() {
        return AsyncTaskType.DOWNLOAD_ARCHIVE;
    }

    @Override
    public AsyncTaskParams prepare(final AsyncTaskParams params) {
        final DownloadArchiveParams downloadParams = (DownloadArchiveParams) params;

        Assert.notNull(downloadParams.getStorageId(), validator.fieldRequiredMessage(params, STORAGE_ID));
        Assert.isTrue(CollectionUtils.isNotEmpty(downloadParams.getPaths()),
                validator.fieldRequiredMessage(params, PATHS));

        validator.assertStoragePermission(downloadParams.getStorageId(), AclPermission.READ_NAME);

        final AbstractDataStorage storage = dataStorageManager.load(downloadParams.getStorageId());
        dataStorageManager.checkReadPermissionsOnPaths(storage, downloadParams.getPaths());

        return downloadParams.toBuilder()
                .archiveName(validArchiveName(downloadParams.getArchiveName()))
                .build();
    }

    @Override
    public AsyncTaskConsumable consume(AsyncTask task) {
        Assert.isTrue(AsyncTaskStatus.SUCCESS == task.getStatus(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_NOT_READY, task.getId(), task.getStatus()));
        final DownloadArchiveConfig config = getDownloadArchiveConfig();

        final Long storageId = archiveStorageId(task, config);
        Assert.notNull(storageId, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_STORAGE_NOT_CONFIGURED));
        final AbstractDataStorage storage = dataStorageManager.load(storageId);
        final DataStorageDownloadFileUrl signed = storageProviderManager.generateDownloadURL(storage,
                archivePath(task, config), null, ContentDisposition.ATTACHMENT, offeredArchiveName(task));
        return DownloadArchiveConsumable.builder()
                .url(signed.getUrl())
                .expires(signed.getExpires())
                .build();
    }

    private String validArchiveName(final String requested) {
        if (StringUtils.isBlank(requested)) {
            return DEFAULT_ARCHIVE_NAME;
        }
        Assert.isTrue(SAFE_ARCHIVE_NAME.matcher(requested).matches(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_NAME_INVALID, requested));
        return requested;
    }

    private DownloadArchiveConfig getDownloadArchiveConfig() {
        return ArchiveTasksConfig.downloadOrEmpty(fullConfig());
    }

    private ArchiveTasksConfig fullConfig() {
        return preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_ARCHIVE_CONFIG);
    }

    private Long archiveStorageId(final AsyncTask task, final DownloadArchiveConfig config) {
        return archiveResult(task)
                .map(DownloadArchiveResult::getStorageId)
                .orElseGet(config::getDestinationStorageId);
    }

    private String archivePath(final AsyncTask task, final DownloadArchiveConfig config) {
        final String recorded = archiveResult(task)
                .map(DownloadArchiveResult::getPath)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        if (Objects.isNull(recorded)) {
            return archivePrefix(config) + task.getId() + ARCHIVE_EXTENSION;
        }
        Assert.isTrue(recorded.endsWith(task.getId() + ARCHIVE_EXTENSION), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_PATH_INVALID, recorded, task.getId()));
        return recorded;
    }

    private String archivePrefix(final DownloadArchiveConfig config) {
        final String configured = StringUtils.defaultIfBlank(config.getDestinationPrefix(),
                DEFAULT_ARCHIVE_PREFIX);
        final String trimmed = StringUtils.strip(configured, SLASH);
        return StringUtils.isEmpty(trimmed) ? StringUtils.EMPTY : trimmed + SLASH;
    }

    private Optional<DownloadArchiveResult> archiveResult(final AsyncTask task) {
        return task.getResult() instanceof DownloadArchiveResult result
                ? Optional.of(result)
                : Optional.empty();
    }

    String offeredArchiveName(final AsyncTask task) {
        final String requested = archiveParams(task)
                .map(DownloadArchiveParams::getArchiveName)
                .filter(StringUtils::isNotBlank)
                .orElse(DEFAULT_ARCHIVE_NAME);
        return StringUtils.endsWithIgnoreCase(requested, ARCHIVE_EXTENSION)
                ? requested
                : requested + ARCHIVE_EXTENSION;
    }

    private Optional<DownloadArchiveParams> archiveParams(final AsyncTask task) {
        return task.getParams() instanceof DownloadArchiveParams params
                ? Optional.of(params)
                : Optional.empty();
    }
}
