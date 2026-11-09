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
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.controller.vo.async.AsyncTaskStatusVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskConsumeResult;
import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.entity.datastorage.ArchiveTasksConfig;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.security.acl.AclExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AsyncTaskApiService {

    private final AsyncTaskManager asyncTaskManager;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;
    private final CheckPermissionHelper permissionHelper;
    private final MessageHelper messageHelper;
    private final Map<AsyncTaskType, AsyncTaskProcessor> asynchProcessorsByType;

    public AsyncTaskApiService(final AsyncTaskManager asyncTaskManager,
                               final List<AsyncTaskProcessor> creationProcessors,
                               final PreferenceManager preferenceManager,
                               final AuthManager authManager,
                               final CheckPermissionHelper permissionHelper,
                               final MessageHelper messageHelper) {

        this.asyncTaskManager = asyncTaskManager;
        this.preferenceManager = preferenceManager;
        this.authManager = authManager;
        this.permissionHelper = permissionHelper;
        this.messageHelper = messageHelper;
        this.asynchProcessorsByType = creationProcessors.stream().
                collect(Collectors.toUnmodifiableMap(AsyncTaskProcessor::getType, Function.identity()));
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public PagedResult<List<AsyncTask>> loadByFilter(final AsyncTaskFilterVO filter) {
        return asyncTaskManager.loadByFilter(restrictToCaller(filter));
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public AsyncTask load(final String id) {
        return loadTaskIfHasAccess(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public AsyncTask createAsyncTask(final AsyncTaskParams params) {
        Assert.notNull(params, messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_PARAMS_REQUIRED));
        final AsyncTaskProcessor processor = asynchProcessorsByType.get(params.getType());
        Assert.notNull(processor, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_TYPE_NOT_SUPPORTED, params.getType()));
        final AsyncTaskParams prepared = processor.prepare(params);
        final String owner = authManager.getAuthorizedUser();
        assertUserQuota(owner);
        return asyncTaskManager.create(prepared.getType(), owner, prepared);
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public AsyncTask cancel(final String id) {
        loadTaskIfHasAccess(id);
        return asyncTaskManager.cancel(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public AsyncTaskConsumeResult consume(final String id) {
        final AsyncTask task = loadTaskIfHasAccess(id);
        AsyncTaskProcessor consumer = asynchProcessorsByType.get(task.getType());
        Assert.notNull(consumer, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_NOTHING_TO_CONSUME, task.getId(), task.getType()));
        return AsyncTaskConsumeResult.of(consumer.consume(task));
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public Optional<AsyncTask> claimNext(final AsyncTaskType type) {
        return asyncTaskManager.claimNext(type);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public AsyncTask updateStatus(final String id, final AsyncTaskStatusVO status) {
        return asyncTaskManager.updateStatus(id, status.getStatus(), status.getResult(),
                status.getErrorMessage());
    }

    private AsyncTaskFilterVO restrictToCaller(final AsyncTaskFilterVO filter) {
        final AsyncTaskFilterVO restricted = filter == null
                ? AsyncTaskFilterVO.builder().build()
                : filter.toBuilder().build();
        if (!mayReachForeignTask()) {
            restricted.setOwner(authManager.getAuthorizedUser());
        }
        return restricted;
    }

    private AsyncTask loadTaskIfHasAccess(final String id) {
        final AsyncTask task = asyncTaskManager.load(id);
        if (mayReachForeignTask() || permissionHelper.isOwner(task.getOwner())) {
            return task;
        }
        throw new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_NOT_FOUND, id));
    }

    private boolean mayReachForeignTask() {
        return authManager.isAdmin() || permissionHelper.hasAnyRole(DefaultRoles.ROLE_STORAGE_ADMIN);
    }

    private void assertUserQuota(final String owner) {
        final int limit = ArchiveTasksConfig.maxUserTasksOrDefault(
                preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_ARCHIVE_CONFIG));
        final long active = asyncTaskManager.countActive(owner);
        Assert.isTrue(active < limit, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_USER_QUOTA_EXCEEDED, limit));
    }
}
