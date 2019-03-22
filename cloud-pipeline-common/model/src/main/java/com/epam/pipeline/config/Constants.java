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

package com.epam.pipeline.config;

@SuppressWarnings("checkstyle:ConstantName")
public final class Constants {
    public static final String FMT_ISO_LOCAL_DATE = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String SECURITY_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String SIMPLE_DATE_FORMAT = "yyyyMMdd";
    public static final String SIMPLE_TIME_FORMAT = "HHmmss";
    public static final Integer SECONDS_IN_MINUTE = 60;
    public static final String PATH_DELIMITER = "/";
    public static final String X509_BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    public static final String X509_END_CERTIFICATE = "-----END CERTIFICATE-----";
    public static final String MODEL_PARAMETERS_FILE_NAME = "src/model_parameters.json";
    public static final String HTTP_AUTH_COOKIE = "HttpAuthorization";

    public static final String FIRECLOUD_TOKEN_HEADER = "Firecloud-Token";

    private Constants() {
        //no op
    }
}
