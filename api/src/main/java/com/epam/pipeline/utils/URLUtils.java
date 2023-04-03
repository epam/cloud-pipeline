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

package com.epam.pipeline.utils;

public final class URLUtils {

    private static final String SLASH = "/";
    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final String PORT_DELIMITER = ":";

    private URLUtils() {
        //no op
    }

    public static String getUrlWithoutTrailingSlash(String url) {
        return url.endsWith(SLASH) ?
                url.substring(0, url.length() - 1) : url;
    }

    public static String getUrlWithTrailingSlash(String url) {
        return url.endsWith(SLASH) ? url : url + SLASH;
    }

    public static String getHost(String url) {
        return url.trim()
                .replace(HTTP, "")
                .replace(HTTPS, "")
                .split(SLASH)[0]
                .split(PORT_DELIMITER)[0];
    }
}
