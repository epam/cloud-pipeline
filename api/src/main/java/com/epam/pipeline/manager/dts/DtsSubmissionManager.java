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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DtsSubmissionManager {

    private final DtsRegistryManager dtsRegistryManager;
    private final DtsClientBuilder clientBuilder;
    private final AuthManager authManager;

    public DtsSubmission createSubmission(Long dtsId, DtsSubmission dtsSubmission) {
        DtsClient dtsClient = getDtsClient(dtsId);
        return DtsClient.executeRequest(dtsClient.createSubmission(dtsSubmission)).getPayload();
    }

    public DtsSubmission findSubmission(Long dtsId, Long runId) {
        DtsClient dtsClient = getDtsClient(dtsId);
        return DtsClient.executeRequest(dtsClient.findSubmission(runId)).getPayload();
    }

    public DtsClusterConfiguration getClusterConfiguration(Long dtsId) {
        return DtsClient.executeRequest(getDtsClient(dtsId).getClusterConfiguration()).getPayload();
    }

    public DtsSubmission stopSubmission(Long dtsId, Long runId) {
        DtsClient dtsClient = getDtsClient(dtsId);
        return DtsClient.executeRequest(dtsClient.stopSubmission(runId)).getPayload();
    }

    private DtsClient getDtsClient(Long dtsId) {
        DtsRegistry dtsRegistry = dtsRegistryManager.load(dtsId);
        return clientBuilder.createDtsClient(dtsRegistry.getUrl(),
                authManager.issueTokenForCurrentUser().getToken());
    }

}
