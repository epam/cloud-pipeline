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
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    private static final int INSERT_BATCH_SIZE = 10000;

    @Transactional(propagation = Propagation.MANDATORY)
    @SuppressWarnings("unchecked")
    public void insertInstanceOffers(List<InstanceOffer> offerList) {
        for (int i = 0; i < offerList.size(); i += INSERT_BATCH_SIZE) {
            final List<InstanceOffer> batchList = offerList.subList(i,
                    i + INSERT_BATCH_SIZE > offerList.size() ? offerList.size() : i + INSERT_BATCH_SIZE);

            Map<String, Object>[] batchValues = new Map[batchList.size()];
            for (int j = 0; j < batchList.size(); j++) {
                InstanceOffer offer = batchList.get(j);
                batchValues[j] = InstanceOfferParameters.getParameters(offer).getValues();
            }
            getNamedParameterJdbcTemplate().batchUpdate(createInstanceOfferQuery, batchValues);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeInstanceOffers() {
        getJdbcTemplate().update(removeInstanceOffersQuery);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeInstanceOffersForRegion(Long regionId) {
        getJdbcTemplate().update(removeInstanceOffersForRegionQuery, regionId);
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
        PRICE_LIST_PUBLISH_DATE,
        VCPU,
        MEMORY,
        MEMORY_UNIT,
        INSTANCE_FAMILY,
        GPU,
        REGION;

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
            params.addValue(PRICE_LIST_PUBLISH_DATE.name(), instanceOffer.getPriceListPublishDate());
            params.addValue(VCPU.name(), instanceOffer.getVCPU());
            params.addValue(MEMORY.name(), instanceOffer.getMemory());
            params.addValue(MEMORY_UNIT.name(), instanceOffer.getMemoryUnit());
            params.addValue(INSTANCE_FAMILY.name(), instanceOffer.getInstanceFamily());
            params.addValue(GPU.name(), instanceOffer.getGpu());
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
                instanceOffer.setRegionId(rs.getLong(REGION.name()));
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
        REGION;

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
                instanceType.setRegionId(rs.getLong(REGION.name()));
                return instanceType;
            };
        }
    }

}
