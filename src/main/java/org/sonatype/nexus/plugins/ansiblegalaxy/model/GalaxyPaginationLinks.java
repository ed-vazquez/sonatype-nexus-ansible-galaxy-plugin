package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 API pagination links: {first, previous, next, last}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GalaxyPaginationLinks {

  @JsonProperty("first")
  private String first;

  @JsonProperty("previous")
  private String previous;

  @JsonProperty("next")
  private String next;

  @JsonProperty("last")
  private String last;

  public GalaxyPaginationLinks() {
  }

  public String getFirst() {
    return first;
  }

  public void setFirst(final String first) {
    this.first = first;
  }

  public String getPrevious() {
    return previous;
  }

  public void setPrevious(final String previous) {
    this.previous = previous;
  }

  public String getNext() {
    return next;
  }

  public void setNext(final String next) {
    this.next = next;
  }

  public String getLast() {
    return last;
  }

  public void setLast(final String last) {
    this.last = last;
  }
}
