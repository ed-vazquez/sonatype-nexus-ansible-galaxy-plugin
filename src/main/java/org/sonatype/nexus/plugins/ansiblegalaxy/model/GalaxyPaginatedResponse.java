package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 API paginated response: {meta, links, data}.
 */
public class GalaxyPaginatedResponse<T> {

  @JsonProperty("meta")
  private GalaxyPaginationMeta meta;

  @JsonProperty("links")
  private GalaxyPaginationLinks links;

  @JsonProperty("data")
  private List<T> data;

  public GalaxyPaginatedResponse() {
  }

  public GalaxyPaginatedResponse(final GalaxyPaginationMeta meta,
                                 final GalaxyPaginationLinks links,
                                 final List<T> data) {
    this.meta = meta;
    this.links = links;
    this.data = data;
  }

  public GalaxyPaginationMeta getMeta() {
    return meta;
  }

  public void setMeta(final GalaxyPaginationMeta meta) {
    this.meta = meta;
  }

  public GalaxyPaginationLinks getLinks() {
    return links;
  }

  public void setLinks(final GalaxyPaginationLinks links) {
    this.links = links;
  }

  public List<T> getData() {
    return data;
  }

  public void setData(final List<T> data) {
    this.data = data;
  }
}
