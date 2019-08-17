package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resources describes the resources requested by a task.
 */
@ApiModel(description = "Resources describes the resources requested by a task.")
@Validated
@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2019-08-17T00:22:00.237+03:00")

public class TesResources   {
  @JsonProperty("cpu_cores")
  private Long cpuCores = null;

  @JsonProperty("preemptible")
  private Boolean preemptible = null;

  @JsonProperty("ram_gb")
  private Double ramGb = null;

  @JsonProperty("disk_gb")
  private Double diskGb = null;

  @JsonProperty("zones")
  @Valid
  private List<String> zones = null;

  public TesResources cpuCores(Long cpuCores) {
    this.cpuCores = cpuCores;
    return this;
  }

  /**
   * Requested number of CPUs
   * @return cpuCores
  **/
  @ApiModelProperty(value = "Requested number of CPUs")


  public Long getCpuCores() {
    return cpuCores;
  }

  public void setCpuCores(Long cpuCores) {
    this.cpuCores = cpuCores;
  }

  public TesResources preemptible(Boolean preemptible) {
    this.preemptible = preemptible;
    return this;
  }

  /**
   * Is the task allowed to run on preemptible compute instances (e.g. AWS Spot)?
   * @return preemptible
  **/
  @ApiModelProperty(value = "Is the task allowed to run on preemptible compute instances (e.g. AWS Spot)?")


  public Boolean isPreemptible() {
    return preemptible;
  }

  public void setPreemptible(Boolean preemptible) {
    this.preemptible = preemptible;
  }

  public TesResources ramGb(Double ramGb) {
    this.ramGb = ramGb;
    return this;
  }

  /**
   * Requested RAM required in gigabytes (GB)
   * @return ramGb
  **/
  @ApiModelProperty(value = "Requested RAM required in gigabytes (GB)")


  public Double getRamGb() {
    return ramGb;
  }

  public void setRamGb(Double ramGb) {
    this.ramGb = ramGb;
  }

  public TesResources diskGb(Double diskGb) {
    this.diskGb = diskGb;
    return this;
  }

  /**
   * Requested disk size in gigabytes (GB)
   * @return diskGb
  **/
  @ApiModelProperty(value = "Requested disk size in gigabytes (GB)")


  public Double getDiskGb() {
    return diskGb;
  }

  public void setDiskGb(Double diskGb) {
    this.diskGb = diskGb;
  }

  public TesResources zones(List<String> zones) {
    this.zones = zones;
    return this;
  }

  public TesResources addZonesItem(String zonesItem) {
    if (this.zones == null) {
      this.zones = new ArrayList<String>();
    }
    this.zones.add(zonesItem);
    return this;
  }

  /**
   * Request that the task be run in these compute zones.
   * @return zones
  **/
  @ApiModelProperty(value = "Request that the task be run in these compute zones.")


  public List<String> getZones() {
    return zones;
  }

  public void setZones(List<String> zones) {
    this.zones = zones;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TesResources tesResources = (TesResources) o;
    return Objects.equals(this.cpuCores, tesResources.cpuCores) &&
        Objects.equals(this.preemptible, tesResources.preemptible) &&
        Objects.equals(this.ramGb, tesResources.ramGb) &&
        Objects.equals(this.diskGb, tesResources.diskGb) &&
        Objects.equals(this.zones, tesResources.zones);
  }

  @Override
  public int hashCode() {
    return Objects.hash(cpuCores, preemptible, ramGb, diskGb, zones);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TesResources {\n");
    
    sb.append("    cpuCores: ").append(toIndentedString(cpuCores)).append("\n");
    sb.append("    preemptible: ").append(toIndentedString(preemptible)).append("\n");
    sb.append("    ramGb: ").append(toIndentedString(ramGb)).append("\n");
    sb.append("    diskGb: ").append(toIndentedString(diskGb)).append("\n");
    sb.append("    zones: ").append(toIndentedString(zones)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

