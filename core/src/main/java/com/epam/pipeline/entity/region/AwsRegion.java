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

package com.epam.pipeline.entity.region;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Amazon cloud region.
 *
 * Some of the entity fields are perpetual and cannot be changed since the region is created in dao.
 *
 * Amazon cloud region perpetual fields are: {@link AbstractCloudRegion#getRegionCode()}, {@link #profile}.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AwsRegion extends AbstractCloudRegion implements VersioningAwareRegion {

    private CloudProvider provider = CloudProvider.AWS;
    private String corsRules;
    private String policy;
    private String kmsKeyId;
    private String kmsKeyArn;
    private String profile;
    //Not empty
    private String sshKeyName;
    //Not empty
    private String tempCredentialsRole;
    private String omicsServiceRole;
    private String omicsEcrUrl;
    private Integer backupDuration;
    private boolean versioningEnabled;
    private String iamRole;
    private String s3Endpoint;
    /**
     * Shall be enabled for the {@link #s3Endpoint} which validates a request signature against a query string
     * exactly as it was received. Otherwise any request with a space, an asterisk or a tilde
     * in a query parameter fails with 403 SignatureDoesNotMatch, f.e. a listing of a folder
     * which name contains a space.
     *
     * The option is configurable using API only, therefore it is nullable to be kept as it is
     * if an update request does not specify it.
     */
    private Boolean s3SendQueryAsSigned;
}
