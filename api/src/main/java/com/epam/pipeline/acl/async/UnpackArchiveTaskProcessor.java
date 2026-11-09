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
import com.epam.pipeline.dto.async.AsyncTaskType;
import com.epam.pipeline.dto.async.UnpackArchiveParams;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnpackArchiveTaskProcessor implements AsyncTaskProcessor {

    private static final String SOURCE_STORAGE_ID = "sourceStorageId";
    private static final String SOURCE_PATH = "sourcePath";
    private static final String DESTINATION_STORAGE_ID = "destinationStorageId";
    private static final String DESTINATION_PATH = "destinationPath";

    private final DataStorageManager dataStorageManager;
    private final AsyncTaskParamsValidator validator;
    private final MessageHelper messageHelper;

    @Override
    public AsyncTaskType getType() {
        return AsyncTaskType.UNPACK_ARCHIVE;
    }

    @Override
    public AsyncTaskParams prepare(final AsyncTaskParams params) {
        final UnpackArchiveParams unpackParams = (UnpackArchiveParams) params;

        Assert.notNull(unpackParams.getSourceStorageId(),
                validator.fieldRequiredMessage(params, SOURCE_STORAGE_ID));
        Assert.notNull(unpackParams.getDestinationStorageId(),
                validator.fieldRequiredMessage(params, DESTINATION_STORAGE_ID));
        Assert.isTrue(StringUtils.isNotBlank(unpackParams.getSourcePath()),
                validator.fieldRequiredMessage(params, SOURCE_PATH));
        Assert.notNull(unpackParams.getDestinationPath(),
                validator.fieldRequiredMessage(params, DESTINATION_PATH));

        validator.assertStoragePermission(unpackParams.getSourceStorageId(), AclPermission.READ_NAME);
        validator.assertStoragePermission(unpackParams.getDestinationStorageId(), AclPermission.WRITE_NAME);

        if (unpackParams.isDeleteSourceOnSuccess()) {
            validator.assertStoragePermission(unpackParams.getSourceStorageId(), AclPermission.WRITE_NAME);
        }

        final String sourcePath = validator.asFilePath(unpackParams.getSourcePath());
        final String destinationPrefix = validator.asFolderPrefix(unpackParams.getDestinationPath());

        final AbstractDataStorage source = dataStorageManager.load(unpackParams.getSourceStorageId());
        final AbstractDataStorage destination =
                dataStorageManager.load(unpackParams.getDestinationStorageId());

        dataStorageManager.checkReadPermissionsOnPaths(source, List.of(sourcePath));
        dataStorageManager.checkWritePermissionsOnFolder(destination, destinationPrefix);
        if (unpackParams.isDeleteSourceOnSuccess()) {
            dataStorageManager.checkWritePermissionsOnFile(source, sourcePath);
        }

        return unpackParams.toBuilder()
                .destinationPath(destinationPrefix)
                .build();
    }

    @Override
    public AsyncTaskConsumable consume(AsyncTask task) {
        throw new UnsupportedOperationException(messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_NOTHING_TO_CONSUME, task.getId(), task.getType()));
    }
}
