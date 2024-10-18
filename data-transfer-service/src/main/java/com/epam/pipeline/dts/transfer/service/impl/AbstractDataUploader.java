/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer.service.impl;

import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.service.DataUploader;
import java.util.List;
import org.springframework.util.Assert;

public abstract class AbstractDataUploader implements DataUploader {

    @Override
    public void transfer(final TransferTask transferTask) {
        transfer(transferTask, false, null, null);
    }

    @Override
    public void transfer(final TransferTask transferTask,
                         final boolean logEnabled,
                         final String pipeCmd,
                         final String pipeCmdSuffix) {
        final StorageItem source = transferTask.getSource();
        final StorageItem destination = transferTask.getDestination();
        if (source.getType() == StorageType.LOCAL) {
            checkStoragePath(destination.getPath());
            upload(source, destination, transferTask.getIncluded(), transferTask.getUser(),
                    transferTask.isDeleteSource(), logEnabled, pipeCmd, pipeCmdSuffix);
        } else {
            checkStoragePath(source.getPath());
            download(source, destination, transferTask.getIncluded(), transferTask.getUser(),
                    transferTask.isDeleteSource(), logEnabled, pipeCmd, pipeCmdSuffix);
        }
    }

    private void checkStoragePath(final String path) {
        final String expectedPathPrefix = getFilesPathPrefix();
        Assert.state(path.startsWith(expectedPathPrefix),
            String.format("%s path must have %s scheme.", getStorageType(), expectedPathPrefix));
    }

    public abstract void upload(StorageItem source, StorageItem destination, List<String> include, String username,
                                boolean deleteSource);

    public abstract void upload(StorageItem source, StorageItem destination, List<String> include, String username,
                                boolean deleteSource, boolean logEnabled, String pipeCmd, String pipeCmdSuffix);

    public abstract void download(StorageItem source, StorageItem destination, List<String> include, String username,
                                  boolean deleteSource);

    public abstract void download(StorageItem source, StorageItem destination, List<String> include, String username,
                                  boolean deleteSource, boolean logEnabled, String pipeCmd, String pipeCmdSuffix);

    public abstract String getFilesPathPrefix();
}
