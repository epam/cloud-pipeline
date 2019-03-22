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

package com.epam.pipeline.manager.cloud.aws;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SpotPrice;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StateReason;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterParameters;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.cloud.aws.AwsEc2Exception;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EC2Helper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EC2Helper.class);
    private static final int SPOT_REQUEST_INTERVAL = 3;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String NAME_TAG = "tag:Name";
    private static final String INSTANCE_STATE_NAME = "instance-state-name";

    private final PreferenceManager preferenceManager;

    public AmazonEC2 getEC2Client(String awsRegion) {
        AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard();
        builder.setRegion(awsRegion);
        return builder.build();
    }

    public double getSpotPrice(final String instanceType, final AwsRegion region) {
        AmazonEC2 client = getEC2Client(region.getRegionCode());
        Collection<String> availabilityZones = getAvailabilityZones(client, region.getRegionCode());
        if (CollectionUtils.isEmpty(availabilityZones)) {
            LOGGER.debug("Failed to find availability zones;");
            return 0;
        }
        DescribeSpotPriceHistoryRequest request = new DescribeSpotPriceHistoryRequest()
                .withInstanceTypes(instanceType)
                .withProductDescriptions("Linux/UNIX")
                .withStartTime(getSpotRequestStartTime())
                .withEndTime(com.epam.pipeline.entity.utils.DateUtils.now())
                .withFilters(new Filter().withName("availability-zone")
                        .withValues(availabilityZones));

        DescribeSpotPriceHistoryResult priceHistoryResult =
                client.describeSpotPriceHistory(request);

        return priceHistoryResult.getSpotPriceHistory()
                .stream().collect(Collectors.groupingBy(SpotPrice::getAvailabilityZone))
                .entrySet().stream()
                .map(entry -> getMeanValue(entry.getValue()))
                .min(Double::compareTo)
                .orElse(0.0);
    }

    public void stopInstance(String instanceId, String awsRegion) {
        AmazonEC2 client = getEC2Client(awsRegion);
        StopInstancesRequest stopInstancesRequest = new StopInstancesRequest().withInstanceIds(instanceId);
        client.stopInstances(stopInstancesRequest);
        Waiter<DescribeInstancesRequest> waiter = client.waiters().instanceStopped();
        waiter.run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceId)));
    }

    public void startInstance(String instanceId, String awsRegion) {
        AmazonEC2 client = getEC2Client(awsRegion);
        StartInstancesRequest startInstancesRequest = new StartInstancesRequest().withInstanceIds(instanceId);
        client.startInstances(startInstancesRequest);
        Waiter<DescribeInstancesRequest> waiter = client.waiters().instanceRunning();
        waiter.run(new WaiterParameters<>(new DescribeInstancesRequest().withInstanceIds(instanceId)));
    }

    public Optional<StateReason> getInstanceStateReason(String instanceId, String awsRegion) {
        try {
            return ListUtils.emptyIfNull(
                    getEC2Client(awsRegion)
                            .describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId))
                            .getReservations())
                    .stream()
                    .findFirst()
                    .flatMap(reservation -> ListUtils.emptyIfNull(reservation.getInstances())
                            .stream()
                            .findFirst())
                    .filter(ec2 -> {
                        LOGGER.debug("Checking instance state: {} {}", ec2.getState(), ec2.getStateReason());
                        final String stateName = ec2.getState().getName();
                        return !InstanceStateName.Pending.toString().equals(stateName) &&
                                !InstanceStateName.Running.toString().equals(stateName);
                    })
                    .map(Instance::getStateReason);
        } catch (AmazonEC2Exception e) {
            LOGGER.debug("Error during getting instances {} state {}", instanceId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retrieves instance launch time.
     *
     * @param runId Instance run id.
     * @param awsRegion Instance aws region.
     * @return Instance launch time in UTC.
     */
    public LocalDateTime getInstanceLaunchTime(final String runId, final String awsRegion) {
        final Instance instance = getInstance(runId, awsRegion, new Filter().withName(NAME_TAG).withValues(runId));
        final Date launchTime = instance.getLaunchTime();
        return LocalDateTime.ofInstant(launchTime.toInstant(), UTC);
    }

    /**
     * Retrieves pending or running instance.
     *
     * @param runId Instance run id.
     * @param awsRegion Instance aws region.
     * @return Required instance.
     */
    public Instance getActiveInstance(final String runId, final String awsRegion) {
        return getInstance(runId, awsRegion, new Filter().withName(NAME_TAG).withValues(runId),
                new Filter().withName(INSTANCE_STATE_NAME).withValues("pending", "running"));
    }

    private Instance getInstance(final String runId, final String awsRegion, final Filter... filters) {
        final AmazonEC2 client = getEC2Client(awsRegion);
        final List<Reservation> reservations = client.describeInstances(new DescribeInstancesRequest()
                .withFilters(filters))
                .getReservations();
        if (CollectionUtils.isEmpty(reservations)) {
            throw new AwsEc2Exception(String.format("No reservations found with name tag '%s' in %s region",
                    runId, awsRegion));
        }
        final Reservation reservation = reservations.get(0);
        final List<Instance> instances = reservation.getInstances();
        if (CollectionUtils.isEmpty(instances)) {
            throw new AwsEc2Exception(String.format("No instances found in reservation with id %s",
                    reservation.getReservationId()));
        }
        return instances.get(0);
    }


    private double getMeanValue(List<SpotPrice> value) {
        if (CollectionUtils.isEmpty(value)) {
            return 0f;
        }
        int num = value.size();
        double sum = value.stream()
                .mapToDouble(v -> Double.parseDouble(v.getSpotPrice())).sum();
        return sum / num;
    }

    private Date getSpotRequestStartTime() {
        return DateUtils.addHours(
                com.epam.pipeline.entity.utils.DateUtils.now(), -SPOT_REQUEST_INTERVAL);
    }

    private Collection<String> getAvailabilityZones(AmazonEC2 client, String awsRegion) {
        Collection<String> allowedNetworks = getAllowedNetworks(awsRegion);
        if (CollectionUtils.isNotEmpty(allowedNetworks)) {
            return allowedNetworks;
        }
        return client.describeAvailabilityZones().getAvailabilityZones().stream()
                .map(AvailabilityZone::getZoneName).collect(Collectors.toList());
    }

    private Collection<String> getAllowedNetworks(String awsRegion) {
        CloudRegionsConfiguration configuration = preferenceManager.getObjectPreferenceAs(
            SystemPreferences.CLUSTER_NETWORKS_CONFIG, new TypeReference<CloudRegionsConfiguration>() {});
        if (configuration == null || CollectionUtils.isEmpty(configuration.getRegions())) {
            return Collections.emptySet();
        }
        return configuration.getRegions().stream()
                .filter(region -> awsRegion.equals(region.getName()))
                .filter(region -> MapUtils.isNotEmpty(region.getAllowedNetworks()))
                .findFirst()
                .map(region -> region.getAllowedNetworks().keySet())
                .orElse(Collections.emptySet());
    }
}
