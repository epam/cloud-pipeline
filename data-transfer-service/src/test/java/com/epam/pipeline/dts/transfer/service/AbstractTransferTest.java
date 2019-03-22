/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer.service;

import com.epam.pipeline.dts.AbstractTest;
import com.epam.pipeline.dts.transfer.model.StorageItem;
import com.epam.pipeline.dts.transfer.model.StorageType;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractTransferTest extends AbstractTest {

    static final String NON_EXISTING_LOCAL_PATH = "/local/path/to/file";
    static final String LOCAL_CREDENTIALS = "{}";
    static final String S3_PREFIX = "s3://";
    static final String S3_PATH = S3_PREFIX + "path/to/file";
    static final String S3_CREDENTIALS =
        "{" +
            "\"api\": \"api\"," +
            "\"apiToken\": \"apiToken\"" +
        "}";
    static final String GS_PREFIX = "gs://";
    static final String GS_PATH = GS_PREFIX + "path/to/file";
    static final String GS_FOLDER_PATH = GS_PREFIX + "path/to/folder/";
    static final String GS_CREDENTIALS =
        "{" +
            "\"clientId\": \"clientId\"," +
            "\"clientSecret\": \"clientSecret\"," +
            "\"refreshToken\": \"refreshToken\"" +
        "}";

    StorageItem s3Item() {
        return new StorageItem(StorageType.S3, S3_PATH, S3_CREDENTIALS);
    }

    StorageItem invalidS3Item() {
        return new StorageItem(StorageType.S3, StringUtils.removeStart(S3_PATH, S3_PREFIX), S3_CREDENTIALS);
    }

    StorageItem gsItem() {
        return gsItem(GS_PATH);
    }

    StorageItem gsFolderItem() {
        return gsItem(GS_FOLDER_PATH);
    }

    StorageItem invalidGsItem() {
        return gsItem(StringUtils.removeStart(GS_PATH, GS_PREFIX));
    }

    StorageItem gsItem(final String path) {
        return new StorageItem(StorageType.GS, path, GS_CREDENTIALS);
    }

    StorageItem nonExistingLocalItem() {
        return localItem(NON_EXISTING_LOCAL_PATH);
    }

    StorageItem localItem(final String path) {
        return new StorageItem(StorageType.LOCAL, path, LOCAL_CREDENTIALS);
    }

    TransferTask taskOf(final StorageItem source, final StorageItem destination) {
        return TransferTask.builder()
            .source(source)
            .destination(destination)
            .build();
    }
}
