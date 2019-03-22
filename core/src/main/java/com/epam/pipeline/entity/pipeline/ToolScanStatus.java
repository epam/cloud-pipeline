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

package com.epam.pipeline.entity.pipeline;

/**
 * Describes the status of Tool security scan
 */
public enum ToolScanStatus {
    /**
     * The tool is pending scan. Security scan is a time costly operaion, so there's a queue for it.
     */
    PENDING(1),

    /**
     * Tool's security scan has been completed
     */
    COMPLETED(2),

    /**
     * Tool's security scan has failed for some reason
     */
    FAILED(3),


    /**
     * Tool's security scan never been executed,
     * WARNING: This value isn't stored in a database
     */
    NOT_SCANNED(4);

    private int code;

    public int getCode() {
        return code;
    }

    ToolScanStatus(int code) {
        this.code = code;
    }

    public static ToolScanStatus getByCode(int code) {
        return values()[code - 1];
    }
}
