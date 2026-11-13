/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.credits;

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.credits.PlatformUsageCreditsUserBalanceEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static com.epam.pipeline.test.creator.credits.PlatformUsageCreditsUserBalanceCreatorUtils.balanceEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class PlatformUsageCreditsUserBalanceMapperTest {

    private final PlatformUsageCreditsUserBalanceMapper mapper =
            Mappers.getMapper(PlatformUsageCreditsUserBalanceMapper.class);

    @Test
    public void shouldMapAllFieldsFromEntityToDto() {
        final PlatformUsageCreditsUserBalanceEntity entity = balanceEntity();

        final PlatformUsageCreditsUserBalance dto = mapper.toDto(entity);

        assertThat(dto.getUserId(), is(entity.getUserId()));
        assertThat(dto.getCurrentValue(), is(entity.getCurrentValue()));
        assertThat(dto.getModifiedDate(), is(entity.getModifiedDate()));
    }

    @Test
    public void shouldPreserveModifiedDate() {
        final PlatformUsageCreditsUserBalanceEntity entity = balanceEntity();

        final PlatformUsageCreditsUserBalance dto = mapper.toDto(entity);

        assertThat(dto.getModifiedDate(), notNullValue());
        assertThat(dto.getModifiedDate(), is(entity.getModifiedDate()));
    }
}
