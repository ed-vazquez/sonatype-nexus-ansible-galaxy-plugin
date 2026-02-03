package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 API pagination metadata: {count}.
 */
public class GalaxyPaginationMeta {

  @JsonProperty("count")
  private int count;

  public GalaxyPaginationMeta() {
  }

  public GalaxyPaginationMeta(final int count) {
    this.count = count;
  }

  public int getCount() {
    return count;
  }

  public void setCount(final int count) {
    this.count = count;
  }
}
