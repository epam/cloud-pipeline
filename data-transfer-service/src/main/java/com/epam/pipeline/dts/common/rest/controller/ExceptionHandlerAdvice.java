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

package com.epam.pipeline.dts.common.rest.controller;

import com.epam.pipeline.dts.common.rest.Result;
import com.epam.pipeline.dts.listing.exception.ForbiddenException;
import com.epam.pipeline.dts.listing.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@ControllerAdvice
public class ExceptionHandlerAdvice {

    @ResponseBody
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ExceptionHandler(Throwable.class)
    public final ResponseEntity<Result<String>> handleUncaughtException(final Throwable exception,
                                                                        final WebRequest request) {
        // adds information about encountered error to application log
        logError(exception, request);
        return new ResponseEntity<>(Result.error(exception.getMessage()), HttpStatus.OK);
    }

    @ResponseBody
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ExceptionHandler(ForbiddenException.class)
    public final ResponseEntity<Result<String>> handleForbiddenExeception(final ForbiddenException exception,
                                                                          final WebRequest request) {
        logError(exception, request);
        return new ResponseEntity<>(Result.error(exception.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ResponseBody
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ExceptionHandler(NotFoundException.class)
    public final ResponseEntity<Result<String>> handleNotFoundExeception(final NotFoundException exception,
                                                                         final WebRequest request) {
        logError(exception, request);
        return new ResponseEntity<>(Result.error(exception.getMessage()), HttpStatus.NOT_FOUND);
    }

    private void logError(final Throwable exception, final WebRequest request) {
        log.error("An error during request processing {}", request.getDescription(true));
        log.error(exception.getMessage(), exception);
    }
}
