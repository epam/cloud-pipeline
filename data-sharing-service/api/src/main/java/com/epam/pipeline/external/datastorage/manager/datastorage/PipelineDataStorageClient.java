/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.manager.datastorage;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageItemType;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.rest.Result;
import com.epam.pipeline.vo.GenerateDownloadUrlVO;
import com.epam.pipeline.vo.data.storage.UpdateDataStorageItemVO;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;


public interface PipelineDataStorageClient {

    String PATH = "path";
    String VERSION = "version";
    String ID = "id";
    String SHOW_VERSION = "showVersion";
    String AUTHORIZATION = "Authorization";

    @GET("restapi/datastorage/findByPath")
    Call<Result<AbstractDataStorage>> getStorage(@Query(ID) String id, @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/load")
    Call<Result<AbstractDataStorage>> getStorage(@Path(ID) long id, @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/list")
    Call<Result<List<AbstractDataStorageItem>>> getStorageContent(@Path(ID) long id,
                                                                  @Query(PATH) String path,
                                                                  @Query(SHOW_VERSION) Boolean showVersion,
                                                                  @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/list/page")
    Call<Result<DataStorageListing>> getStorageContent(@Path(ID) long id,
                                                       @Query(PATH) String path,
                                                       @Query(SHOW_VERSION) Boolean showVersion,
                                                       @Query("pageSize") Integer pageSize,
                                                       @Query("marker") String marker,
                                                       @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/tags/list")
    Call<Result<AbstractDataStorageItem>> getItemWithTags(@Path(ID) long id,
                                                          @Query(PATH) String path,
                                                          @Query(SHOW_VERSION) Boolean showVersion,
                                                          @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/tags")
    Call<Result<Map<String, String>>> getItemTags(@Path(ID) long id,
                                                  @Query(PATH) String path,
                                                  @Query(VERSION) String version,
                                                  @Header(AUTHORIZATION) String token);

    @POST("restapi/datastorage/{id}/tags")
    Call<Result<Map<String, String>>> updateItemTags(@Path(ID) long id,
                                                     @Query(PATH) String path,
                                                     @Body Map<String, String> tags,
                                                     @Query(VERSION) String version,
                                                     @Query("rewrite") Boolean rewrite,
                                                     @Header(AUTHORIZATION) String token);

    @HTTP(method = "DELETE", path = "restapi/datastorage/{id}/tags", hasBody = true)
    Call<Result<Map<String, String>>> deleteItemTags(@Path(ID) long id,
                                                     @Query(PATH) String path,
                                                     @Body Set<String> tags,
                                                     @Query(VERSION) String version,
                                                     @Header(AUTHORIZATION) String token);

    @POST("restapi/datastorage/tempCredentials/")
    Call<Result<TemporaryCredentials>> generateCredentials(@Body List<DataStorageAction> operations,
                                                           @Header(AUTHORIZATION) String token);

    @POST("restapi/datastorage/{id}/list")
    Call<Result<List<AbstractDataStorageItem>>> updateItems(@Path(ID) long id,
                                                            @Body List<UpdateDataStorageItemVO> items,
                                                            @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/content")
    Call<Result<DataStorageItemContent>> downloadItem(@Path(ID) long id,
                                                      @Query(PATH) String path,
                                                      @Query(VERSION) String version,
                                                      @Header(AUTHORIZATION) String token);

    @HTTP(method = "DELETE", path = "restapi/datastorage/{id}/list", hasBody = true)
    Call<Result<Integer>> deleteItems(@Path(ID) long id, @Query("totally") boolean totally,
                                      @Body List<UpdateDataStorageItemVO> items,
                                      @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/generateUrl")
    Call<Result<DataStorageDownloadFileUrl>> generateDownloadUrl(@Path(ID) long id,
                                                                 @Query(PATH) String path,
                                                                 @Query(VERSION) String version,
                                                                 @Header(AUTHORIZATION) String token);
    @POST("restapi/datastorage/{id}/generateUrl")
    Call<Result<List<DataStorageDownloadFileUrl>>> generateDownloadUrl(@Path(ID) long id,
                                                                       @Body GenerateDownloadUrlVO paths,
                                                                       @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/generateUploadUrl")
    Call<Result<DataStorageDownloadFileUrl>> generateUploadUrl(@Path(ID) long id,
                                                                 @Query(PATH) String path,
                                                                 @Header(AUTHORIZATION) String token);

    @POST("restapi/datastorage/{id}/content")
    Call<Result<DataStorageFile>> createDataStorageFile(@Path(ID) long id,
                                                        @Query(PATH) String path,
                                                        @Body String content,
                                                        @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/download")
    Call<ResponseBody> downloadFile(@Path(ID) long id,
                                              @Query(PATH) String path,
                                              @Query(VERSION) String version,
                                              @Header(AUTHORIZATION) String token);

    @GET("restapi/datastorage/{id}/type")
    Call<Result<DataStorageItemType>> getItemType(@Path(ID) Long storageId,
                                                  @Query(PATH) String path,
                                                  @Header(AUTHORIZATION) String token);
}
