package com.epam.pipeline.manager.datastorage.convert;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestAction;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestType;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DataStorageConvertManagerTest {

    private static final DataStorageConvertRequestType TARGET_TYPE = DataStorageConvertRequestType.VERSIONED_STORAGE;
    private static final DataStorageConvertRequestAction SOURCE_ACTION = DataStorageConvertRequestAction.LEAVE;
    private static final AbstractDataStorage STORAGE = DatastorageCreatorUtils.getNfsDataStorage();
    private static final Pipeline TARGET = PipelineCreatorUtils.getPipeline();

    private final DataStorageManager dataStorageManager = mock(DataStorageManager.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final DataStorageToSecuredEntityConvertManager innerManager = mockInnerManager();
    private final List<DataStorageToSecuredEntityConvertManager> innerManagers =
            Collections.singletonList(innerManager);
    private final DataStorageConvertManager manager = new DataStorageConvertManager(dataStorageManager,
            preferenceManager, messageHelper, innerManagers);

    @Before
    public void setUp() {
        doReturn(STORAGE).when(dataStorageManager).load(CommonCreatorConstants.ID);
    }

    @Test
    public void convertShouldUseVersionedStorageTargetTypeIfNotSpecified() {
        manager.convert(CommonCreatorConstants.ID, request());

        verify(innerManager).convert(STORAGE, request(SOURCE_ACTION));
    }

    @Test
    public void convertShouldConvertDataStorage() {
        final AbstractSecuredEntity target = manager.convert(CommonCreatorConstants.ID, request());

        assertThat(target, is(TARGET));
    }

    @Test
    public void convertShouldLeaveSourceDataStorageIfLeaveSourceActionIsSpecified() {
        manager.convert(CommonCreatorConstants.ID, request(DataStorageConvertRequestAction.LEAVE));

        verify(dataStorageManager, never()).delete(any(), anyBoolean());
    }

    @Test
    public void convertShouldUnregisterSourceDataStorageIfUnregisterSourceActionIsSpecified() {
        manager.convert(CommonCreatorConstants.ID, request(DataStorageConvertRequestAction.UNREGISTER));

        verify(dataStorageManager).delete(CommonCreatorConstants.ID, false);
    }

    @Test
    public void convertShouldDeleteSourceDataStorageIfDeleteSourceActionIsSpecified() {
        manager.convert(CommonCreatorConstants.ID, request(DataStorageConvertRequestAction.DELETE));

        verify(dataStorageManager).delete(CommonCreatorConstants.ID, true);
    }

    @Test
    public void convertShouldUseSourceActionFromPreferencesIfNotSpecified() {
        doReturn(TARGET_TYPE.name())
                .when(preferenceManager)
                .getStringPreference(SystemPreferences.STORAGE_CONVERT_SOURCE_ACTION.getKey());

        manager.convert(CommonCreatorConstants.ID, requestWithoutAction());

        verify(dataStorageManager, never()).delete(any(), anyBoolean());
    }

    @Test
    public void convertShouldLeaveSourceDataStorageIfNotSpecifiedAndPreferenceIsNotSet() {
        manager.convert(CommonCreatorConstants.ID, requestWithoutAction());

        verify(dataStorageManager, never()).delete(any(), anyBoolean());
    }

    private DataStorageToSecuredEntityConvertManager mockInnerManager() {
        final DataStorageToSecuredEntityConvertManager innerManager =
                mock(DataStorageToSecuredEntityConvertManager.class);
        doReturn(TARGET_TYPE).when(innerManager).getTargetType();
        doReturn(TARGET).when(innerManager).convert(any(), any());
        return innerManager;
    }

    private DataStorageConvertRequest requestWithoutAction() {
        return request(null);
    }

    private DataStorageConvertRequest request() {
        return request(SOURCE_ACTION);
    }

    private DataStorageConvertRequest request(final DataStorageConvertRequestAction action) {
        return new DataStorageConvertRequest(TARGET_TYPE, action);
    }

}
