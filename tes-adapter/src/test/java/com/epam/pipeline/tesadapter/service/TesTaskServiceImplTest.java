package com.epam.pipeline.tesadapter.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TesTaskServiceImplTest {
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    private TesTaskServiceImpl tesTaskService;

    @BeforeAll
    void setUp() {
        cloudPipelineAPIClient = Mockito.mock(CloudPipelineAPIClient.class);
        tesTaskService = new TesTaskServiceImpl(cloudPipelineAPIClient);
    }

    @Test
    void listTesTask() {
        Assertions.assertNotNull(tesTaskService.listTesTask());
    }
}