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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Util class to delete batch of S3 objects with respect to AWS limit for number of
 * deleted objects in one request. Note that to actually delete all keys, calling of method
 * close() is required.
 */
public class S3ObjectDeleter implements AutoCloseable {

    private static final int MAX_DELETE_REQUEST_SIZE = 1000;
    private AmazonS3 client;
    private List<DeleteObjectsRequest.KeyVersion> keysToDelete;
    private DeleteObjectsRequest deleteRequest;

    public S3ObjectDeleter(AmazonS3 client, String bucket) {
        this.client = client;
        this.keysToDelete = new ArrayList<>();
        this.deleteRequest = new DeleteObjectsRequest(bucket);
    }

    /**
     * Adds an object to a deletion queue, actual deletion is performed only if current queue
     * size exceeds the limit
     * @param key specifies object to delete
     * @param version optional, should be specified if a certain verion is deleted
     * @return true - if some object were deleted, false - if object was just added to deletion queue
     */
    public boolean deleteKey(String key, String version) {
        keysToDelete.add(new DeleteObjectsRequest.KeyVersion(key, version));
        if (keysToDelete.size() == MAX_DELETE_REQUEST_SIZE) {
            executeDeletion();
            return true;
        }
        return false;
    }

    public boolean deleteKey(String key) {
        return deleteKey(key, null);
    }

    /**
     * Finishes deletion process and deletes all objects left in queue
     */
    @Override
    public void close() {
        if (CollectionUtils.isEmpty(keysToDelete)) {
            return;
        }
        executeDeletion();
    }

    private void executeDeletion() {
        deleteRequest.setKeys(keysToDelete);
        client.deleteObjects(deleteRequest);
        keysToDelete = new ArrayList<>();
    }


}

