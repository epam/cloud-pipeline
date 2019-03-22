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


public abstract class AbstractRestController {

    public static final int BUF_SIZE = 2 * 1024;
    /**
     * Declares HTTP status OK code value, used to specify this code when REST API
     * is described, using Swagger-compliant annotations. It allows create nice
     * documentation automatically.
     */
    public static final int HTTP_STATUS_OK = 200;

    /**
     * {@code String} specifies API responses description that explains meaning of different values
     * for $.status JSON path. It's required and used with swagger ApiResponses annotation.
     */
    public static final String API_STATUS_DESCRIPTION =
            "It results in a response with HTTP status OK, but "
                    + "you should always check $.status, which can take several values:<br/>"
                    + "<b>OK</b> means call has been done without any problems;<br/>"
                    + "<b>ERROR</b> means call has been aborted due to errors (see $.message "
                    + "for details in this case).";

}
