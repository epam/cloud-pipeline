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

package com.epam.pipeline.manager;

import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HierarchicalEntityManager {

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private DockerRegistryManager registryManager;

    public Map<AclClass, List<AbstractSecuredEntity>> loadAvailable(final AclSid aclSid, final AclClass aclClass) {
        log.debug("Retrieving whole hierarchy of all available objects for SID: " + aclSid.getName());
        final List<AbstractHierarchicalEntity> allHierarchy = loadEntities(aclClass)
                .stream()
                .peek(h -> permissionManager.filterTree(aclSid, h, AclPermission.READ))
                .collect(Collectors.toList());

        log.debug("Flatten trees to map by AclClass");
        return flattenHierarchy(allHierarchy, aclClass)
                .stream()
                .collect(Collectors.groupingBy(AbstractSecuredEntity::getAclClass));
    }

    private List<AbstractSecuredEntity> flattenHierarchy(
            final List<? extends AbstractHierarchicalEntity> allHierarchy, final AclClass aclClass) {
        final List<AbstractSecuredEntity> collector = new ArrayList<>();
        flattenHierarchy(allHierarchy, collector, aclClass);
        log.debug("Size of map with available objects: " + collector.size());
        return collector;
    }

    private void flattenHierarchy(final List<? extends AbstractHierarchicalEntity> allHierarchy,
                                  final List<AbstractSecuredEntity> collector, final AclClass aclClass) {
        if (CollectionUtils.isEmpty(allHierarchy)) {
            return;
        }

        for (final AbstractHierarchicalEntity entity : allHierarchy) {
            List<? extends AbstractHierarchicalEntity> children = entity.getChildren();

            for (AbstractHierarchicalEntity child : children) {
                // Filter object with mask 0, because it can be 0 only in case when this object has some children
                // to show, but forbidden by itself, and was cleared for read only view by filterTree() method
                if (child.getMask() == 0) {
                    continue;
                }
                if (Objects.nonNull(aclClass) && !child.getAclClass().equals(aclClass)) {
                    continue;
                }
                AbstractHierarchicalEntity copyView = child.copyView();
                collector.add(copyView);
            }
            collector.addAll(filterLeaves(entity.getLeaves(), aclClass));
            flattenHierarchy(children, collector, aclClass);
        }
    }

    private List<? extends AbstractSecuredEntity> filterLeaves(final List<? extends AbstractSecuredEntity> leaves,
                                                               final AclClass aclClass) {
        if (Objects.isNull(aclClass)) {
            return leaves;
        }
        return ListUtils.emptyIfNull(leaves).stream()
                .filter(entity -> Objects.equals(entity.getAclClass(), aclClass))
                .collect(Collectors.toList());
    }

    private List<AbstractHierarchicalEntity> loadEntities(final AclClass aclClass) {
        if (Objects.isNull(aclClass)) {
            return Arrays.asList(folderManager.loadTree(), registryManager.loadAllRegistriesContent());
        }
        if (isFolderContent(aclClass)) {
            return Collections.singletonList(folderManager.loadTree());
        }
        if (isRegistriesContent(aclClass)) {
            return Collections.singletonList(registryManager.loadAllRegistriesContent());
        }
        throw new UnsupportedOperationException(
                String.format("'%s' ACL class is not supported for loading permissions", aclClass));
    }

    private boolean isRegistriesContent(final AclClass aclClass) {
        return aclClass == AclClass.DOCKER_REGISTRY || aclClass == AclClass.TOOL || aclClass == AclClass.TOOL_GROUP;
    }

    private boolean isFolderContent(final AclClass aclClass) {
        return aclClass == AclClass.PIPELINE || aclClass == AclClass.FOLDER || aclClass == AclClass.DATA_STORAGE
                || aclClass == AclClass.CONFIGURATION || aclClass == AclClass.METADATA_ENTITY;
    }
}
