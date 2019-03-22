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

package com.epam.pipeline.entity.cluster;

import lombok.Getter;
import lombok.Setter;

import io.fabric8.kubernetes.api.model.NodeSystemInfo;

@Getter
@Setter
public class NodeInstanceSystemInfo {
    private String machineID;
    private String systemUUID;
    private String bootID;
    private String kernelVersion;
    private String osImage;
    private String operatingSystem;
    private String architecture;
    private String containerRuntimeVersion;
    private String kubeletVersion;
    private String kubeProxyVersion;

    public NodeInstanceSystemInfo() {
    }

    public NodeInstanceSystemInfo(NodeSystemInfo info) {
        this();
        this.setMachineID(info.getMachineID());
        this.setSystemUUID(info.getSystemUUID());
        this.setBootID(info.getBootID());
        this.setKernelVersion(info.getKernelVersion());
        this.setOsImage(info.getOsImage());
        this.setOperatingSystem(info.getOperatingSystem());
        this.setArchitecture(info.getArchitecture());
        this.setContainerRuntimeVersion(info.getContainerRuntimeVersion());
        this.setKubeletVersion(info.getKubeletVersion());
        this.setKubeProxyVersion(info.getKubeProxyVersion());
    }
}
