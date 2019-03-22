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

package com.epam.pipeline.entity.pipeline;

import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * Created by Mariia_Zueva on 5/29/2017.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RunInstance {

    private String nodeType;
    /**
     * Node size the was requested by user
     */
    private Integer nodeDisk;
    /**
     * Real requested node disk size (adjusted to docker size image).
     * In common case it will be nodeDisk + dockerSize * 3
     */
    private Integer effectiveNodeDisk;
    private String nodeIP;
    private String nodeId;
    private String nodeImage;
    private String nodeName;
    private Boolean spot;
    private Long cloudRegionId;
    private CloudProvider cloudProvider;

    @JsonIgnore
    public boolean isEmpty() {
        return nodeType == null && (nodeDisk == null || nodeDisk <= 0)
                && nodeIP == null && nodeId == null && nodeImage == null
                && spot == null && cloudRegionId == null && !StringUtils.hasText(nodeName);
    }

    public boolean requirementsMatch(RunInstance other) {
        if (other == null) {
            return false;
        }
        if (!Objects.equals(this.nodeType, other.nodeType)) {
            return false;
        }
        if (!Objects.equals(this.effectiveNodeDisk, other.effectiveNodeDisk)) {
            return false;
        }
        if (!Objects.equals(this.nodeImage, other.nodeImage)) {
            return false;
        }
        if (!Objects.equals(this.spot, other.spot)) {
            return false;
        }
        return Objects.equals(this.cloudRegionId, other.cloudRegionId);
    }
}
