package com.epam.pipeline.entity;

public interface SecuredStorageEntity {

    Long getRootId();
    String getOwner();
    String resolveAbsolutePath(String relativePath);
    boolean isVersioningEnabled();

}
