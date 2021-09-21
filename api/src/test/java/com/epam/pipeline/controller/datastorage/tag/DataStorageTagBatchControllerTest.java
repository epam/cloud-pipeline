package com.epam.pipeline.controller.datastorage.tag;

import com.epam.pipeline.acl.datastorage.tag.DataStorageTagBatchApiService;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTag;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagCopyRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteAllRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagDeleteRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagInsertRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadBatchRequest;
import com.epam.pipeline.entity.datastorage.tag.DataStorageTagLoadRequest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.OBJECT_TYPE;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.DATA_STORAGE_TAG_LIST_TYPE;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = DataStorageTagBatchController.class)
public class DataStorageTagBatchControllerTest extends AbstractControllerTest {
    
    private static final long ID = 1L;
    private static final String TAGS_URL = SERVLET_PATH + "/datastorage/%d/tags/batch";
    private static final String TAGS_INSERT_URL = TAGS_URL + "/insert";
    private static final String TAGS_COPY_URL = TAGS_URL + "/copy";
    private static final String TAGS_LOAD_URL = TAGS_URL + "/load";
    private static final String TAGS_DELETE_URL = TAGS_URL + "/delete";
    private static final String TAGS_DELETE_ALL_URL = TAGS_URL + "/deleteAll";

    private static final DataStorageTag OBJECT_TAG = DatastorageCreatorUtils.getDataStorageTag();
    private static final List<DataStorageTag> OBJECT_TAGS = Collections.singletonList(OBJECT_TAG);

    @Autowired
    private DataStorageTagBatchApiService apiService;
    
    @Test
    public void shouldFailInsertTagsForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(TAGS_INSERT_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldInsertTags() {
        final DataStorageTagInsertBatchRequest request = new DataStorageTagInsertBatchRequest(
                Collections.singletonList(
                        new DataStorageTagInsertRequest(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING)));
        doReturn(OBJECT_TAGS).when(apiService).insert(ID, request);

        final MvcResult mvcResult = performRequest(put(String.format(TAGS_INSERT_URL, ID)).content(stringOf(request)));

        verify(apiService).insert(ID, request);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }
    @Test
    public void shouldFailCopyTagsForUnauthorizedUser() {
        performUnauthorizedRequest(put(String.format(TAGS_COPY_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCopyTags() {
        final DataStorageTagCopyBatchRequest request = new DataStorageTagCopyBatchRequest(Collections.singletonList(
                new DataStorageTagCopyRequest(
                        new DataStorageTagCopyRequest.DataStorageTagCopyRequestObject(TEST_STRING, TEST_STRING),
                        new DataStorageTagCopyRequest.DataStorageTagCopyRequestObject(TEST_STRING, TEST_STRING))));
        doReturn(OBJECT_TAGS).when(apiService).copy(ID, request);

        final MvcResult mvcResult = performRequest(put(String.format(TAGS_COPY_URL, ID)).content(stringOf(request)));

        verify(apiService).copy(ID, request);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }

    @Test
    public void shouldFailLoadTagsForUnauthorizedUser() {
        performUnauthorizedRequest(post(String.format(TAGS_LOAD_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadTags() {
        final DataStorageTagLoadBatchRequest request = new DataStorageTagLoadBatchRequest(Collections.singletonList(
                new DataStorageTagLoadRequest(TEST_STRING)));
        doReturn(OBJECT_TAGS).when(apiService).load(ID, request);

        final MvcResult mvcResult = performRequest(post(String.format(TAGS_LOAD_URL, ID)).content(stringOf(request)));

        verify(apiService).load(ID, request);
        assertResponse(mvcResult, OBJECT_TAGS, DATA_STORAGE_TAG_LIST_TYPE);
    }

    @Test
    public void shouldFailDeleteTagsForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(TAGS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteTags() {
        final DataStorageTagDeleteBatchRequest request = new DataStorageTagDeleteBatchRequest(
                Collections.singletonList(new DataStorageTagDeleteRequest(TEST_STRING, TEST_STRING)));

        final MvcResult mvcResult = performRequest(delete(String.format(TAGS_DELETE_URL, ID))
                .content(stringOf(request)));

        verify(apiService).delete(ID, request);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }

    @Test
    public void shouldFailDeleteAllTagsForUnauthorizedUser() {
        performUnauthorizedRequest(delete(String.format(TAGS_DELETE_ALL_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAllTags() {
        final DataStorageTagDeleteAllBatchRequest request = new DataStorageTagDeleteAllBatchRequest(
                Collections.singletonList(new DataStorageTagDeleteAllRequest(TEST_STRING)));

        final MvcResult mvcResult = performRequest(delete(String.format(TAGS_DELETE_ALL_URL, ID))
                .content(stringOf(request)));

        verify(apiService).deleteAll(ID, request);
        assertResponse(mvcResult, null, OBJECT_TYPE);
    }
}
