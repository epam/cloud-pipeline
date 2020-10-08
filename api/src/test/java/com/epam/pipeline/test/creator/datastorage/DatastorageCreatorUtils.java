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

package com.epam.pipeline.test.creator.datastorage;

import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;

public final class DatastorageCreatorUtils {

    private DatastorageCreatorUtils() {

    }

    public static S3bucketDataStorage getS3bucketDataStorage() {
        return new S3bucketDataStorage(ID, TEST_STRING, TEST_STRING);
    }
}
