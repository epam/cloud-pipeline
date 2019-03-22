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

package com.epam.pipeline.assertions;

import com.epam.pipeline.assertions.base.BaseEntityAssert;
import com.epam.pipeline.assertions.base.SecuredEntityAssert;
import com.epam.pipeline.assertions.datastorage.DataStorageAssert;
import com.epam.pipeline.assertions.datastorage.S3StorageAssert;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;

public final class ProjectAssertions {

    private ProjectAssertions() {
        //no op
    }

    public static BaseEntityAssert assertThat(BaseEntity actual) {
        return new BaseEntityAssert(actual);
    }

    public static SecuredEntityAssert assertThat(AbstractSecuredEntity actual) {
        return new SecuredEntityAssert(actual);
    }

    public static DataStorageAssert assertThat(AbstractDataStorage actual) {
        return new DataStorageAssert(actual);
    }

    public static S3StorageAssert assertThat(S3bucketDataStorage actual) {
        return new S3StorageAssert(actual);
    }
}
