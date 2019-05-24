/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.controller;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import com.epam.pipeline.external.datastorage.message.MessageHelper;

@ControllerAdvice
public class ExceptionHandlerAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);

    @Autowired
    private MessageHelper messageHelper;

    @ResponseBody
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ExceptionHandler(Throwable.class)
    public final ResponseEntity<Result<String>> handleUncaughtException(final Throwable exception, final WebRequest
            request) {
        // adds information about encountered error to application log
        LOG.error(messageHelper.getMessage("logger.error", request.getDescription(true)), exception);
        HttpStatus code = HttpStatus.OK;

        String message = exception.getMessage();

        return new ResponseEntity<>(Result.error(StringUtils.defaultString(StringUtils.trimToNull(message),
                messageHelper.getMessage("error.default"))), code);
    }
}
