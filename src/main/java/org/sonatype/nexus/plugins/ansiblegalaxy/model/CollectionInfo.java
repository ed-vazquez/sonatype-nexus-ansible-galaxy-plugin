package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the collection_info section from MANIFEST.json inside an Ansible Galaxy collection tar.gz.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectionInfo {

  @JsonProperty("namespace")
  private String namespace;

  @JsonProperty("name")
  private String name;

  @JsonProperty("version")
  private String version;

  @JsonProperty("description")
  private String description;

  public CollectionInfo() {
  }

  public CollectionInfo(final String namespace, final String name, final String version) {
    this.namespace = namespace;
    this.name = name;
    this.version = version;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(final String namespace) {
    this.namespace = namespace;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }
}
