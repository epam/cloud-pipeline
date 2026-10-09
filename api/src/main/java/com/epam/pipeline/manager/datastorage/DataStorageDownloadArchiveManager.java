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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.DownloadArchiveVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskStatus;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.DownloadArchiveParams;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ContentDisposition;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DownloadArchiveConfig;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AwsRegionCredentials;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.RegionAwareS3Helper;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.regex.Pattern;

/**
 * Turns a request for an archive into a task and hands out the link once the archive exists. The
 * archive itself is built by an executor service, not here.
 */
@Service
@RequiredArgsConstructor
public class DataStorageDownloadArchiveManager {

    private static final String ARCHIVE_PREFIX = "archives/";
    private static final String ARCHIVE_EXTENSION = ".zip";
    private static final String DEFAULT_ARCHIVE_NAME = "archive.zip";
    private static final Pattern SAFE_ARCHIVE_NAME = Pattern.compile("^[\\w\\-. ]+$");

    private final DataStorageManager dataStorageManager;
    private final AsyncTaskManager asyncTaskManager;
    private final PreferenceManager preferenceManager;
    private final CloudRegionManager cloudRegionManager;
    /**
     * Five collectors are declared, one per cloud, so this field's name is what picks the S3 one, as
     * in {@code S3StorageProvider}. Signing emits no event; the helper's constructor just wants one.
     */
    private final StorageEventCollector s3Events;
    private final MessageHelper messageHelper;

    /**
     * Accepts the request without measuring the selection: walking it takes minutes on many files, and
     * the platform's path size call gives up on a time limit. The executor counts it while listing.
     */
    public AsyncTask createTask(final Long storageId, final DownloadArchiveVO request, final String owner) {
        Assert.notNull(storageId, messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NOT_FOUND));
        Assert.isTrue(CollectionUtils.isNotEmpty(request.getPaths()), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_PATHS_REQUIRED));
        final AbstractDataStorage storage = dataStorageManager.load(storageId);
        dataStorageManager.checkReadPermissionsOnPaths(storage, request.getPaths());
        assertUserQuota(owner);
        return asyncTaskManager.create(AsyncTaskType.DOWNLOAD_ARCHIVE, owner, DownloadArchiveParams.builder()
                .storageId(storageId)
                .paths(request.getPaths())
                .archiveName(archiveName(request.getArchiveName()))
                .build());
    }

    /**
     * Signs a link to the finished archive anew on every call. A stored one would be dead by the time
     * its owner returned: the signature lives hours, the archive days.
     */
    public DataStorageDownloadFileUrl generateUrl(final AsyncTask task) {
        Assert.isTrue(AsyncTaskStatus.SUCCESS == task.getStatus(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_NOT_READY, task.getId(), task.getStatus()));
        final DownloadArchiveConfig config = config();
        Assert.isTrue(StringUtils.isNotBlank(config.getBucket()), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_STORAGE_NOT_CONFIGURED));
        final AwsRegion region = (AwsRegion) cloudRegionManager.loadOrDefault(config.getRegionId());
        final AwsRegionCredentials credentials = cloudRegionManager.loadCredentials(region);
        final S3Helper helper = new RegionAwareS3Helper(s3Events, messageHelper, region, credentials);
        return helper.generateDownloadURL(config.getBucket(), archiveKey(task.getId()), null,
                ContentDisposition.ATTACHMENT);
    }

    /**
     * Derived from the task id rather than chosen by the executor, so that the archive can be found -
     * or cleaned up after a worker that died before reporting - from the task alone.
     */
    public String archiveKey(final String taskId) {
        return ARCHIVE_PREFIX + taskId + ARCHIVE_EXTENSION;
    }

    private void assertUserQuota(final String owner) {
        final int limit = config().getMaxUserTasks();
        final long active = asyncTaskManager.countActive(owner);
        Assert.isTrue(active < limit, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_USER_QUOTA_EXCEEDED, limit));
    }

    private DownloadArchiveConfig config() {
        return preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_DOWNLOAD_ARCHIVE_CONFIG);
    }

    private String archiveName(final String requested) {
        if (StringUtils.isBlank(requested)) {
            return DEFAULT_ARCHIVE_NAME;
        }
        Assert.isTrue(SAFE_ARCHIVE_NAME.matcher(requested).matches(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_ARCHIVE_NAME_INVALID, requested));
        return requested;
    }
}
