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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AutoscalerServiceImpl implements AutoscalerService {

    private final PreferenceManager preferenceManager;
    private final CloudRegionManager cloudRegionManager;

    @Override
    public boolean requirementsMatch(RunInstance instanceOld, RunInstance instanceNew) {
        if (instanceOld == null || instanceNew == null) {
            return false;
        }
        return instanceOld.requirementsMatch(instanceNew);
    }

    @Override
    public RunInstance getDefaultInstance() {
        RunInstance instance = new RunInstance();
        instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        instance.setEffectiveNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        instance.setCloudRegionId(cloudRegionManager.loadDefaultRegion().getId());
        return instance;
    }

    @Override
    public RunInstance configurationToInstance(PipelineConfiguration configuration) {
        RunInstance instance = new RunInstance();
        if (configuration.getInstanceType() == null) {
            instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        } else {
            instance.setNodeType(configuration.getInstanceType());
        }
        if (configuration.getInstanceDisk() == null) {
            instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        } else {
            instance.setNodeDisk(Integer.parseInt(configuration.getInstanceDisk()));
        }
        instance.setEffectiveNodeDisk(instance.getNodeDisk());
        instance.setNodeImage(configuration.getInstanceImage());
        instance.setCloudRegionId(Optional.ofNullable(configuration.getCloudRegionId())
                .map(cloudRegionManager::load)
                .orElse(cloudRegionManager.loadDefaultRegion())
                .getId());
        return instance;
    }

    @Override
    public RunInstance fillInstance(RunInstance instance) {
        if (instance.getNodeType() == null) {
            instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        }
        if (instance.getNodeDisk() == null) {
            instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
            instance.setEffectiveNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        }
        if (instance.getCloudRegionId() == null) {
            instance.setCloudRegionId(cloudRegionManager.loadDefaultRegion().getId());
        }
        return instance;
    }
}
