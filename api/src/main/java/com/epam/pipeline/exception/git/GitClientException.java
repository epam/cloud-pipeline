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

package com.epam.pipeline.exception.git;

/**
 * Created by kite on 21.03.17.
 */
public class GitClientException extends Exception {
    public GitClientException() {
    }

    public GitClientException(String message) {
        super(message);
    }

    public GitClientException(String message, Throwable cause) {
        super("Exception while trying to connect to Gitlab API: " + message, cause);
    }
    public GitClientException(Throwable cause) {
        super("Exception while trying to connect to Gitlab API", cause);
    }
}
