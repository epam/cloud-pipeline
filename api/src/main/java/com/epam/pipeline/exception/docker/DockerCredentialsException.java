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

package com.epam.pipeline.exception.docker;

import org.apache.commons.lang3.StringUtils;

public class DockerCredentialsException extends RuntimeException {

    public DockerCredentialsException(String url, String userName, String password) {
        super(getMessageText(url, userName, password));
    }

    public DockerCredentialsException(String url, String userName, String password, Throwable cause) {
        super(getMessageText(url, userName, password), cause);
    }

    private static String getMessageText(String url, String userName, String password) {
        if (StringUtils.isEmpty(userName)) {
            return String.format("Failed to authenticate into docker registry '%s'. "
                    + "Provide user name and password for authentication.", url);
        }
        return String.format("Failed to authenticate into docker registry '%s' "
                + "with user name '%s' and password '%s'. "
                + "Provide user name and password for authentication.", url, userName, password);
    }
}
