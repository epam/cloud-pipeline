/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.kube;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.networking.IPBlock;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule;
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class KubernetesNetworkingService {
    private static final String CIDR_FORMAT = "%s/32";

    private final PreferenceManager preferenceManager;
    private final KubernetesAPIClient kubernetesClient;
    private final String kubeNamespace;

    public KubernetesNetworkingService(final PreferenceManager preferenceManage,
                                       final KubernetesAPIClient kubernetesClient,
                                       @Value("${kube.namespace:default}") final String kubeNamespace) {
        this.preferenceManager = preferenceManage;
        this.kubernetesClient = kubernetesClient;
        this.kubeNamespace = kubeNamespace;
    }

    public void updateEgressIpBlocks(final List<String> ips) {
        if (isNFSNetworkAccessRestricted()) {
            return;
        }

        final String networkPolicyName = preferenceManager.getPreference(SystemPreferences.KUBE_NETWORK_POLICY_NAME);
        final NetworkPolicy networkPolicy = kubernetesClient.getNetworkPolicy(kubeNamespace, networkPolicyName);
        Assert.notNull(networkPolicy, String.format("Network policy '%s' was not found", networkPolicyName));

        final List<NetworkPolicyEgressRule> egressRules = ListUtils.emptyIfNull(ips).stream()
                .map(this::buildRule)
                .collect(Collectors.toList());
        networkPolicy.getSpec().getEgress().addAll(egressRules);

        kubernetesClient.updateNetworkPolicy(kubeNamespace, networkPolicyName, networkPolicy);
    }

    public void deleteEgressIpBlocks(final List<String> ips) {
        if (isNFSNetworkAccessRestricted()) {
            return;
        }

        final String networkPolicyName = preferenceManager.getPreference(SystemPreferences.KUBE_NETWORK_POLICY_NAME);
        final NetworkPolicy networkPolicy = kubernetesClient.getNetworkPolicy(kubeNamespace, networkPolicyName);
        Assert.notNull(networkPolicy, String.format("Network policy '%s' was not found", networkPolicyName));

        final List<String> cidrs = ListUtils.emptyIfNull(ips).stream()
                .map(ip -> String.format(CIDR_FORMAT, ip))
                .collect(Collectors.toList());
        final List<NetworkPolicyEgressRule> filteredRules = networkPolicy.getSpec().getEgress().stream()
                .filter(rule -> !ruleContainsCidr(rule, cidrs))
                .collect(Collectors.toList());
        networkPolicy.getSpec().setEgress(filteredRules);

        kubernetesClient.updateNetworkPolicy(kubeNamespace, networkPolicyName, networkPolicy);
    }

    private boolean isNFSNetworkAccessRestricted() {
        return Optional.of(SystemPreferences.DATA_STORAGE_NFS_NETWORK_ACCESS_RESTRICTED)
                .map(preferenceManager::getPreference)
                .orElse(false);
    }

    private NetworkPolicyEgressRule buildRule(final String ip) {
        final IPBlock ipBlock = new IPBlock();
        ipBlock.setCidr(String.format(CIDR_FORMAT, ip));

        final NetworkPolicyPeer networkPolicyPeer = new NetworkPolicyPeer();
        networkPolicyPeer.setIpBlock(ipBlock);

        final NetworkPolicyEgressRule networkPolicyEgressRule = new NetworkPolicyEgressRule();
        networkPolicyEgressRule.setTo(Collections.singletonList(networkPolicyPeer));
        return networkPolicyEgressRule;
    }

    private boolean ruleContainsCidr(final NetworkPolicyEgressRule egressRule, final List<String> cidrs) {
        return ListUtils.emptyIfNull(egressRule.getTo()).stream()
                .map(NetworkPolicyPeer::getIpBlock)
                .filter(Objects::nonNull)
                .map(IPBlock::getCidr)
                .filter(StringUtils::isNotBlank)
                .anyMatch(cidrs::contains);
    }
}
