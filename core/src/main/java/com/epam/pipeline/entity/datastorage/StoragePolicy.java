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

package com.epam.pipeline.entity.datastorage;

import com.epam.pipeline.entity.datastorage.lifecycle.s3.S3StorageLifecyclePolicy;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class StoragePolicy {

    private Boolean versioningEnabled = false;
    private Integer backupDuration;

    /**
     * Use it only for backward capability, for new code please use storageLifecyclePolicy field
     * */
    @Deprecated
    private Integer shortTermStorageDuration;

    /**
     * Use it only for backward capability, for new code please use storageLifecyclePolicy field
     * */
    @Deprecated
    private Integer longTermStorageDuration;

    private Integer incompleteUploadCleanupDays;

    /**
     *  Represents Json object describes cloud-native storage lifecycle policy,
     *  f.e. {@link S3StorageLifecyclePolicy} for S3 Bucket.
     *  Stored as String here to be able to work flexible with different cloud policies.
     * */
    private String storageLifecyclePolicy;
    public boolean isVersioningEnabled() {
        return versioningEnabled != null && versioningEnabled;
    }
}
