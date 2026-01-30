/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.controller;

//import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
/*import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;*/
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletDiskFileUpload;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
//import org.springframework.web.multipart.commons.CommonsMultipartFile;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public abstract class AbstractRestController {

    protected static final int BUF_SIZE = 2 * 1024;
    /**
     * Declares HTTP status OK code value, used to specify this code when REST API
     * is described, using Swagger-compliant annotations. It allows create nice
     * documentation automatically.
     */
    protected static final int HTTP_STATUS_OK = 200;

    /**
     * {@code String} specifies API responses description that explains meaning of different values
     * for $.status JSON path. It's required and used with swagger ApiResponses annotation.
     */
    protected static final String API_STATUS_DESCRIPTION =
            "It results in a response with HTTP status OK, but "
                    + "you should always check $.status, which can take several values:<br/>"
                    + "<b>OK</b> means call has been done without any problems;<br/>"
                    + "<b>ERROR</b> means call has been aborted due to errors (see $.message "
                    + "for details in this case).";

    private static final String NO_FILES_MESSAGE = "No files specified";
    private static final String NOT_A_MULTIPART_REQUEST = "Not a multipart request";
    public static final String FALSE = "false";

    /**
     * Writes passed content to {@code HttpServletResponse} to allow it's downloading from
     * the client
     * @param response to write data
     * @param bytes content to download
     * @param name file name
     * @throws IOException
     */
    protected void writeFileToResponse(HttpServletResponse response, byte[] bytes, String name)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", String.format("attachment;filename=%s", name));
        response.setContentLengthLong(bytes.length);
        try (ServletOutputStream stream = response.getOutputStream()) {
            stream.write(bytes);
            stream.flush();
        }
    }

    /**
     * Processes a multipart file upload as streaming upload
     *
     * @param request a HttpServletRequest to controller
     * @return an InputStream of data, being uploaded
     * @throws IOException
     * @throws FileUploadException
     */
    /*protected InputStream getMultipartStream(HttpServletRequest request) throws IOException, FileUploadException {
        Assert.isTrue(ServletFileUpload.isMultipartContent(request), NOT_A_MULTIPART_REQUEST);
        ServletFileUpload upload = new ServletFileUpload();
        FileItemIterator iterator = upload.getItemIterator(request);

        Assert.isTrue(iterator.hasNext(), NO_FILES_MESSAGE);
        while (iterator.hasNext()) {
            FileItemStream stream = iterator.next();
            if (!stream.isFormField()) {
                return stream.openStream();
            }
        }

        throw new IllegalArgumentException(NO_FILES_MESSAGE);
    }*/

    protected <T> List<T> processStreamingUpload(List<MultipartFile> files,
                                                 BiFunction<InputStream, String, T> uploadMapper)
        throws IOException, FileUploadException {

        if (files.isEmpty()) {
            throw new IllegalArgumentException(NO_FILES_MESSAGE);
        }

        List<T> uploadedResults = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try (var inputStream = file.getInputStream()) {
                    uploadedResults.add(uploadMapper.apply(inputStream, file.getOriginalFilename()));
                }
            }
        }

        if (uploadedResults.isEmpty()) {
            throw new IllegalArgumentException(NO_FILES_MESSAGE);
        }

        return uploadedResults;
    }

    /**
     * Consumes the whole multipart file to memory.
     *
     * @param request a HttpServletRequest to controller
     * @return a {@link MultipartFile}, containing all the file data in memory
     */
    protected MultipartFile consumeMultipartFile(final HttpServletRequest request)
            throws FileUploadException, IOException {
        return consumeMultipartFile(request, Collections.emptySet());
    }

    /**
     * Consumes the whole multipart file to memory.
     *
     * @param request a HttpServletRequest to controller
     * @param allowedExtensions a set of file extensions, that are allowed for uploading. Example: txt, png
     * @return a {@link MultipartFile}, containing all the file data in memory
     */
    protected MultipartFile consumeMultipartFile(final HttpServletRequest request,
                                                 final Set<String> allowedExtensions)
            throws FileUploadException, IOException {
        return consumeMultipartFiles(request, allowedExtensions).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(NO_FILES_MESSAGE));
    }

    /**
     * Consumes all multipart files to memory.
     * <p>
     * This exists for backward compatibility with legacy controller tests that send raw multipart body via
     * {@code .content(...)} rather than using MockMvc's {@code multipart(...)} builder.
     * </p>
     */
    protected List<MultipartFile> consumeMultipartFiles(final HttpServletRequest request)
            throws FileUploadException, IOException {
        return consumeMultipartFiles(request, Collections.emptySet());
    }

    protected List<MultipartFile> consumeMultipartFiles(final HttpServletRequest request,
                                                        final Set<String> allowedExtensions)
            throws FileUploadException, IOException {
        Assert.isTrue(JakartaServletFileUpload.isMultipartContent(request), NOT_A_MULTIPART_REQUEST);
        final JakartaServletDiskFileUpload upload = new JakartaServletDiskFileUpload();
        final List<DiskFileItem> items = upload.parseRequest(request);
        final List<DiskFileItem> fileItems = items.stream()
                .filter(item -> !item.isFormField())
                .toList();
        Assert.isTrue(!fileItems.isEmpty(), NO_FILES_MESSAGE);

        final List<MultipartFile> files = new ArrayList<>();
        for (final DiskFileItem item : fileItems) {
            try {
                final String originalFilename = FilenameUtils.getName(trimToEmpty(item.getName()));
                if (!allowedExtensions.isEmpty()) {
                    final String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
                    Assert.isTrue(allowedExtensions.contains(extension),
                            String.format("File type %s is not allowed for uploading. Allowed types: %s",
                                    extension, allowedExtensions.stream().collect(Collectors.joining(", "))));
                }
                final byte[] bytes = item.get();
                files.add(new InMemoryMultipartFile(item.getFieldName(), originalFilename,
                        item.getContentType(), bytes));
            } finally {
                try {
                    item.delete();
                } catch (final IOException e) {
                    // ignore cleanup errors
                }
            }
        }
        return files;
    }

    private static String trimToEmpty(final String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Simple in-memory MultipartFile.
     */
    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(final String name,
                                      final String originalFilename,
                                      final String contentType,
                                      final byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(final java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }

    protected void writeStreamToResponse(HttpServletResponse response,
                                         InputStream stream,
                                         String fileName) throws IOException {
        writeStreamToResponse(response, stream, fileName, MediaType.APPLICATION_OCTET_STREAM);
    }

    protected void writeStreamToResponse(HttpServletResponse response,
                                         InputStream stream,
                                         String fileName,
                                         MediaType contentType) throws IOException {
        writeStreamToResponse(response, stream, fileName, contentType, false);
    }

    protected void writeStreamToResponse(HttpServletResponse response,
                                         InputStream stream,
                                         String fileName,
                                         MediaType contentType,
                                         boolean inline) throws IOException {
        try (InputStream in = stream) {
            writeToResponse(response,
                    ResultWriter.checked(fileName, out -> IOUtils.copy(in, out)), contentType, inline);
        }
    }

    protected void writeStreamToResponse(HttpServletResponse response,
                                         InputStream stream,
                                         String fileName,
                                         MediaType contentType,
                                         boolean inline,
                                         Map<String, String> headers) throws IOException {
        try (InputStream in = stream) {
            writeToResponse(response,
                    ResultWriter.checked(fileName, out -> IOUtils.copy(in, out)), contentType, inline, headers);
        }
    }

    protected void writeToResponse(final HttpServletResponse response,
                                   final ResultWriter writer) throws IOException {
        writeToResponse(response, writer, MediaType.APPLICATION_OCTET_STREAM);
    }

    protected void writeToResponse(final HttpServletResponse response,
                                   final ResultWriter writer,
                                   final MediaType contentType) throws IOException {
        writeToResponse(response, writer, contentType, false);
    }

    protected void writeToResponse(final HttpServletResponse response,
                                   final ResultWriter writer,
                                   final MediaType contentType,
                                   final boolean inline) throws IOException {
        writeToResponse(response, writer, contentType, inline, Collections.emptyMap());
    }

    protected void writeToResponse(final HttpServletResponse response,
                                   final ResultWriter writer,
                                   final MediaType contentType,
                                   final boolean inline,
                                   final Map<String, String> headers) throws IOException {
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, getContentDisposition(writer, inline));
        response.setContentType(contentType.toString());
        MapUtils.emptyIfNull(headers).forEach(response::setHeader);
        writer.write(response);
        response.flushBuffer();
    }

    private String getContentDisposition(final ResultWriter writer, final boolean inline) {
        final String disposition = inline ? "inline": "attachment";
        return disposition + ";filename=" + writer.getName();
    }

    protected MediaType guessMediaType(String fileName) {
        switch (FilenameUtils.getExtension(fileName)) {
            case "gif":
                return MediaType.IMAGE_GIF;
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "jpg":
            case "png":
                return MediaType.IMAGE_PNG;
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * Consumes the whole multipart file to memory
     * @param request a HttpServletRequest to controller
     * @return a {@link MultipartFile}, containing all the dile data in memory
     * @throws FileUploadException
     */
    /*protected MultipartFile consumeMultipartFile(HttpServletRequest request) throws FileUploadException {
        return consumeMultipartFile(request, Collections.emptySet());
    }*/

    /**
     * Consumes the whole multipart file to memory
     * @param request a HttpServletRequest to controller
     * @param allowedExtensions a set of file extensions, that are allowed for uploading. Example: txt, png
     * @return a {@link MultipartFile}, containing all the dile data in memory
     * @throws FileUploadException
     */
    /*protected MultipartFile consumeMultipartFile(HttpServletRequest request, Set<String> allowedExtensions)
        throws FileUploadException {
        Assert.isTrue(ServletFileUpload.isMultipartContent(request), NOT_A_MULTIPART_REQUEST);
        FileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        List<FileItem> items = upload.parseRequest(request);
        MultipartFile file = new CommonsMultipartFile(items.stream()
                                            .findFirst()
                                            .orElseThrow(() -> new IllegalArgumentException(NO_FILES_MESSAGE))
        );

        if (CollectionUtils.isNotEmpty(allowedExtensions)) {
            String extension = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase();
            Assert.isTrue(allowedExtensions.contains(extension),
                          String.format("File type %s is not allowed for uploading. Allowed types: %s", extension,
                                        allowedExtensions.stream().collect(Collectors.joining(", "))));
        }

        return file;
    }*/

}
