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

package com.epam.pipeline.dts.common.rest;

import io.swagger.annotations.ApiModelProperty;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
public final class Result<T> {

    private T payload;

    private String message;

    private ResultStatus status;

    private Result(final ResultStatus status, final T payload) {
        this(status, null, payload);
    }

    private Result(final ResultStatus status, final String message, final T payload) {
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    /**
     * @return Payload of a service response
     */
    public T getPayload() {
        return payload;
    }

    /**
     * @return Message of a service response
     */
    public String getMessage() {
        return message;
    }

    @ApiModelProperty(value = "it defines the status with which an operation may result in",
            allowableValues = "OK, INFO, WARN, ERROR", required = true)
    public ResultStatus getStatus() {
        return status;
    }

    /**
     * Create a Result object with status {@code ResultStatus.OK} and desired payload
     * @param payload the payload of a Result object
     * @param <T> the class of the payload
     * @return a new Result object with status OK
     */
    public static <T> Result<T> success(final T payload) {
        return new Result<>(ResultStatus.OK, payload);
    }

    /**
     * Create a Result object with status {@code ResultStatus.OK}, desired payload and message
     * @param payload payload of a Result object
     * @param message message of a Result object
     * @param <T> the class of the payload
     * @return a new Result object with status OK
     */
    public static <T> Result<T> success(final T payload, final String message) {
        return new Result<>(ResultStatus.OK, message, payload);
    }

    /**
     * Create a Result object with status {@code ResultStatus.ERROR} and a desired message
     * @param message message of a Result object
     * @param <T> class of the payload
     * @return  a new Result object with status ERROR
     */
    public static <T> Result<T> error(final String message) {
        return result(ResultStatus.ERROR, message, null);
    }

    /**
     * Create a Result object with status {@code ResultStatus.INFO}, desired payload and message
     * @param payload payload of a Result object
     * @param message message of a Result object
     * @param <T> class of the payload
     * @return  a new Result object with status INFO
     */
    public static <T> Result<T> info(final String message, final T payload) {
        return result(ResultStatus.INFO, message, payload);
    }

    /**
     * Create a Result object with status {@code ResultStatus.WARN}, desired payload and message
     * @param payload payload of a Result object
     * @param message message of a Result object
     * @param <T> class of the payload
     * @return  a new Result object with status WARN
     */
    public static <T> Result<T> warn(final String message, final T payload) {
        return result(ResultStatus.WARN, message, payload);
    }

    /**
     * Create a Result object with status {@code ResultStatus.ERROR}, desired payload and message
     * @param payload payload of a Result object
     * @param message message of a Result object
     * @param <T> class of the payload
     * @return  a new Result object with status ERROR
     */
    public static <T> Result<T> error(final String message, final T payload) {
        return result(ResultStatus.ERROR, message, payload);
    }

    /**
     * Create a Result object with desired status, payload and message
     * @param status status of a Result object
     * @param payload payload of a Result object
     * @param message message of a Result object
     * @param <T> class of the payload
     * @return  a new Result object with status INFO
     */
    public static <T> Result<T> result(final ResultStatus status, final String message, final T payload) {
        return new Result<>(status, message, payload);
    }
}

