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

package com.epam.pipeline.manager.event;

import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.pipeline.FolderCrudManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FolderEventService implements EntityEventService {

    private final EventManager eventManager;
    private final FolderCrudManager folderManager;
    private final PipelineEventService pipelineEventService;
    private final DataStorageEventService dataStorageEventService;
    private final ConfigurationEventService configurationEventService;
    private final MetadataEntityEventService metadataEntityEventService;
    private final MetadataEntityManager metadataEntityManager;

    @Override
    public AclClass getSupportedClass() {
        return AclClass.FOLDER;
    }

    @Override
    public void updateEventsWithChildrenAndIssues(Long id) {
        String folderType = EventObjectType.FOLDER.name().toLowerCase();
        eventManager.addUpdateEvent(folderType, id);
        eventManager.addUpdateEventsForIssues(id, AclClass.FOLDER);

        Folder folder = folderManager.load(id);

        ListUtils.emptyIfNull(folder.getChildFolders())
                .forEach(childFolder -> updateEventsWithChildrenAndIssues(childFolder.getId()));
        ListUtils.emptyIfNull(folder.getPipelines())
                .forEach(pipeline -> pipelineEventService.updateEventsWithChildrenAndIssues(pipeline.getId()));
        ListUtils.emptyIfNull(folder.getStorages())
                .forEach(storage -> dataStorageEventService.updateEventsWithChildrenAndIssues(storage.getId()));
        ListUtils.emptyIfNull(folder.getConfigurations())
                .forEach(runConfiguration -> configurationEventService
                        .updateEventsWithChildrenAndIssues(runConfiguration.getId()));

        folder.getMetadata().keySet()
                .forEach(metadataClass -> ListUtils.emptyIfNull(metadataEntityManager
                        .loadMetadataEntityByClassNameAndFolderId(id, metadataClass))
                        .forEach(metadataEntity -> metadataEntityEventService
                                .updateEventsWithChildrenAndIssues(metadataEntity.getId())));
    }
}
