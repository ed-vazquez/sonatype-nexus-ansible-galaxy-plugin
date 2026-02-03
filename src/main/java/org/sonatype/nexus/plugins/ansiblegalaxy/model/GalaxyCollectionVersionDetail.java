package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Galaxy v3 full version detail with download_url and artifact.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GalaxyCollectionVersionDetail {

  @JsonProperty("href")
  private String href;

  @JsonProperty("namespace")
  private String namespace;

  @JsonProperty("name")
  private String name;

  @JsonProperty("version")
  private String version;

  @JsonProperty("download_url")
  private String downloadUrl;

  @JsonProperty("artifact")
  private GalaxyArtifact artifact;

  @JsonProperty("collection")
  private GalaxyCollectionRef collection;

  public GalaxyCollectionVersionDetail() {
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

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }

  public void setDownloadUrl(final String downloadUrl) {
    this.downloadUrl = downloadUrl;
  }

  public GalaxyArtifact getArtifact() {
    return artifact;
  }

  public void setArtifact(final GalaxyArtifact artifact) {
    this.artifact = artifact;
  }

  public GalaxyCollectionRef getCollection() {
    return collection;
  }

  public void setCollection(final GalaxyCollectionRef collection) {
    this.collection = collection;
  }

  /**
   * Nested reference to the parent collection.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class GalaxyCollectionRef {

    @JsonProperty("href")
    private String href;

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("name")
    private String name;

    public GalaxyCollectionRef() {
    }

    public GalaxyCollectionRef(final String href, final String namespace, final String name) {
      this.href = href;
      this.namespace = namespace;
      this.name = name;
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
  }
}
