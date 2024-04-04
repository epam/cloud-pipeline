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

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

public final class URLUtils {

    private URLUtils() {
        //no op
    }

    public static String normalizeUrl(String url) {
        Assert.state(StringUtils.isNotBlank(url), "Url shall be specified");
        return url.endsWith("/") ? url : url + "/";
    }

    public static String getUrlWithoutTrailingSlash(String url) {
        return url.endsWith("/") ?
                url.substring(0, url.length() - 1) : url;
    }
}
