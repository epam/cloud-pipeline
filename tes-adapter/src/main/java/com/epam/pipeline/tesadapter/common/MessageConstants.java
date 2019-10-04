package com.epam.pipeline.tesadapter.common;

public final class MessageConstants {

    //Common errors
    public static final String ERROR_PARAMETER_REQUIRED = "error.parameter.required";
    public static final String ERROR_PARAMETER_NULL_OR_EMPTY = "error.null.param";
    public static final String ERROR_PARAMETER_INCOMPATIBLE_CONTENT = "error.parameter.incompatible.content";

    //Parameters mapping
    public static final String ERROR_PARAMETER_NON_SCALAR_TYPE = "error.parameter.non.scalar.type";

    //Auth messages
    public static final String TOKEN_FOUND_IN_REQUEST = "debug.token.found.in.request";
    public static final String IP_ACCEPTED = "debug.ip.is.accepted";
    public static final String NO_MATCHED_AUTH_METHODS = "debug.no.matched.auth.methods";
}
