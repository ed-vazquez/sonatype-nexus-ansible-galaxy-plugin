package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 collection list entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GalaxyCollection {

  @JsonProperty("href")
  private String href;

  @JsonProperty("namespace")
  private String namespace;

  @JsonProperty("name")
  private String name;

  @JsonProperty("deprecated")
  private boolean deprecated;

  @JsonProperty("versions_url")
  private String versionsUrl;

  @JsonProperty("highest_version")
  private GalaxyCollectionVersion highestVersion;

  public GalaxyCollection() {
  }

  public String getHref() {
    return href;
  }

  public void setHref(final String href) {
    this.href = href;
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

  public boolean isDeprecated() {
    return deprecated;
  }

  public void setDeprecated(final boolean deprecated) {
    this.deprecated = deprecated;
  }

  public String getVersionsUrl() {
    return versionsUrl;
  }

  public void setVersionsUrl(final String versionsUrl) {
    this.versionsUrl = versionsUrl;
  }

  public GalaxyCollectionVersion getHighestVersion() {
    return highestVersion;
  }

  public void setHighestVersion(final GalaxyCollectionVersion highestVersion) {
    this.highestVersion = highestVersion;
  }
}
