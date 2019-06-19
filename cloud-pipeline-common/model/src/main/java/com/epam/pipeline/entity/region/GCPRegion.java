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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Google Cloud Platform region. Represents a zone inside of one of GCP Regions.
 * Holds settings and authorization options
 * related to GCP deployment.
 */
@NoArgsConstructor
@Getter
@Setter
public class GCPRegion extends AbstractCloudRegion {

    private CloudProvider provider = CloudProvider.GCP;
    /**
     * Optional path to service account secret json file, if
     * it is not specified, APPLICATION_DEFAULT credentials will
     * be used for authorization
     */
    private String authFile;
    private String sshPublicKeyPath;
    private String project;
    private String applicationName;
    @JsonProperty("tempCredentialsRole")
    private String impersonatedAccount;
    private List<GCPCustomInstanceType> customInstanceTypes;
    private String corsRules;
    private String policy;
    private Integer backupDuration;
    private boolean versioningEnabled;
}
