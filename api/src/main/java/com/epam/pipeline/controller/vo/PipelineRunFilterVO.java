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

package com.epam.pipeline.controller.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.epam.pipeline.entity.filter.AclSecuredFilter;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;

@Getter
@Setter
public class PipelineRunFilterVO implements AclSecuredFilter {
    private List<Long> pipelineIds;
    private List<String> versions;
    private List<TaskStatus> statuses;
    private Date startDateFrom;
    private Date endDateTo;
    private String partialParameters;
    private Long parentId;
    private List<String> owners;
    private String ownershipFilter;
    private List<Long> entitiesIds;
    private List<Long> configurationIds;
    private List<Long> projectIds;
    private List<String> dockerImages;

    private boolean userModified = true;

    //these filters are used for ACL filtering
    @JsonIgnore
    private List<Long> allowedPipelines;
    @JsonIgnore
    public boolean isEmpty() {
        return areSimpleArgumentsEmpty() && areGroupArgumentsEmpty();
    }

    /**
     *  Grouping is supported only if 'status' filter is provided or ACL filters
     *  (allowedPipelines and ownershipFilter)
     * @return
     */
    public boolean useGrouping() {
        return !userModified || isEmpty();
    }

    private boolean areGroupArgumentsEmpty() {
        return CollectionUtils.isEmpty(statuses) && CollectionUtils.isEmpty(allowedPipelines)
                && ownershipFilter == null;
    }

    private boolean areSimpleArgumentsEmpty() {
        return CollectionUtils.isEmpty(pipelineIds) && CollectionUtils.isEmpty(versions)
                && startDateFrom == null && endDateTo == null && partialParameters == null
                && parentId == null && CollectionUtils.isEmpty(owners)
                && CollectionUtils.isEmpty(configurationIds) && CollectionUtils.isEmpty(entitiesIds)
                && CollectionUtils.isEmpty(projectIds);
    }

    @Data
    @AllArgsConstructor
    public static class ProjectFilter {
        private List<Long> pipelineIds;
        private List<Long> configurationIds;

        public ProjectFilter() {
            this.pipelineIds = new ArrayList<>();
            this.configurationIds = new ArrayList<>();
        }

        public boolean isEmpty() {
            return CollectionUtils.isEmpty(pipelineIds) && CollectionUtils.isEmpty(configurationIds);
        }
    }
}
