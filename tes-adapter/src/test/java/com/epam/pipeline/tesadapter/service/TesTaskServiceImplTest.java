package com.epam.pipeline.tesadapter.service;


import com.epam.pipeline.tesadapter.common.MessageHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TesTaskServiceImplTest {
    private CloudPipelineAPIClient cloudPipelineAPIClient;
    private TesTaskServiceImpl tesTaskService;
    private MessageHelper messageHelper;
    private TaskMapper taskMapper;

    @BeforeEach
    public void setUp() {
        cloudPipelineAPIClient = Mockito.mock(CloudPipelineAPIClient.class);
        messageHelper = Mockito.mock(MessageHelper.class);
        taskMapper = Mockito.mock(TaskMapper.class);
        tesTaskService = new TesTaskServiceImpl(cloudPipelineAPIClient, taskMapper, messageHelper);
    }

    @Test
    void listTesTask() {
        Assertions.assertNotNull(tesTaskService.listTesTask());
    }
}