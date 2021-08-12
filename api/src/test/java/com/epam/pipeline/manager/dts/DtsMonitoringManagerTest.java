package com.epam.pipeline.manager.dts;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DtsMonitoringManagerTest {

    private static final Long ID = 1L;
    private static final Duration OFFLINE_TIMEOUT = Duration.ofMinutes(5);
    private static final LocalDateTime RECENT_HEARTBEAT = DateUtils.nowUTC().minus(Duration.ofMinutes(1));
    private static final LocalDateTime OLD_HEARTBEAT = DateUtils.nowUTC().minus(Duration.ofHours(1));
    private static final LocalDateTime NO_HEARTBEAT = null;
    private final DtsRegistryManager dtsRegistryManager = mock(DtsRegistryManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final DtsMonitoringManager manager = new DtsMonitoringManager(dtsRegistryManager, preferenceManager,
            messageHelper);

    @Before
    public void setUp() {
        doReturn((int) OFFLINE_TIMEOUT.getSeconds())
                .when(preferenceManager)
                .getIntPreference(SystemPreferences.DTS_OFFLINE_TIMEOUT_SECONDS.getKey());
    }

    @Test
    public void monitorShouldNotFailIfThereAreNoDtsRegistered() {
        doReturn(Collections.emptyList()).when(dtsRegistryManager).loadAll();

        manager.monitor();
    }

    @Test
    public void monitorShouldNotUpdateStatusOfDtsWithRecentHeartbeat() {
        doReturn(Collections.singletonList(dts(RECENT_HEARTBEAT))).when(dtsRegistryManager).loadAll();

        manager.monitor();

        verify(dtsRegistryManager, times(0)).updateStatus(eq(ID), any());
    }

    @Test
    public void monitorShouldSetOfflineStatusForDtsWithOldHeartbeat() {
        doReturn(Collections.singletonList(dts(OLD_HEARTBEAT))).when(dtsRegistryManager).loadAll();

        manager.monitor();

        verify(dtsRegistryManager).updateStatus(ID, DtsStatus.OFFLINE);
    }

    @Test
    public void monitorShouldSetOfflineStatusForDtsWithNoHeartbeat() {
        doReturn(Collections.singletonList(dts(NO_HEARTBEAT))).when(dtsRegistryManager).loadAll();

        manager.monitor();

        verify(dtsRegistryManager).updateStatus(ID, DtsStatus.OFFLINE);
    }

    private DtsRegistry dts(final LocalDateTime heartbeat) {
        final DtsRegistry dts = new DtsRegistry();
        dts.setId(ID);
        dts.setHeartbeat(heartbeat);
        return dts;
    }
}
