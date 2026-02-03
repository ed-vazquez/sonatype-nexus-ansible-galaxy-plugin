package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 version list entry.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GalaxyCollectionVersion {

  @JsonProperty("href")
  private String href;

  @JsonProperty("version")
  private String version;

  public GalaxyCollectionVersion() {
  }

  public GalaxyCollectionVersion(final String version, final String href) {
    this.version = version;
    this.href = href;
  }

  public String getHref() {
    return href;
  }

  public void setHref(final String href) {
    this.href = href;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }
}
