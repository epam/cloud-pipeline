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
 * ServiceInfo describes information about the service, such as storage details, resource availability, and other documentation.
 */
@ApiModel(description = "ServiceInfo describes information about the service, such as storage details, resource availability, and other documentation.")
@Validated
@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2019-08-17T00:22:00.237+03:00")

public class TesServiceInfo   {
  @JsonProperty("name")
  private String name = null;

  @JsonProperty("doc")
  private String doc = null;

  @JsonProperty("storage")
  @Valid
  private List<String> storage = null;

  public TesServiceInfo name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Returns the name of the service, e.g. \"ohsu-compbio-funnel\".
   * @return name
  **/
  @ApiModelProperty(value = "Returns the name of the service, e.g. \"ohsu-compbio-funnel\".")


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public TesServiceInfo doc(String doc) {
    this.doc = doc;
    return this;
  }

  /**
   * Returns a documentation string, e.g. \"Hey, we're OHSU Comp. Bio!\".
   * @return doc
  **/
  @ApiModelProperty(value = "Returns a documentation string, e.g. \"Hey, we're OHSU Comp. Bio!\".")


  public String getDoc() {
    return doc;
  }

  public void setDoc(String doc) {
    this.doc = doc;
  }

  public TesServiceInfo storage(List<String> storage) {
    this.storage = storage;
    return this;
  }

  public TesServiceInfo addStorageItem(String storageItem) {
    if (this.storage == null) {
      this.storage = new ArrayList<String>();
    }
    this.storage.add(storageItem);
    return this;
  }

  /**
   * Lists some, but not necessarily all, storage locations supported by the service.  Must be in a valid URL format. e.g.  file:///path/to/local/funnel-storage s3://ohsu-compbio-funnel/storage etc.
   * @return storage
  **/
  @ApiModelProperty(value = "Lists some, but not necessarily all, storage locations supported by the service.  Must be in a valid URL format. e.g.  file:///path/to/local/funnel-storage s3://ohsu-compbio-funnel/storage etc.")


  public List<String> getStorage() {
    return storage;
  }

  public void setStorage(List<String> storage) {
    this.storage = storage;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TesServiceInfo tesServiceInfo = (TesServiceInfo) o;
    return Objects.equals(this.name, tesServiceInfo.name) &&
        Objects.equals(this.doc, tesServiceInfo.doc) &&
        Objects.equals(this.storage, tesServiceInfo.storage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, doc, storage);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TesServiceInfo {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    doc: ").append(toIndentedString(doc)).append("\n");
    sb.append("    storage: ").append(toIndentedString(storage)).append("\n");
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

