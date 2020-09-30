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

package com.epam.pipeline.cmd;

import java.util.Optional;

public class CmdExecutionException extends RuntimeException {

    public CmdExecutionException(String cmd, Throwable cause) {
        super(cmd, cause);
    }

    public CmdExecutionException(String cmd) {
        super(cmd);
    }

    public String getRootMessage() {
        return getRootMessage(this);
    }

    private String getRootMessage(final Throwable e) {
        return Optional.of(e)
                .map(Throwable::getCause)
                .map(e1 -> Optional.ofNullable(e.getMessage()).orElse("") + " -> " + getRootMessage(e1))
                .orElseGet(e::getMessage);
    }
}
