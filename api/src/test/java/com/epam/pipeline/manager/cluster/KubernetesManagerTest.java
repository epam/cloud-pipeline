package com.epam.pipeline.manager.cluster;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesManagerTest {

    private static final String NEW_LINE = "\n";
    private static final String LOG = "Simple first log line" + NEW_LINE + "Simple second log line";
    private static final String LOG2 = "Simple second log line" + NEW_LINE + "Simple third log line";
    private static final String LOG3 = "Simple third log line" + NEW_LINE + "Simple fourth log line";
    private static final String COLLAPSED = "< ... >";
    private static final String POD_ID = "pod_id";

    @Spy
    private KubernetesManager manager = new KubernetesManager();

    @Before
    public void setup() {
        when(manager.getKubernetesClient()).thenReturn(new DefaultKubernetesClient());
    }

    @Test
    public void getPodLogsWhenItFitsToSize() {

        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG)
                .when(manager).getTailLog(anyObject(),eq(POD_ID), anyInt(), anyInt());

        String logs = manager.getPodLogs(POD_ID, 256);
        Assert.assertEquals(LOG, logs);
    }

    @Test
    public void getPodLogsWhenItFitsToSizeAndTailOverlapsHead() {
        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG2)
                .when(manager).getTailLog(anyObject(),eq(POD_ID), anyInt(), anyInt());

        String logs = manager.getPodLogs(POD_ID, 64);
        Assert.assertEquals(LOG + NEW_LINE + COLLAPSED + NEW_LINE + "Simple third log line", logs);
    }

    @Test
    public void getPodLogsWhenItDoesNotFitsToSize() {
        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG2)
                .when(manager).getTailLog(anyObject(),eq(POD_ID), anyInt(), anyInt());

        String logs = manager.getPodLogs(POD_ID, 32);
        Assert.assertEquals(LOG + NEW_LINE + COLLAPSED, logs);
    }

    @Test
    public void getPodLogsWhenItDoesNotFitsToSize2() {
        doReturn(LOG)
                .when(manager).getHeadLogs(eq(POD_ID), anyInt(), anyObject());
        doReturn(LOG3)
                .when(manager).getTailLog(anyObject(),eq(POD_ID), anyInt(), anyInt());

        String logs = manager.getPodLogs(POD_ID, 64);
        Assert.assertEquals(LOG + NEW_LINE + COLLAPSED + NEW_LINE + LOG3, logs);
    }
}