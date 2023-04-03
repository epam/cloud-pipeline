package com.epam.pipeline.security.acl.redis;

public final class AclImplFields {
    public static final String OBJ_ID = "oi_id";
    public static final String OBJ_TYPE = "oi_type";
    public static final String ID = "id";
    public static final String INHERIT = "inherit";
    public static final String OWNER = "owner";
    public static final String PARENT = "parent";
    public static final String ACES = "aces";
    public static final String ACE_IS_PRINCIPAL = "isPrincipal";
    public static final String ACE_SID = "sid";
    public static final String ACE_MASK = "mask";
    public static final String ACE_GRANTING = "granting";

    private AclImplFields() {
        //
    }
}
