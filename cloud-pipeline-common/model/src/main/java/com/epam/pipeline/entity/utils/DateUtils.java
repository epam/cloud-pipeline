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

package com.epam.pipeline.entity.utils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class DateUtils {

    /**
     * @deprecated Use static methods on the class itself rather than its instance.
     */
    @Deprecated
    public DateUtils() { }

    public static Date now() {
        return Date.from(nowUTC().toInstant(ZoneOffset.UTC));
    }

    public static LocalDateTime nowUTC() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    public static LocalDateTime toLocalDateTime(final Date date) {
        return date.toInstant().atZone(ZoneId.of("Z")).toLocalDateTime();
    }
}
