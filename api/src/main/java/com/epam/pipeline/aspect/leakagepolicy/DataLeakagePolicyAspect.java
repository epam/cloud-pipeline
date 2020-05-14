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

package com.epam.pipeline.aspect.leakagepolicy;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.exception.StorageForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * This aspect controls 'data-leakage' policy
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DataLeakagePolicyAspect {

    private final MessageHelper messageHelper;

    @Before("@annotation(com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation) && " +
            "args(dataStorage,..)")
    public void proceedSensitiveStorageOperation(final JoinPoint joinPoint,
                                                 final AbstractDataStorage dataStorage) {
        if (dataStorage.isSensitive()) {
            throw new StorageForbiddenOperationException(messageHelper.getMessage(
                    MessageConstants.ERROR_SENSITIVE_DATASTORAGE_OPERATION,
                    dataStorage.getName(), dataStorage.getType()));
        }
    }
}
