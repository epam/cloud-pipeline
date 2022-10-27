/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.resource;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class StaticResourcesService {

    private static final String DELIMITER = "/";

    private final DataStorageManager dataStorageManager;
    private final MessageHelper messageHelper;

    @SneakyThrows
    public DataStorageStreamingContent getContent(final String path) {
        Assert.isTrue(StringUtils.isNotBlank(path) && path.contains(DELIMITER),
                messageHelper.getMessage(MessageConstants.ERROR_STATIC_RESOURCES_INVALID_PATH));
        final String[] split = path.split(DELIMITER, 2);
        final String bucketName = split[0];
        final String filePath = split[1];
        Assert.isTrue(StringUtils.isNotBlank(filePath),
                messageHelper.getMessage(MessageConstants.ERROR_STATIC_RESOURCES_INVALID_PATH));
        final AbstractDataStorage storage = dataStorageManager.loadByNameOrId(bucketName);
        return dataStorageManager.getStreamingContent(storage.getId(), filePath, null);
    }
}
