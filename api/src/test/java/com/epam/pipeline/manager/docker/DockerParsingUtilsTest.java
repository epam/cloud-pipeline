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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.entity.docker.HistoryEntry;
import com.epam.pipeline.entity.docker.RawImageDescription;
import org.junit.Test;

import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class DockerParsingUtilsTest {
    private static final String LATEST_DATE = ",\"created\":\"2017-10-02T18:59:07.729529044Z\",";
    private static final String EARLIEST_DATE = ",\"created\":\"2017-10-02T18:57:48.784338364Z\",";
    private static final String SHORT_DATE = ",\"created\":\"2017-10-02T18:58:48.784338Z\",";
    private static final int EARLIEST_MINUTES = 57;
    private static final int LATEST_MINUTES = 59;

    @Test
    public void shouldCalculateLatestAndEarliestDateTimeProperly() {
        HistoryEntry entryWithEarliestDate = new HistoryEntry();
        entryWithEarliestDate.setV1Compatibility(EARLIEST_DATE);
        HistoryEntry entryWithLatestDate = new HistoryEntry();
        entryWithLatestDate.setV1Compatibility(LATEST_DATE);
        HistoryEntry entryWithShortDate = new HistoryEntry();
        entryWithShortDate.setV1Compatibility(SHORT_DATE);
        RawImageDescription description = new RawImageDescription();
        description.setHistory(Arrays.asList(entryWithEarliestDate, entryWithLatestDate, entryWithShortDate));

        assertThat(DockerParsingUtils.getEarliestDate(description).toInstant().atZone(ZoneOffset.UTC).getMinute())
                .isEqualTo(EARLIEST_MINUTES);
        assertThat(DockerParsingUtils.getLatestDate(description).toInstant().atZone(ZoneOffset.UTC).getMinute())
                .isEqualTo(LATEST_MINUTES);
    }
}
