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
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.exception.StorageForbiddenOperationException;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This aspect controls 'data-leakage' policy
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLeakagePolicyAspect {

    private final MessageHelper messageHelper;
    private final DataStorageManager storageManager;
    private final PipelineRunManager runManager;

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

    @Before("@annotation(com.epam.pipeline.manager.datastorage.leakagepolicy.SensitiveStorageOperation) && " +
            "args(actions,..)")
    public void checkAccessFromSensitiveRun(final JoinPoint joinPoint,
                                            final List<DataStorageAction> actions) {
        if (CollectionUtils.isEmpty(actions)) {
            return;
        }
        final boolean sensitiveContext = isRequestContextSensitive();
        log.debug("Processing request for {}sensitive context", sensitiveContext ? "" : "non-");
        final List<AbstractDataStorage> storages = actions.stream()
                .map(action -> storageManager.load(action.getId()))
                .collect(Collectors.toList());
        final boolean sensitiveRequest = storages.stream().anyMatch(AbstractDataStorage::isSensitive);
        log.debug("Sensitive data is{} requested.", sensitiveRequest ? "" : " not");
        if (!sensitiveContext && sensitiveRequest) {
            if (actions.stream().allMatch(DataStorageAction::isListOnly)) {
                log.debug("Listing-only request for sensitive data is allowed for non-sensitive request.");
                return;
            }
            log.debug("Sensitive data is requested outside of sensitive run. Request is forbidden.");
            throw new StorageForbiddenOperationException(messageHelper.getMessage(
                    MessageConstants.ERROR_SENSITIVE_REQUEST_WRONG_CONTEXT));
        }
        if (sensitiveContext && actions.stream()
                .anyMatch(action -> action.isWrite() || action.isWriteVersion())) {
            log.debug("Write operation is requested for sensitive context. Request is forbidden.");
            throw new StorageForbiddenOperationException(messageHelper.getMessage(
                    MessageConstants.ERROR_SENSITIVE_WRITE_FORBIDDEN));
        }
    }

    private boolean isRequestContextSensitive() {
        final RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        if (requestAttributes == null) {
            return false;
        }
        final HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        final String ip = request.getRemoteAddr();
        log.debug("Processing request from IP: {}", ip);
        return runManager.loadActiveRunsByPodIP(ip)
                .map(PipelineRun::getSensitive)
                .orElse(false);
    }
}
