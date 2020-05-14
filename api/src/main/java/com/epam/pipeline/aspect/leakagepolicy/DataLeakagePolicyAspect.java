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
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * This aspect controls 'data-leakage' policy
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DataLeakagePolicyAspect {

    private final MessageHelper messageHelper;

    @Around("execution(@com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation * *(..))")
    public void proceedSensitiveStorageOperation(final ProceedingJoinPoint joinPoint) throws Throwable {
        Stream.of(joinPoint.getArgs())
            .filter(AbstractDataStorage.class::isInstance)
            .map(AbstractDataStorage.class::cast)
            .forEach(this::assertStorageIsNotSensitive);
        joinPoint.proceed();
    }

    public void assertStorageIsNotSensitive(final AbstractDataStorage storage) {
        if (storage.isSensitive()) {
            throw new IllegalArgumentException(messageHelper
                                                   .getMessage(MessageConstants.ERROR_SENSITIVE_DATASTORAGE_OPERATION,
                                                               storage.getId(), storage.getType()));
        }
    }
}
