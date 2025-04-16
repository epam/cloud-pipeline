package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.datastorage.rules.DataStorageRuleDao;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataStorageRuleManagerTest {

    private static final String MOCK_MESSAGE = "Parameter is required!";
    private static final String NAME = "name";
    private static final String FILE_MASK = "*";
    private static final long PIPELINE_ID = 1L;

    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final DataStorageRuleDao dataStorageRuleDao = mock(DataStorageRuleDao.class);
    private final PipelineManager pipelineManager = mock(PipelineManager.class);

    private final DataStorageRuleManager dataStorageRuleManager = new DataStorageRuleManager();

    @Before
    public void setup() {
        Whitebox.setInternalState(dataStorageRuleManager, "messageHelper", messageHelper);
        Whitebox.setInternalState(dataStorageRuleManager, "dataStorageRuleDao", dataStorageRuleDao);
        Whitebox.setInternalState(dataStorageRuleManager, "pipelineManager", pipelineManager);
        when(messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED)).thenReturn(MOCK_MESSAGE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRuleShouldFailIfResultRuleDoesNotHaveName() {
        DataStorageRule dataStorageRule = new DataStorageRule();
        dataStorageRule.setPipelineId(PIPELINE_ID);
        dataStorageRule.setIsResult(true);
        dataStorageRule.setFileMask(FILE_MASK);
        dataStorageRuleManager.createRule(dataStorageRule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createRuleShouldFailIfResultRuleDoesNotHaveMoveToSts() {
        DataStorageRule dataStorageRule = new DataStorageRule();
        dataStorageRule.setPipelineId(PIPELINE_ID);
        dataStorageRule.setIsResult(true);
        dataStorageRule.setName(NAME);
        dataStorageRule.setFileMask(FILE_MASK);
        dataStorageRule.setMoveToSts(false);
        dataStorageRuleManager.createRule(dataStorageRule);
    }

    @Test
    public void createRuleShouldSucceedIfResultRuleHaveName() {
        DataStorageRule dataStorageRule = new DataStorageRule();
        dataStorageRule.setPipelineId(PIPELINE_ID);
        dataStorageRule.setIsResult(false);
        dataStorageRule.setFileMask(FILE_MASK);
        dataStorageRule.setName(NAME);
        dataStorageRuleManager.createRule(dataStorageRule);
    }

    @Test
    public void createRuleShouldSucceedIfSimpleRuleDoesNotHaveName() {
        DataStorageRule dataStorageRule = new DataStorageRule();
        dataStorageRule.setPipelineId(PIPELINE_ID);
        dataStorageRule.setIsResult(false);
        dataStorageRule.setFileMask(FILE_MASK);
        dataStorageRuleManager.createRule(dataStorageRule);
    }
}