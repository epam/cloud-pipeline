package com.epam.pipeline.manager.security.acl.storage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Inherited
@Retention(RUNTIME)
@Target(ElementType.METHOD)
public @interface StorageAclList {
}
