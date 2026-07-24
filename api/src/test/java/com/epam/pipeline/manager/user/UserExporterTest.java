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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUserBalance;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.user.UserCreatorUtils.getPipelineUser;
import static org.assertj.core.api.Assertions.assertThat;

public class UserExporterTest {

    private static final int CREDITS_VALUE = 1500;
    private static final String USAGE_CREDITS_HEADER = "usageCredits";

    private final UserExporter exporter = new UserExporter();

    @Test
    public void shouldIncludeUsageCreditsColumnInHeaderWhenFlagIsSet() {
        final PipelineUserExportVO settings = new PipelineUserExportVO();
        settings.setIncludeHeader(true);
        settings.setIncludeCredits(true);

        final String csv = exporter.exportUsers(settings, Collections.emptyList(), Collections.emptyList());

        assertThat(csv).contains(USAGE_CREDITS_HEADER);
    }

    @Test
    public void shouldNotIncludeUsageCreditsColumnWhenFlagIsNotSet() {
        final PipelineUserExportVO settings = new PipelineUserExportVO();
        settings.setIncludeHeader(true);
        settings.setIncludeCredits(false);

        final String csv = exporter.exportUsers(settings, Collections.emptyList(), Collections.emptyList());

        assertThat(csv).doesNotContain(USAGE_CREDITS_HEADER);
    }

    @Test
    public void shouldWriteCreditsValueWhenBalanceIsPresent() {
        final PipelineUserExportVO settings = exportSettingsWithCredits();
        final PipelineUser user = getPipelineUser("USER1", ID);
        user.setUsageCredits(new PlatformUsageCreditsUserBalance(ID, CREDITS_VALUE, null));
        final List<PipelineUserWithStoragePath> users = Collections.singletonList(wrap(user));

        final String csv = exporter.exportUsers(settings, users, Collections.emptyList());

        assertThat(csv).contains(String.valueOf(CREDITS_VALUE));
    }

    @Test
    public void shouldWriteEmptyStringWhenBalanceIsNull() {
        final PipelineUserExportVO settings = exportSettingsWithCredits();
        final PipelineUser user = getPipelineUser("USER1", ID);
        // usageCredits intentionally left null
        final List<PipelineUserWithStoragePath> users = Collections.singletonList(wrap(user));

        final String csv = exporter.exportUsers(settings, users, Collections.emptyList());

        // header present but no numeric value on the data row
        assertThat(csv).contains(USAGE_CREDITS_HEADER);
        assertThat(csv).doesNotContain(String.valueOf(CREDITS_VALUE));
    }

    private static PipelineUserExportVO exportSettingsWithCredits() {
        final PipelineUserExportVO settings = new PipelineUserExportVO();
        settings.setIncludeHeader(true);
        settings.setIncludeCredits(true);
        return settings;
    }

    private static PipelineUserWithStoragePath wrap(final PipelineUser user) {
        return PipelineUserWithStoragePath.builder().pipelineUser(user).build();
    }
}
