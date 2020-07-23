/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper;

import java.util.Collections;
import java.util.List;

import com.epam.pipeline.controller.vo.configuration.RunConfigurationVO;
import com.epam.pipeline.controller.vo.configuration.RunConfigurationWithEntitiesVO;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.pipeline.Folder;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class AbstractRunConfigurationMapper {

    @Mapping(target = "parentId", expression = "java(fillParentFolderId(runConfiguration))")
    public abstract RunConfigurationVO toRunConfigurationVO(RunConfiguration runConfiguration);

    @Mapping(target = "parentId", expression = "java(fillParentFolderId(runConfiguration))")
    @Mapping(target = "metadataClass", ignore = true)
    @Mapping(target = "entitiesIds", ignore = true)
    @Mapping(target = "folderId", ignore = true)
    public abstract RunConfigurationWithEntitiesVO toRunConfigurationWithEntitiesVO(RunConfiguration runConfiguration);

    @Mapping(target = "entries", expression = "java(fillEntries(runConfigurationVO))")
    @Mapping(target = "parent", expression = "java(fillParentFolder(runConfigurationVO))")
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "mask", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "locked", ignore = true)
    public abstract RunConfiguration toRunConfiguration(RunConfigurationVO runConfigurationVO);

    Long fillParentFolderId(RunConfiguration runConfiguration) {
        return runConfiguration.getParent() == null ? null : runConfiguration.getParent().getId();
    }

    Folder fillParentFolder(RunConfigurationVO runConfigurationVO) {
        return runConfigurationVO.getParentId() != null ? new Folder(runConfigurationVO.getParentId()) : null;
    }

    List<AbstractRunConfigurationEntry> fillEntries(RunConfigurationVO runConfigurationVO) {
        return CollectionUtils.isNotEmpty(runConfigurationVO.getEntries())
                ? runConfigurationVO.getEntries() :
                Collections.emptyList();
    }
}
