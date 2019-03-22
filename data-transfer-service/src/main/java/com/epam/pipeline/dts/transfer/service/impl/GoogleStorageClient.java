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

package com.epam.pipeline.dts.transfer.service.impl;

import com.epam.pipeline.dts.transfer.model.google.GoogleTransferInfo;
import com.epam.pipeline.dts.transfer.model.google.GoogleCredentials;
import com.epam.pipeline.dts.util.Utils;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@Slf4j
public class GoogleStorageClient {
    public static final String DELIMITER = "/";
    private static final int BUFFER_SIZE = 64 * 1024;

    public void uploadDirectory(GoogleTransferInfo transferInfo) {
        Storage storage = getStorage(transferInfo.getCredentials());
        uploadDirectory(new File(transferInfo.getSource()), transferInfo.getDestination(), storage);
    }

    public void uploadFile(GoogleTransferInfo transferInfo) {
        Storage storage = getStorage(transferInfo.getCredentials());
        uploadFile(transferInfo.getSource(), transferInfo.getDestination(), storage);
    }

    public void downloadDirectory(GoogleTransferInfo transferInfo) {
        Bucket bucket = getBucket(transferInfo.getCredentials(), transferInfo.getBucketName());
        String key = transferInfo.getKey() + DELIMITER;
        downloadFolder(key, transferInfo.getSource(), transferInfo.getDestination(), bucket);
    }

    public void downloadFile(GoogleTransferInfo transferInfo) {
        Blob blob = getBucket(transferInfo.getCredentials(), transferInfo.getBucketName())
                .get(transferInfo.getKey());
        String destination = transferInfo.getDestination();
        if (destination.endsWith(DELIMITER)) {
            destination += Paths.get(blob.getName()).getFileName();
        }
        downloadFile(blob, destination);
    }

    public boolean isFile(GoogleTransferInfo transferInfo) {
        Bucket bucket = getBucket(transferInfo.getCredentials(), transferInfo.getBucketName());
        return !transferInfo.getSource().endsWith(DELIMITER) && isFileExists(transferInfo.getKey(), bucket);
    }

    public boolean isDirectory(GoogleTransferInfo transferInfo) {
        Bucket bucket = getBucket(transferInfo.getCredentials(), transferInfo.getBucketName());
        return isDirectoryExists(transferInfo.getKey(), bucket);
    }

    private void uploadDirectory(File source, String destination, Storage storage) {
        String[] files = source.list();
        String sourcePath = source.getAbsolutePath();
        if (files == null) {
            log.info(String.format("Specified directory %s is empty.", source.getAbsolutePath()));
            return;
        }
        for (String file : files) {
            String sourceFile = String.join(DELIMITER, sourcePath, file);
            String destinationFile = String.join(DELIMITER, StringUtils.removeEnd(destination, DELIMITER), file);
            if (Paths.get(sourceFile).toFile().isDirectory()) {
                uploadDirectory(Paths.get(sourceFile).toFile(), destinationFile, storage);
            } else {
                uploadFile(sourceFile, destinationFile, storage);
            }
        }
    }

    private void uploadFile(String source, String destination, Storage storage) {
        log.debug(String.format("Ready to upload file from %s to %s.", source, destination));
        BlobInfo blob = createBlobInfo(destination);
        try (WriteChannel writer = storage.writer(blob);
             InputStream input = Files.newInputStream(Paths.get(source))) {
            byte[] buffer = new byte[1024];
            int limit;
            while ((limit = input.read(buffer)) >= 0) {
                writer.write(ByteBuffer.wrap(buffer, 0, limit));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("An error occurred during uploading file from %s to %s.",
                    source, destination), e);
        }
    }

    private void downloadFolder(String key, String source, String destination, Bucket bucket) {
        for (Blob blob : listBucket(bucket, key)) {
            if (blob.getName().equals(key)) {
                continue;
            }
            if (blob.getName().endsWith(DELIMITER)) {
                downloadFolder(blob.getName(), source, destination, bucket);
            } else {
                downloadFile(blob, blob.getName().replaceFirst(source, destination));
            }
        }
    }

    private void downloadFile(Blob blob, String destination) {
        log.debug(String.format("Ready to download file from gs://%s/%s to %s.", blob.getBucket(), blob.getName(),
                destination));
        try (ReadChannel reader = blob.reader();
             OutputStream writeTo = FileUtils.openOutputStream(new File(destination));
             WritableByteChannel channel = Channels.newChannel(writeTo)) {
            ByteBuffer bytes = ByteBuffer.allocate(BUFFER_SIZE);
            while (reader.read(bytes) > 0) {
                bytes.flip();
                channel.write(bytes);
                bytes.clear();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("An error occurred during downloading file from gs://%s/%s to %s.",
                            blob.getBucket(), blob.getName(), destination), e);
        }
    }

    private static Storage getStorage(GoogleCredentials credentials) {
        UserCredentials userCredentials = UserCredentials
                .newBuilder()
                .setClientId(credentials.getClientId())
                .setClientSecret(credentials.getClientSecret())
                .setRefreshToken(credentials.getRefreshToken())
                .build();
        StorageOptions storageOptions = StorageOptions
                .newBuilder()
                .setCredentials(userCredentials)
                .build();
        Storage storage = storageOptions.getService();
        Assert.notNull(storage, "Cannot create storage.");
        return storage;
    }

    private BlobInfo createBlobInfo(String path) {
        BlobInfo blobInfo = BlobInfo.newBuilder(createBlobId(path)).build();
        Assert.notNull(blobInfo, String.format("Cannot find storage object by path %s.", path));
        return blobInfo;
    }

    private BlobId createBlobId(String path) {
        Pair<String, String> bucketNameAndKey = Utils.getBucketNameAndKey(path);
        BlobId blobId = BlobId.of(bucketNameAndKey.getLeft(), bucketNameAndKey.getRight());
        Assert.notNull(blobId, String.format("Cannot create storage object for path %s.", path));
        return blobId;
    }

    private boolean isDirectoryExists(String key, Bucket bucket) {
        return listBucket(bucket, String.format("%s/", key)).iterator().hasNext();
    }

    private boolean isFileExists(String key, Bucket bucket) {
        return bucket.get(key) != null;
    }

    private Iterable<Blob> listBucket(Bucket bucket, String prefix) {
        return bucket.list(Storage.BlobListOption.currentDirectory(),
                Storage.BlobListOption.prefix(prefix)).iterateAll();
    }

    private Bucket getBucket(GoogleCredentials googleCredentials, String bucketName) {
        Storage storage = getStorage(googleCredentials);
        Bucket bucket = storage.get(bucketName);
        Assert.notNull(bucket, String.format("Required bucket %s does not exist.", bucketName));
        return bucket;
    }
}
