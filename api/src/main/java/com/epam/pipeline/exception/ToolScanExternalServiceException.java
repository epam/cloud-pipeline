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

package com.epam.pipeline.exception;

import com.epam.pipeline.entity.pipeline.Tool;

/**
 * An exception, related to sending images to Clair for security validation.
 */
public class ToolScanExternalServiceException extends Exception {
    private static final String EXCEPTION_TEMPLATE_1 =
            "Error while sending a request to External scan service. Tool: %s";
    private static final String EXCEPTION_TEMPLATE_2 =
            "Error while sending a request to External scan service. Tool: %s. Reason: %s";

    public ToolScanExternalServiceException(Tool tool, Throwable cause) {
        super(String.format(EXCEPTION_TEMPLATE_1, tool.getImage()), cause);
    }

    public ToolScanExternalServiceException(Tool tool, String message, Throwable cause) {
        super(String.format(EXCEPTION_TEMPLATE_2, tool.getImage(), message), cause);
    }

    public ToolScanExternalServiceException(Tool tool, String message) {
        super(String.format(EXCEPTION_TEMPLATE_2, tool.getImage(), message));
    }
}
