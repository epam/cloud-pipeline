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

package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.cluster.GpuDevice;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class InstanceOfferDao extends NamedParameterJdbcDaoSupport {

    private Pattern wherePattern = Pattern.compile("@WHERE@");
    private static final String AND = " AND ";

    private final String createInstanceOfferQuery;
    private final String removeInstanceOffersQuery;
    private final String loadInstanceOfferQuery;
    private final String loadFirstInstanceOffer;
    private final String loadInstanceTypesQuery;
    private final String removeInstanceOffersForRegionQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    @SuppressWarnings("unchecked")
    public void insertInstanceOffers(final List<InstanceOffer> offers, final int batchSize) {
        final AtomicInteger counter = new AtomicInteger();
        StreamUtils.chunked(offers.stream(), batchSize).forEach(batch -> {
            log.debug("Inserting {}/{} instance offers...", counter.addAndGet(batch.size()), offers.size());
            getNamedParameterJdbcTemplate().batchUpdate(createInstanceOfferQuery,
                    InstanceOfferParameters.getParameters(batch));
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeInstanceOffers() {
        getJdbcTemplate().update(removeInstanceOffersQuery);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeInstanceOffersForRegion(Long regionId) {
        getJdbcTemplate().update(removeInstanceOffersForRegionQuery, regionId);
    }

    @Transactional
    public void replaceInstanceOffersForRegion(final Long id, final List<InstanceOffer> offers, int batchSize) {
        removeInstanceOffersForRegion(id);
        insertInstanceOffers(offers, batchSize);
    }

    public List<InstanceOffer> loadInstanceOffers(InstanceOfferRequestVO instanceOfferRequestVO) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(loadInstanceOfferQuery)
                .replaceFirst(makeFilterCondition(instanceOfferRequestVO, params));
        return getNamedParameterJdbcTemplate().query(query, params, InstanceOfferParameters.getRowMapper());
    }

    public List<InstanceType> loadInstanceTypes(InstanceOfferRequestVO instanceOfferRequestVO) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(loadInstanceTypesQuery)
                .replaceFirst(makeFilterCondition(instanceOfferRequestVO, params));
        return getNamedParameterJdbcTemplate().query(query, params, InstanceTypeParameters.getRowMapper());
    }

    public List<InstanceType> loadInstanceTypes() {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(loadInstanceTypesQuery)
            .replaceFirst("");
        return getNamedParameterJdbcTemplate().query(query, params, InstanceTypeParameters.getRowMapper());
    }

    public Date getPriceListPublishDate() {
        List<InstanceOffer> offers = getNamedParameterJdbcTemplate()
                .query(loadFirstInstanceOffer, InstanceOfferParameters.getRowMapper());
        if (CollectionUtils.isNotEmpty(offers)) {
            return offers.get(0).getPriceListPublishDate();
        }
        return null;
    }

    private String makeFilterCondition(InstanceOfferRequestVO requestVO, MapSqlParameterSource params) {
        StringBuilder whereBuilder = new StringBuilder();

        if (!requestVO.isEmpty()) {
            int clausesCount = 0;
            whereBuilder.append(" WHERE ");

            if (requestVO.getTermType() != null) {
                whereBuilder.append(" i.term_type = :").append(InstanceOfferParameters.TERM_TYPE.name());
                params.addValue(InstanceOfferParameters.TERM_TYPE.name(), requestVO.getTermType());
                clausesCount++;
            }

            if (CollectionUtils.isNotEmpty(requestVO.getTermTypes())) {
                whereBuilder.append(" i.term_type IN :").append(InstanceOfferParameters.TERM_TYPE.name());
                params.addValue(InstanceOfferParameters.TERM_TYPE.name(),
                        String.join(",", requestVO.getTermTypes()));
                clausesCount++;
            }

            if (requestVO.getUnit() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.unit = :").append(InstanceOfferParameters.UNIT.name());
                params.addValue(InstanceOfferParameters.UNIT.name(), requestVO.getUnit());
                clausesCount++;
            }

            if (requestVO.getInstanceType() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.instance_type = :").append(InstanceOfferParameters.INSTANCE_TYPE.name());
                params.addValue(InstanceOfferParameters.INSTANCE_TYPE.name(), requestVO.getInstanceType());
                clausesCount++;
            }

            if (requestVO.getTenancy() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.tenancy = :").append(InstanceOfferParameters.TENANCY.name());
                params.addValue(InstanceOfferParameters.TENANCY.name(), requestVO.getTenancy());
                clausesCount++;
            }

            if (requestVO.getOperatingSystem() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.operating_system = :").append(InstanceOfferParameters.OPERATING_SYSTEM.name());
                params.addValue(InstanceOfferParameters.OPERATING_SYSTEM.name(), requestVO.getOperatingSystem());
                clausesCount++;
            }

            if (requestVO.getProductFamily() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.product_family = :").append(InstanceOfferParameters.PRODUCT_FAMILY.name());
                params.addValue(InstanceOfferParameters.PRODUCT_FAMILY.name(), requestVO.getProductFamily());
                clausesCount++;
            }

            if (requestVO.getVolumeType() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.volume_type = :").append(InstanceOfferParameters.VOLUME_TYPE.name());
                params.addValue(InstanceOfferParameters.VOLUME_TYPE.name(), requestVO.getVolumeType());
            }
            if (requestVO.getVolumeApiName() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.volume_api_name = :").append(InstanceOfferParameters.VOLUME_API_NAME.name());
                params.addValue(InstanceOfferParameters.VOLUME_API_NAME.name(), requestVO.getVolumeApiName());
            }
            if (requestVO.getRegionId() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" i.region = :").append(InstanceOfferParameters.REGION.name());
                params.addValue(InstanceOfferParameters.REGION.name(), requestVO.getRegionId());
            }
        }

        return whereBuilder.toString();
    }

    enum InstanceOfferParameters {
        SKU,
        TERM_TYPE,
        UNIT,
        PRICE_PER_UNIT,
        CURRENCY,
        INSTANCE_TYPE,
        TENANCY,
        OPERATING_SYSTEM,
        PRODUCT_FAMILY,
        VOLUME_TYPE,
        VOLUME_API_NAME,
        PRICE_LIST_PUBLISH_DATE,
        VCPU,
        MEMORY,
        MEMORY_UNIT,
        INSTANCE_FAMILY,
        GPU,
        GPU_NAME,
        GPU_MANUFACTURER,
        GPU_CORES,
        REGION,
        CLOUD_PROVIDER;

        private static Map<String, Object>[] getParameters(final List<InstanceOffer> offers) {
            return offers.stream()
                    .map(offer -> InstanceOfferParameters.getParameters(offer).getValues())
                    .<Map<String, Object>>toArray(Map[]::new);
        }

        static MapSqlParameterSource getParameters(InstanceOffer instanceOffer) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(SKU.name(), instanceOffer.getSku());
            params.addValue(TERM_TYPE.name(), instanceOffer.getTermType());
            params.addValue(UNIT.name(), instanceOffer.getUnit());
            params.addValue(PRICE_PER_UNIT.name(), instanceOffer.getPricePerUnit());
            params.addValue(CURRENCY.name(), instanceOffer.getCurrency());
            params.addValue(INSTANCE_TYPE.name(), instanceOffer.getInstanceType());
            params.addValue(TENANCY.name(), instanceOffer.getTenancy());
            params.addValue(OPERATING_SYSTEM.name(), instanceOffer.getOperatingSystem());
            params.addValue(PRODUCT_FAMILY.name(), instanceOffer.getProductFamily());
            params.addValue(VOLUME_TYPE.name(), instanceOffer.getVolumeType());
            params.addValue(VOLUME_API_NAME.name(), instanceOffer.getVolumeApiName());
            params.addValue(PRICE_LIST_PUBLISH_DATE.name(), instanceOffer.getPriceListPublishDate());
            params.addValue(VCPU.name(), instanceOffer.getVCPU());
            params.addValue(MEMORY.name(), instanceOffer.getMemory());
            params.addValue(MEMORY_UNIT.name(), instanceOffer.getMemoryUnit());
            params.addValue(INSTANCE_FAMILY.name(), instanceOffer.getInstanceFamily());
            params.addValue(GPU.name(), instanceOffer.getGpu());
            final Optional<GpuDevice> gpu = Optional.ofNullable(instanceOffer.getGpuDevice());
            params.addValue(GPU_NAME.name(), gpu.map(GpuDevice::getName).orElse(null));
            params.addValue(GPU_MANUFACTURER.name(), gpu.map(GpuDevice::getManufacturer).orElse(null));
            params.addValue(GPU_CORES.name(), gpu.map(GpuDevice::getCores).orElse(null));
            params.addValue(REGION.name(), instanceOffer.getRegionId());
            return params;
        }

        static RowMapper<InstanceOffer> getRowMapper() {
            return (rs, rowNum) -> {
                InstanceOffer instanceOffer = new InstanceOffer();
                instanceOffer.setSku(rs.getString(SKU.name()));
                instanceOffer.setTermType(rs.getString(TERM_TYPE.name()));
                instanceOffer.setUnit(rs.getString(UNIT.name()));
                instanceOffer.setPricePerUnit(rs.getDouble(PRICE_PER_UNIT.name()));
                instanceOffer.setCurrency(rs.getString(CURRENCY.name()));
                instanceOffer.setInstanceType(rs.getString(INSTANCE_TYPE.name()));
                instanceOffer.setTenancy(rs.getString(TENANCY.name()));
                instanceOffer.setProductFamily(rs.getString(PRODUCT_FAMILY.name()));
                instanceOffer.setVolumeType(rs.getString(VOLUME_TYPE.name()));
                instanceOffer.setOperatingSystem(rs.getString(OPERATING_SYSTEM.name()));
                instanceOffer
                        .setPriceListPublishDate(new Date(rs.getTimestamp(PRICE_LIST_PUBLISH_DATE.name()).getTime()));
                instanceOffer.setVCPU(rs.getInt(VCPU.name()));
                instanceOffer.setMemory(rs.getDouble(MEMORY.name()));
                instanceOffer.setMemoryUnit(rs.getString(MEMORY_UNIT.name()));
                instanceOffer.setInstanceFamily(rs.getString(INSTANCE_FAMILY.name()));
                instanceOffer.setGpu(rs.getInt(GPU.name()));
                if (instanceOffer.getGpu() > 0) {
                    instanceOffer.setGpuDevice(GpuDevice.from(
                            rs.getString(GPU_NAME.name()),
                            rs.getString(GPU_MANUFACTURER.name()),
                            DaoHelper.parseInteger(rs, GPU_CORES.name())));
                }
                instanceOffer.setRegionId(rs.getLong(REGION.name()));
                String cloudProviderName = rs.getString(CLOUD_PROVIDER.name());
                if (!rs.wasNull()) {
                    instanceOffer.setCloudProvider(CloudProvider.valueOf(cloudProviderName));
                }
                return instanceOffer;
            };
        }
    }

    enum InstanceTypeParameters {
        SKU,
        INSTANCE_TYPE,
        OPERATING_SYSTEM,
        VCPU,
        MEMORY,
        MEMORY_UNIT,
        INSTANCE_FAMILY,
        GPU,
        GPU_NAME,
        GPU_MANUFACTURER,
        GPU_CORES,
        REGION,
        TERM_TYPE;

        static RowMapper<InstanceType> getRowMapper() {
            return (rs, rowNum) -> {
                InstanceType instanceType = new InstanceType();
                instanceType.setSku(rs.getString(SKU.name()));
                instanceType.setName(rs.getString(INSTANCE_TYPE.name()));
                instanceType.setOperatingSystem(rs.getString(OPERATING_SYSTEM.name()));
                instanceType.setVCPU(rs.getInt(VCPU.name()));
                instanceType.setMemory(rs.getFloat(MEMORY.name()));
                instanceType.setMemoryUnit(rs.getString(MEMORY_UNIT.name()));
                instanceType.setInstanceFamily(rs.getString(INSTANCE_FAMILY.name()));
                instanceType.setGpu(rs.getInt(GPU.name()));
                if (instanceType.getGpu() > 0) {
                    instanceType.setGpuDevice(GpuDevice.from(
                            rs.getString(GPU_NAME.name()),
                            rs.getString(GPU_MANUFACTURER.name()),
                            DaoHelper.parseInteger(rs, GPU_CORES.name())));
                }
                instanceType.setRegionId(rs.getLong(REGION.name()));
                instanceType.setTermType(rs.getString(TERM_TYPE.name()));
                return instanceType;
            };
        }
    }

}
