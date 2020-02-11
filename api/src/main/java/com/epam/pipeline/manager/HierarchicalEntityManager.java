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

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.AbstractHierarchicalEntity;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.security.acl.AclSid;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class HierarchicalEntityManager {

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private DockerRegistryManager registryManager;

    public List<AbstractSecuredEntity> loadAvailable(final AclSid aclSid) {
        final List<AbstractHierarchicalEntity> allHierarchy =
                Stream.of(folderManager.loadTree(), registryManager.loadAllRegistriesContent())
                        .peek(h -> permissionManager.filterTree(aclSid, h, AclPermission.READ))
                        .collect(Collectors.toList());

        return flattenHierarchy(allHierarchy);
    }

    private List<AbstractSecuredEntity> flattenHierarchy(
            final List<? extends AbstractHierarchicalEntity> allHierarchy) {
        final List<AbstractSecuredEntity> collector = new ArrayList<>();
        flattenHierarchy(allHierarchy, collector);
        return collector;
    }

    private void flattenHierarchy(final List<? extends AbstractHierarchicalEntity> allHierarchy,
                                  final List<AbstractSecuredEntity> collector) {
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
                AbstractHierarchicalEntity copyView = child.copyView();
                collector.add(copyView);
            }
            collector.addAll(entity.getLeaves());
            flattenHierarchy(children, collector);
        }
    }
}
