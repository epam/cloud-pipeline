package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import java.util.List;

@ApiModel(description = "ServiceInfo describes information about the service, such as storage details, " +
        "resource availability, and other documentation.")
@Data
public class TesServiceInfo {
    @ApiModelProperty(value = "Returns the name of the service, e.g. \"ohsu-compbio-funnel\".")
    @JsonProperty("name")
    private String name;

    @ApiModelProperty(value = "Returns a documentation string, e.g. \"Hey, we're OHSU Comp. Bio!\".")
    @JsonProperty("doc")
    private String doc;

    @ApiModelProperty(value = "Lists some, but not necessarily all, storage locations supported by the service. " +
            " Must be in a valid URL format. e.g.  file:///path/to/local/funnel-storage " +
            "s3://ohsu-compbio-funnel/storage etc.")
    @JsonProperty("storage")
    @Valid
    private List<String> storage;
}

