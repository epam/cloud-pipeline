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

package com.epam.pipeline.test.creator.dts;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public final class DtsCreatorUtils {

    public static final TypeReference<Result<DtsDataStorageListing>> DTS_DATA_STORAGE_LISTING_TYPE =
            new TypeReference<Result<DtsDataStorageListing>>() {};
    public static final TypeReference<Result<DtsSubmission>> DTS_SUBMISSION_TYPE =
            new TypeReference<Result<DtsSubmission>>() {};
    public static final TypeReference<Result<DtsClusterConfiguration>> DTS_CLUSTER_CONFIG_TYPE =
            new TypeReference<Result<DtsClusterConfiguration>>() {};
    public static final TypeReference<Result<DtsRegistry>> DTS_REGISTRY_TYPE =
            new TypeReference<Result<DtsRegistry>>() {};
    public static final TypeReference<Result<List<DtsRegistry>>> DTS_REGISTRY_LIST_TYPE =
            new TypeReference<Result<List<DtsRegistry>>>() {};

    private DtsCreatorUtils() {

    }

    public static DtsDataStorageListing getDtsDataStorageListing() {
        return new DtsDataStorageListing();
    }

    public static DtsSubmission getDtsSubmission() {
        return new DtsSubmission();
    }

    public static DtsClusterConfiguration getDtsClusterConfiguration() {
        return new DtsClusterConfiguration();
    }

    public static DtsRegistry getDtsRegistry() {
        return new DtsRegistry();
    }

    public static DtsRegistryVO getDtsRegistryVO() {
        return new DtsRegistryVO();
    }
}
