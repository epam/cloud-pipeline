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
import com.epam.pipeline.controller.vo.UnpackArchiveVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.UnpackArchiveParams;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.ArchiveTasksConfig;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DataStorageUnpackArchiveManager {

    private static final String SLASH = "/";
    private static final String DOUBLE_SLASH = "//";
    private static final String CURRENT_DIRECTORY = ".";
    private static final String PARENT_DIRECTORY = "..";
    private static final String HOME = "~";

    private static final Pattern FORBIDDEN_IN_PATH = Pattern.compile("\\p{Cntrl}|\\R");

    private final DataStorageManager dataStorageManager;
    private final AsyncTaskManager asyncTaskManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    public AsyncTask createTask(final UnpackArchiveVO request, final String owner) {
        validate(request);
        final String sourcePath = validArchivePath(request.getSourcePath());
        final String destinationPrefix = destinationPrefix(request.getDestinationPath());

        final AbstractDataStorage source = dataStorageManager.load(request.getSourceStorageId());
        final AbstractDataStorage destination = dataStorageManager.load(request.getDestinationStorageId());

        dataStorageManager.checkReadPermissionsOnPaths(source, List.of(sourcePath));
        dataStorageManager.checkWritePermissionsOnFolder(destination, destinationPrefix);
        if (request.isDeleteSourceOnSuccess()) {
            dataStorageManager.checkWritePermissionsOnFile(source, sourcePath);
        }

        assertUserQuota(owner);
        return asyncTaskManager.create(AsyncTaskType.UNPACK_ARCHIVE, owner, UnpackArchiveParams.builder()
                .sourceStorageId(source.getId())
                .sourcePath(sourcePath)
                .destinationStorageId(destination.getId())
                .destinationPath(destinationPrefix)
                .deleteSourceOnSuccess(request.isDeleteSourceOnSuccess())
                .overwrite(request.isOverwrite())
                .build());
    }

    private void validate(final UnpackArchiveVO request) {
        Assert.notNull(request, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_REQUEST_REQUIRED));
        Assert.notNull(request.getSourceStorageId(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_SOURCE_STORAGE_REQUIRED));
        Assert.notNull(request.getDestinationStorageId(), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_DESTINATION_STORAGE_REQUIRED));
        Assert.isTrue(StringUtils.isNotBlank(request.getSourcePath()), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_SOURCE_PATH_REQUIRED));
        Assert.isTrue(StringUtils.isNotBlank(request.getDestinationPath()), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_DESTINATION_PATH_REQUIRED));
    }

    private String validArchivePath(final String requested) {
        assertSafePath(requested);
        Assert.isTrue(!requested.endsWith(SLASH), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_SOURCE_NOT_A_FILE, requested));
        return requested;
    }

    private void assertSafePath(final String path) {
        final String invalid = messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_PATH_INVALID, path);
        final String notCanonical = messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_UNPACK_PATH_NOT_CANONICAL, path);

        Assert.isTrue(!path.startsWith(SLASH), notCanonical);
        Assert.isTrue(!path.contains(DOUBLE_SLASH), notCanonical);

        final String[] components = StringUtils.split(path, SLASH);
        Assert.isTrue(Arrays.stream(components).noneMatch(CURRENT_DIRECTORY::equals), notCanonical);

        Assert.isTrue(Arrays.stream(components).noneMatch(PARENT_DIRECTORY::equals), invalid);
        Assert.isTrue(components.length == 0 || !components[0].startsWith(HOME), invalid);
        Assert.isTrue(!FORBIDDEN_IN_PATH.matcher(path).find(), invalid);
    }

    private String destinationPrefix(final String requested) {
        if (SLASH.equals(requested)) {
            return StringUtils.EMPTY;
        }
        assertSafePath(requested);
        return requested.endsWith(SLASH) ? requested : requested + SLASH;
    }

    private void assertUserQuota(final String owner) {
        final int limit = ArchiveTasksConfig.maxUserTasksOrDefault(config());
        final long active = asyncTaskManager.countActive(owner);
        Assert.isTrue(active < limit, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_USER_QUOTA_EXCEEDED, limit));
    }

    private ArchiveTasksConfig config() {
        return preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_ARCHIVE_CONFIG);
    }
}
