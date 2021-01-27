/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.credentials;

import com.epam.pipeline.dto.cloud.credentials.AbstractCloudProfileCredentials;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.cloud.CloudAwareService;

public interface CloudProfileCredentialsManager<T extends AbstractCloudProfileCredentials> extends CloudAwareService {

    T create(T credentials);

    T update(Long id, T credentials);

    TemporaryCredentials generateProfileCredentials(T credentials, AbstractCloudRegion region);
}
