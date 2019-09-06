package com.epam.pipeline.tesadapter.service;


import com.epam.pipeline.tesadapter.common.MessageHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TesTaskServiceImplTest {
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    private TesTaskServiceImpl tesTaskService;

    @BeforeEach
    public void setUp() {
        cloudPipelineAPIClient = Mockito.mock(CloudPipelineAPIClient.class);
        tesTaskService = new TesTaskServiceImpl(cloudPipelineAPIClient, Mockito.mock(TaskMapper.class), Mockito.mock(MessageHelper.class));
    }

    @Test
    void listTesTask() {
        Assertions.assertNotNull(tesTaskService.listTesTask());
    }
}