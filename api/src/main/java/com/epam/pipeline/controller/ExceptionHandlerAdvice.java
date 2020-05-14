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

package com.epam.pipeline.controller;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.exception.StorageForbiddenOperationException;
import com.epam.pipeline.exception.docker.DockerAuthorizationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.io.FileNotFoundException;
import java.sql.SQLException;

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

        String message;
        if (exception instanceof FileNotFoundException) {
            // any details about real path of a resource should be normally prevented to send to the client
            message = messageHelper.getMessage("error.io.not.found");
        } else if (exception instanceof DataAccessException) {
            // any details about data access error should be normally prevented to send to the client,
            // as its message can contain information about failed SQL query or/and database schema
            if (exception instanceof BadSqlGrammarException) {
                // for convenience we need to provide detailed information about occurred BadSqlGrammarException,
                // but it can be retrieved
                SQLException root = ((BadSqlGrammarException) exception).getSQLException();
                if (root.getNextException() != null) {
                    LOG.error(messageHelper.getMessage("logger.error.root.cause", request.getDescription(true)),
                            root.getNextException());
                }
                message = messageHelper.getMessage("error.sql.bad.grammar");
            } else {
                message = messageHelper.getMessage("error.sql");
            }
        } else if (exception instanceof DockerAuthorizationException) {
            return new ResponseEntity<>(Result.error(exception.getMessage()), HttpStatus.UNAUTHORIZED);
        } else if (exception instanceof StorageForbiddenOperationException) {
            return new ResponseEntity<>(Result.error(exception.getMessage()), HttpStatus.FORBIDDEN);
        } else {
            message = exception.getMessage();
        }

        return new ResponseEntity<>(Result.error(StringUtils.defaultString(StringUtils.trimToNull(message),
                messageHelper.getMessage("error.default"))), HttpStatus.OK);
    }
}
