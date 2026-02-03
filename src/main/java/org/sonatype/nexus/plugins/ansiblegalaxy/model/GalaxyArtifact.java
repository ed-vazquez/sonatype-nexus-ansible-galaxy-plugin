package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 artifact info: {filename, sha256, size}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GalaxyArtifact {

  @JsonProperty("filename")
  private String filename;

  @JsonProperty("sha256")
  private String sha256;

  @JsonProperty("size")
  private long size;

  public GalaxyArtifact() {
  }

  public GalaxyArtifact(final String filename, final String sha256, final long size) {
    this.filename = filename;
    this.sha256 = sha256;
    this.size = size;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(final String filename) {
    this.filename = filename;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(final String sha256) {
    this.sha256 = sha256;
  }

  public long getSize() {
    return size;
  }

  public void setSize(final long size) {
    this.size = size;
  }
}
