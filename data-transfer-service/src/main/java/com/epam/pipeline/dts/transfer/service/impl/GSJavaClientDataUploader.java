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
import com.epam.pipeline.dts.transfer.model.google.GoogleTransferInfo;
import com.epam.pipeline.dts.transfer.model.google.GoogleCredentials;
import com.epam.pipeline.dts.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import java.util.List;
import java.io.File;

import static com.epam.pipeline.dts.transfer.service.impl.GoogleStorageClient.DELIMITER;

@Slf4j
@RequiredArgsConstructor
public class GSJavaClientDataUploader extends AbstractDataUploader {
    private static final String GS_PREFIX = "gs://";

    private final GoogleStorageClient client;

    @Override
    public StorageType getStorageType() {
        return StorageType.GS;
    }

    @Override
    public String getFilesPathPrefix() {
        return GS_PREFIX;
    }

    /**
     * @param include Is not supported yet.
     * @param username not supported.
     */
    @Override
    public void upload(final StorageItem source, final StorageItem destination, final List<String> include,
                       final String username) {
        upload(source.getPath(), destination.getPath(), destination.getCredentials());
    }

    private void upload(String source, String destination, String credentials) {
        File sourceFile = new File(source);
        GoogleCredentials googleCredentials = GoogleCredentials.from(credentials);
        GoogleTransferInfo transferInfo = new GoogleTransferInfo()
            .withCredentials(googleCredentials)
            .withSource(source);
        if (sourceFile.isDirectory()) {
            transferInfo.setDestination(destination);
            client.uploadDirectory(transferInfo);
        } else if (sourceFile.isFile()) {
            String destinationToUpload = destination;
            if (destinationToUpload.endsWith(DELIMITER)) {
                destinationToUpload += sourceFile.getName();
            }
            transferInfo.setDestination(destinationToUpload);
            client.uploadFile(transferInfo);
        } else {
            throw new IllegalArgumentException(String.format("Cannot find source %s.", source));
        }
    }

    /**
     * @param include Is not supported yet.
     * @param username not supported.
     */
    @Override
    public void download(final StorageItem source, final StorageItem destination, final List<String> include,
                         final String username) {
        download(source.getPath(), destination.getPath(), source.getCredentials());
    }

    private void download(String source, String destination, String credentials) {
        Pair<String, String> bucketNameAndKey = Utils.getBucketNameAndKey(source);
        GoogleCredentials googleCredentials = GoogleCredentials.from(credentials);
        GoogleTransferInfo transferInfo = new GoogleTransferInfo()
                .withCredentials(googleCredentials)
                .withBucketName(bucketNameAndKey.getLeft())
                .withKey(bucketNameAndKey.getRight())
                .withSource(source);
        if (client.isFile(transferInfo)) {
            transferInfo.setDestination(destination);
            client.downloadFile(transferInfo);
            log.debug(String.format("File has been successfully downloaded from %s to %s.", source, destination));
        } else if (client.isDirectory(transferInfo)) {
            transferInfo.setDestination(StringUtils.removeEnd(destination, DELIMITER));
            transferInfo.setSource(getRelativeStoragePath(source, bucketNameAndKey.getLeft()));
            client.downloadDirectory(transferInfo);
            log.debug(String.format("Files have been successfully downloaded from %s to folder %s.",
                    source, destination));
        } else {
            throw new IllegalArgumentException(String.format("Cannot find source %s.", source));
        }
    }

    private String getRelativeStoragePath(String path, String bucketName) {
        String relativePath = StringUtils.removeStart(path, String.format("gs://%s/", bucketName));
        relativePath = StringUtils.removeEnd(relativePath, DELIMITER);
        return relativePath;
    }
}
