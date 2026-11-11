/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.async;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.async.AsyncTaskParams;
import com.epam.pipeline.manager.security.storage.StoragePermissionManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AsyncTaskParamsValidator {

    private static final String SLASH = "/";
    private static final String DOUBLE_SLASH = "//";
    private static final String CURRENT_DIRECTORY = ".";
    private static final String PARENT_DIRECTORY = "..";
    private static final String HOME = "~";

    private static final Pattern FORBIDDEN_IN_PATH = Pattern.compile("\\p{Cntrl}|\\R");

    private final StoragePermissionManager storagePermissionManager;
    private final MessageHelper messageHelper;

    public void assertStoragePermission(final Long storageId, final String permission) {
        Assert.isTrue(storagePermissionManager.hasStoragePermission(storageId, permission),
                messageHelper.getMessage(MessageConstants.ERROR_PERMISSION_IS_NOT_GRANTED,
                        storageId, permission));
    }

    public String asFilePath(final String requested) {
        Assert.hasText(requested, messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_PATH_NOT_A_FILE, requested));
        assertSafePath(requested);
        Assert.isTrue(!requested.endsWith(SLASH), messageHelper.getMessage(
                MessageConstants.ERROR_ASYNC_TASK_PATH_NOT_A_FILE, requested));
        return requested;
    }

    public String asFolderPrefix(final String requested) {
        if (StringUtils.isBlank(requested)) {
            return StringUtils.EMPTY;
        }
        assertSafePath(requested);
        return requested.endsWith(SLASH) ? requested : requested + SLASH;
    }

    private void assertSafePath(final String path) {
        final String invalid = messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_PATH_INVALID, path);
        final String notCanonical = messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_PATH_NOT_CANONICAL,
                path);

        Assert.isTrue(!path.startsWith(SLASH), notCanonical);
        Assert.isTrue(!path.contains(DOUBLE_SLASH), notCanonical);

        final String[] components = StringUtils.split(path, SLASH);
        Assert.isTrue(Arrays.stream(components).noneMatch(CURRENT_DIRECTORY::equals), notCanonical);

        Assert.isTrue(Arrays.stream(components).noneMatch(PARENT_DIRECTORY::equals), invalid);
        Assert.isTrue(!path.startsWith(HOME), invalid);
        Assert.isTrue(!FORBIDDEN_IN_PATH.matcher(path).find(), invalid);
    }

    public String fieldRequiredMessage(final AsyncTaskParams params, final String field) {
        return messageHelper.getMessage(MessageConstants.ERROR_ASYNC_TASK_FIELD_REQUIRED,
                field, params.getType());
    }
}
