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

package com.epam.pipeline.manager.cloud.commands;

import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Builder
public class NodeUpCommand extends AbstractClusterCommand {

    private static final int MIN_LENGTH = 18;

    private final String executable;
    private final String script;
    private final String runId;
    private final String sshKey;
    private final String instanceImage;
    private final String instanceType;
    private final String instanceDisk;
    private final String kubeIP;
    private final String kubeToken;
    private final String region;
    private final String encryptionKey;
    private final boolean isSpot;
    private final String bidPrice;

    @Override
    protected List<String> buildCommandArguments() {
        final List<String> commands = new ArrayList<>(MIN_LENGTH);
        commands.add(executable);
        commands.add(script);
        commands.add(RUN_ID_PARAMETER);
        commands.add(runId);
        commands.add("--ins_key");
        commands.add(sshKey);
        commands.add("--ins_img");
        commands.add(instanceImage);
        commands.add("--ins_type");
        commands.add(instanceType);
        commands.add("--ins_hdd");
        commands.add(instanceDisk);
        commands.add("--kube_ip");
        commands.add(kubeIP);
        commands.add("--kubeadm_token");
        commands.add(kubeToken);
        commands.add(REGION_PARAMETER);
        commands.add(region);
        if (StringUtils.isNotBlank(encryptionKey)) {
            commands.add("--kms_encyr_key_id");
            commands.add(encryptionKey);
        }
        if (isSpot) {
            commands.add("--is_spot");
            commands.add("True");
            commands.add("--bid_price");
            commands.add(bidPrice == null ? "" : String.valueOf(bidPrice));
        }
        return commands;
    }
}
