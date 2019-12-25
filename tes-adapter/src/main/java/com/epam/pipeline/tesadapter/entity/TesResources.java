package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ApiModel(description = "Resources describes the resources requested by a task.")
public class TesResources {
    @ApiModelProperty(value = "Requested number of CPUs")
    @JsonProperty("cpu_cores")
    private Long cpuCores;

    @ApiModelProperty(value = "Is the task allowed to run on preemptible compute instances (e.g. AWS Spot)?")
    @JsonProperty("preemptible")
    private Boolean preemptible;

    @ApiModelProperty(value = "Requested RAM required in gigabytes (GB)")
    @JsonProperty("ram_gb")
    private Double ramGb;

    @ApiModelProperty(value = "Requested disk size in gigabytes (GB)")
    @JsonProperty("disk_gb")
    private Double diskGb;

    @ApiModelProperty(value = "Request that the task be run in these compute zones.")
    @JsonProperty("zones")
    @Valid
    private List<String> zones;
}

