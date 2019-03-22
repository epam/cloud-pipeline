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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

@Getter
@Setter
public class Folder extends AbstractHierarchicalEntity {

    private Long parentId;
    private Folder parent;
    private List<Pipeline> pipelines;
    private List<AbstractDataStorage> storages;
    private List<RunConfiguration> configurations;
    private List<Folder> childFolders;
    private Map<String, Integer> metadata;
    private final AclClass aclClass = AclClass.FOLDER;
    private boolean hasMetadata;


    public Folder() {
        this.pipelines = new ArrayList<>();
        this.childFolders = new ArrayList<>();
        this.storages = new ArrayList<>();
        this.configurations = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    public Folder(Long id) {
        this();
        setId(id);
    }

    /**
     * Copy constructor.
     */
    private Folder(Folder folder) {
        this.setId(folder.getId());
        this.setName(folder.getName());
        this.setOwner(folder.getOwner());
        this.setMask(folder.getMask());
        this.parentId = folder.getParentId();
        this.parent = folder.getParent();
        this.pipelines =  new ArrayList<>(folder.getPipelines());
        this.childFolders = folder.getChildFolders().stream().map(Folder::copy).collect(Collectors.toList());
        this.storages = new ArrayList<>(folder.getStorages());
        this.configurations = new ArrayList<>(folder.getConfigurations());
        this.metadata = new HashMap<>(folder.getMetadata());
    }

    public Folder copy() {
        return new Folder(this);
    }

    /**
     * Collects all child entity ids hierarchically
     * @return
     */
    public Map<AclClass, List<Long>> collectChildren() {
        Map<AclClass, List<Long>> result = new HashMap<>();
        addChildren(result, pipelines, AclClass.PIPELINE);
        addChildren(result, storages, AclClass.DATA_STORAGE);
        addChildren(result, childFolders, AclClass.FOLDER);
        addChildren(result, configurations, AclClass.CONFIGURATION);
        if (!CollectionUtils.isEmpty(childFolders)) {
            childFolders.forEach(folder -> {
                Map<AclClass, List<Long>> inner = folder.collectChildren();
                if (inner != null) {
                    inner.forEach((key, value) -> result.compute(key, (aclClass, old) -> {
                        if (old == null) {
                            return value;
                        } else {
                            old.addAll(value);
                            return old;
                        }
                    }));
                }
            });
        }
        return result;
    }


    @Override
    @JsonIgnore
    public List<AbstractSecuredEntity> getLeaves() {
        List<AbstractSecuredEntity> result =
                new ArrayList<>(pipelines.size() + storages.size() + configurations.size());
        result.addAll(pipelines);
        result.addAll(storages);
        result.addAll(configurations);
        return result;
    }

    @Override
    @JsonIgnore
    public List<AbstractHierarchicalEntity> getChildren() {
        return new ArrayList<>(childFolders);
    }

    @Override
    public void filterLeaves(Map<AclClass, Set<Long>> idToRemove) {
        pipelines = filterCollection(pipelines, AclClass.PIPELINE, idToRemove);
        storages = filterCollection(storages, AclClass.DATA_STORAGE, idToRemove);
        configurations = filterCollection(configurations, AclClass.CONFIGURATION, idToRemove);
    }

    @Override
    public void filterChildren(Map<AclClass, Set<Long>> idToRemove) {
        childFolders = filterCollection(childFolders, AclClass.FOLDER, idToRemove);
    }

    /**
     * For {@link Folder} we don not return metadata for readOnly folders
     */
    @Override
    public void clearForReadOnlyView() {
        metadata = new HashMap<>();
    }

    private void addChildren(Map<AclClass, List<Long>> result,
                             List<? extends BaseEntity> children, AclClass childClass) {
        if (!CollectionUtils.isEmpty(children)) {
            result.put(childClass, children.stream().map(BaseEntity::getId).collect(Collectors.toList()));
        }
    }
}
