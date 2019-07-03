package com.epam.pipeline.manager.cluster;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesManagerTest {

    private static final String NEW_LINE = "\n";
    private static final String LOG = "Simple first log line" + NEW_LINE + "Simple second log line";
    private static final String LOG2 = "Simple second log line" + NEW_LINE + "Simple third log line";
    private static final String LOG3 = "Simple third log line" + NEW_LINE + "Simple fourth log line";
    private static final String COLLAPSED = "< ... >";
    private static final String POD_ID = "pod_id";
    private static final int LIMIT_256 = 256;
    private static final int LIMIT_64 = 64;
    private static final int LIMIT_32 = 32;

    @Spy
    private KubernetesManager manager = new KubernetesManager();

    @Before
    public void setUp() {
        when(manager.getKubernetesClient()).thenReturn(new DefaultKubernetesClient());
    }

    @Test
    public void getPodLogsWhenItFitsToSize() {

        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG)
                .when(manager).getTailLog(eq(POD_ID), anyInt(), anyObject());

        String logs = manager.getPodLogs(POD_ID, LIMIT_256);
        Assert.assertEquals(LOG, logs);
    }

    @Test
    public void getPodLogsWhenItFitsToSizeAndTailOverlapsHead() {
        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG2)
                .when(manager).getTailLog(eq(POD_ID), anyInt(), anyObject());

        String logs = manager.getPodLogs(POD_ID, LIMIT_64);
        Assert.assertEquals(LOG + NEW_LINE + COLLAPSED + NEW_LINE + "Simple third log line", logs);
    }

    @Test
    public void getPodLogsWhenItDoesNotFitsToSize() {
        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG2)
                .when(manager).getTailLog(eq(POD_ID), anyInt(), anyObject());

        String logs = manager.getPodLogs(POD_ID, LIMIT_32);
        Assert.assertEquals(LOG + NEW_LINE + COLLAPSED, logs);
    }

    @Test
    public void getPodLogsWhenItDoesNotFitsToSize2() {
        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG3)
                .when(manager).getTailLog(eq(POD_ID), anyInt(), anyObject());

        String logs = manager.getPodLogs(POD_ID, LIMIT_64);
        Assert.assertEquals(LOG + NEW_LINE + COLLAPSED + NEW_LINE + LOG3, logs);
    }
}