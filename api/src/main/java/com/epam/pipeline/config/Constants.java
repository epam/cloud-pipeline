/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.config;


public final class Constants {
    public static final String FMT_ISO_LOCAL_DATE = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String TIME_FORMAT = "HH:mm:ss";
    public static final String ELASTIC_DATE_TIME_FORMAT = FMT_ISO_LOCAL_DATE;
    public static final String EXPORT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String SECURITY_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String SIMPLE_DATE_FORMAT = "yyyyMMdd";
    public static final String SIMPLE_TIME_FORMAT = "HHmmss";
    public static final String PATH_DELIMITER = "/";
    public static final String X509_BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    public static final String X509_END_CERTIFICATE = "-----END CERTIFICATE-----";
    public static final String COMMA = ",";
    public static final String NEWLINE = "\n";
    public static final String DOT = ".";
    public static final String SPACE = " ";
    public static final String COLON = ":";

    public static final Integer SECONDS_IN_MINUTE = 60;
    public static final Integer MINUTES_IN_HOUR = 60;
    public static final Integer HOURS_IN_DAY = 24;
    public static final Integer SECONDS_IN_DAY = SECONDS_IN_MINUTE * MINUTES_IN_HOUR * HOURS_IN_DAY;
    public static final Integer MILLISECONDS_IN_DAY = SECONDS_IN_DAY * 1000;

    public static final String FIRECLOUD_TOKEN_HEADER = "Firecloud-Token";

    private Constants() {
        //no op
    }
}
