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
import com.epam.pipeline.controller.vo.async.AsyncTaskFilterVO;
import com.epam.pipeline.controller.vo.async.AsyncTaskStatusVO;
import com.epam.pipeline.dto.async.AsyncTask;
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.manager.async.AsyncTaskManager;
import com.epam.pipeline.manager.datastorage.DataStorageDownloadArchiveManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AsyncTaskApiService {

    private final AsyncTaskManager asyncTaskManager;
    private final DataStorageDownloadArchiveManager downloadArchiveManager;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public List<AsyncTask> loadByFilter(final AsyncTaskFilterVO filter) {
        return asyncTaskManager.loadByFilter(restrictToCaller(filter));
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public AsyncTask load(final String id) {
        return ownedByCaller(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public AsyncTask cancel(final String id) {
        ownedByCaller(id);
        return asyncTaskManager.cancel(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public DataStorageDownloadFileUrl generateUrl(final String id) {
        return downloadArchiveManager.generateUrl(ownedByCaller(id));
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
        if (!authManager.isAdmin()) {
            restricted.setOwner(authManager.getAuthorizedUser());
        }
        return restricted;
    }

    private AsyncTask ownedByCaller(final String id) {
        final AsyncTask task = asyncTaskManager.load(id);
        if (authManager.isAdmin() || task.getOwner().equalsIgnoreCase(authManager.getAuthorizedUser())) {
            return task;
        }
        throw new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_NOT_FOUND, id));
    }
}
