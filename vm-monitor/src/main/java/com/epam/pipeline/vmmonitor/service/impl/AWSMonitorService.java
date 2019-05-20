/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.vmmonitor.service.impl;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.Tag;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.vmmonitor.model.vm.VMTag;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.VMMonitorService;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AWSMonitorService implements VMMonitorService<AwsRegion> {

    private static final String INSTANCE_STATE_FILTER = "instance-state-name";
    private static final String TAG_KEY_FILTER = "tag:";
    private static final String RUNNING_STATE = "running";

    private final VMTag instanceTag;

    public AWSMonitorService(@Value("${monitor.instance.tag}") final String instanceTag) {
        this.instanceTag = VMTag.fromProperty(instanceTag);
    }

    @Override
    public List<VirtualMachine> fetchRunningVms(final AwsRegion region) {
        final AmazonEC2 ec2 = getEc2Client(region);
        final DescribeInstancesRequest request = new DescribeInstancesRequest()
                .withFilters(getFilters());
        final List<VirtualMachine> vms = new ArrayList<>();
        return fetchInstances(ec2, request, vms);
    }

    private AmazonEC2 getEc2Client(final AwsRegion region) {
        final AmazonEC2ClientBuilder clientBuilder = AmazonEC2ClientBuilder.standard()
                .withRegion(region.getRegionCode());

        if (StringUtils.isNotBlank(region.getProfile())) {
            clientBuilder.withCredentials(new ProfileCredentialsProvider(region.getProfile()));
        }
        return clientBuilder
                .build();
    }

    private List<VirtualMachine> fetchInstances(final AmazonEC2 ec2,
                                                final DescribeInstancesRequest request,
                                                final List<VirtualMachine> vms) {
        final DescribeInstancesResult result = ec2.describeInstances(request);
        List<VirtualMachine> mergedVms = Stream.concat(vms.stream(),
                ListUtils.emptyIfNull(result.getReservations())
                        .stream()
                        .map(reservation -> ListUtils.emptyIfNull(reservation.getInstances()))
                        .flatMap(Collection::stream)
                        .map(this::toVM)).collect(Collectors.toList());
        if (StringUtils.isNotBlank(result.getNextToken())) {
            request.setNextToken(result.getNextToken());
            return fetchInstances(ec2, request, vms);
        }
        return mergedVms;
    }

    @Override
    public CloudProvider provider() {
        return CloudProvider.AWS;
    }

    private List<Filter> getFilters() {
        return Arrays.asList(
                new Filter().withName(TAG_KEY_FILTER + instanceTag.getKey()).withValues(instanceTag.getValue()),
                new Filter().withName(INSTANCE_STATE_FILTER).withValues(RUNNING_STATE));
    }

    private VirtualMachine toVM(final Instance instance) {
        return VirtualMachine.builder()
                .instanceId(instance.getInstanceId())
                .cloudProvider(provider())
                .instanceName(instance.getPrivateDnsName())
                .tags(mapTags(instance.getTags()))
                .privateIp(mapPrivateIp(instance.getNetworkInterfaces()))
                .build();
    }

    private String mapPrivateIp(final List<InstanceNetworkInterface> networkInterfaces) {
        return ListUtils.emptyIfNull(networkInterfaces)
                .stream()
                .findFirst()
                .map(InstanceNetworkInterface::getPrivateIpAddress)
                .orElse(null);
    }

    private Map<String, String> mapTags(final List<Tag> tags) {
        return ListUtils.emptyIfNull(tags)
                .stream()
                .collect(Collectors.toMap(Tag::getKey,
                    tag -> Optional.ofNullable(tag.getValue()).orElse(StringUtils.EMPTY)));
    }
}
