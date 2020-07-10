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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.utils.Base64Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@Aspect
public class CloudRegionAspect {

    public static final String CP_REGION_CREDS_SECRET = "cp-region-creds-secret";
    private final KubernetesManager kubernetesManager;
    private final AzureRegionHelper azureRegionHelper;

    @Autowired
    public CloudRegionAspect(final KubernetesManager kubernetesManager, final AzureRegionHelper azureRegionHelper) {
        this.kubernetesManager = kubernetesManager;
        this.azureRegionHelper = azureRegionHelper;
    }

    @After("execution(* com.epam.pipeline.dao.region.CloudRegionDao.create(..)) && args(region, credentials)")
    public void updateCloudRegionCreds(final JoinPoint joinPoint, final AbstractCloudRegion region,
                                       final AbstractCloudRegionCredentials credentials) {
        if (!region.getProvider().equals(CloudProvider.AZURE)) {
            return;
        }
        if (!kubernetesManager.doesSecretExist(CP_REGION_CREDS_SECRET)) {
            log.warn("Secret: " + CP_REGION_CREDS_SECRET + " doesn't exist!");
            return;
        }
        final AzureRegion azureRegion = (AzureRegion) region;
        final AzureRegionCredentials azureRegionCredentials = (AzureRegionCredentials) credentials;
        log.debug("Update Kube secret with new cred value for region with id: {}", region.getId());
        kubernetesManager.updateSecret(CP_REGION_CREDS_SECRET,
                Collections.singletonMap(
                        azureRegion.getId().toString(),
                        azureRegionHelper.serializeCredentials(azureRegion, azureRegionCredentials)),
                Collections.emptyMap()
        );
    }

    @After("execution(* com.epam.pipeline.dao.region.CloudRegionDao.delete(..)) && args(region)")
    public void deleteCloudRegionCreds(final JoinPoint joinPoint, final Long region) throws JsonProcessingException {
        log.debug("Delete cred value of a region from Kube secret. Region id: {}", region);
        if (kubernetesManager.doesSecretExist(CP_REGION_CREDS_SECRET)) {
            kubernetesManager.updateSecret(
                    CP_REGION_CREDS_SECRET,
                    Collections.emptyMap(),
                    Collections.singletonMap(region.toString(), Base64Utils.EMPTY_ENCODED_MAP)
            );
        }
    }
}
