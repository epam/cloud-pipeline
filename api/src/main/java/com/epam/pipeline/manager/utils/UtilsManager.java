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

package com.epam.pipeline.manager.utils;

import com.epam.pipeline.entity.cluster.ServiceDescription;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class UtilsManager {

    private static final String SSH_URL_TEMPLATE = "%s://%s:%d/ssh/pipeline/%d";
    private static final String FSBROWSER_URL_TEMPLATE = "%s://%s:%d/fsbrowser/%d";

    @Autowired
    private KubernetesManager kubeManager;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private PipelineRunManager runManager;

    @Value("${kube.edge.label}")
    private String edgeLabel;

    public String buildSshUrl(Long runId) {
        return buildUrl(SSH_URL_TEMPLATE, runId);
    }

    public String getEdgeDomainNameOrIP() {
        return getServiceDescription(edgeLabel).getIp();
    }

    public String buildFSBrowserUrl(Long runId) {
        if (isFSBrowserEnabled() && runIsNotSensitive(runId)) {
            return buildUrl(FSBROWSER_URL_TEMPLATE, runId);
        } else {
            throw new IllegalArgumentException("Storage fsbrowser is not enabled.");
        }
    }

    private boolean runIsNotSensitive(final Long runId) {
        return !BooleanUtils.toBoolean(runManager.loadPipelineRun(runId).getSensitive());
    }

    private Boolean isFSBrowserEnabled() {
        return Optional.ofNullable(preferenceManager.getBooleanPreference(
                SystemPreferences.STORAGE_FSBROWSER_ENABLED.getKey()))
                .orElse(false);
    }

    private String buildUrl(String template, Long runId) {
        final ServiceDescription service = getServiceDescription(edgeLabel);
        return String.format(template, service.getScheme(), service.getIp(), service.getPort(), runId);
    }

    private ServiceDescription getServiceDescription(final String label) {
        final ServiceDescription service = kubeManager.getServiceByLabel(label);
        if (service == null) {
            throw new IllegalArgumentException("Edge server is not registered in the cluster.");
        }
        return service;
    }

    public List<DefaultSystemParameter> getSystemParameters() {
        List<DefaultSystemParameter> defaultSystemParameterList = preferenceManager.getPreference(
            SystemPreferences.LAUNCH_SYSTEM_PARAMETERS);
        return defaultSystemParameterList != null ? defaultSystemParameterList : Collections.emptyList();
    }
}
