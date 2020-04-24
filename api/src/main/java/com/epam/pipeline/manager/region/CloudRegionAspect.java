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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.region.*;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;

@Slf4j
@Service
@Aspect
public class CloudRegionAspect {

    public static final String CP_REGION_CREDS_SECRET = "cp-region-creds-secret";
    public static final String STORAGE_ACCOUNT = "storage_account";
    public static final String STORAGE_KEY = "storage_key";
    private final KubernetesManager kubernetesManager;
    private final ObjectMapper mapper = new JsonMapper();

    @Autowired
    public CloudRegionAspect(KubernetesManager kubernetesManager) {
        this.kubernetesManager = kubernetesManager;
    }

    @After("execution(* com.epam.pipeline.dao.region.CloudRegionDao.create(..)) && args(region, credentials)")
    public void updateCloudRegionCreds(JoinPoint joinPoint, AbstractCloudRegion region,
                                       AbstractCloudRegionCredentials credentials) throws JsonProcessingException {
        if (!region.getProvider().equals(CloudProvider.AZURE)) {
            return;
        }
        if (!kubernetesManager.isSecretExist(CP_REGION_CREDS_SECRET)) {
            log.warn("Secret: " + CP_REGION_CREDS_SECRET + " doesn't exist!");
        }
        AzureRegion azureRegion = (AzureRegion) region;
        AzureRegionCredentials azureRegionCredentials = (AzureRegionCredentials) credentials;
        log.debug("Update Kube secret with new cred value for region with id: {}", region.getId());
        final HashMap<String, String> creds = new HashMap<>();
        creds.put(STORAGE_ACCOUNT, azureRegion.getStorageAccount());
        creds.put(STORAGE_KEY, azureRegionCredentials.getStorageAccountKey());
        kubernetesManager.updateSecret(CP_REGION_CREDS_SECRET,
                Collections.singletonMap(
                        azureRegion.getId().toString(),
                        Base64.encodeBase64String(mapper.writeValueAsString(creds).getBytes())),
                Collections.emptyMap()
        );
    }

    @After("execution(* com.epam.pipeline.dao.region.CloudRegionDao.delete(..)) && args(region)")
    public void deleteCloudRegionCreds(JoinPoint joinPoint, Long region) throws JsonProcessingException {
        log.debug("Delete cred value of a region from Kube secret. Region id: {}", region);
        if (kubernetesManager.isSecretExist(CP_REGION_CREDS_SECRET)) {
            kubernetesManager.updateSecret(CP_REGION_CREDS_SECRET,
                    Collections.emptyMap(),
                    Collections.singletonMap(
                            region.toString(),
                            Base64.encodeBase64String(mapper.writeValueAsString(Collections.emptyMap()).getBytes()))
            );
        }
    }
}
