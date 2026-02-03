package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyArtifact;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyCollection;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyCollectionVersion;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyCollectionVersionDetail;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyCollectionVersionDetail.GalaxyCollectionRef;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyPaginatedResponse;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyPaginationLinks;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.GalaxyPaginationMeta;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;

/**
 * Builds Galaxy v3 API JSON responses from stored components and assets.
 */
@Named
@Singleton
public class GalaxyResponseBuilder {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String API_PREFIX = "/api/v3/plugin/ansible/content/published";

  private static final int DEFAULT_PAGE_SIZE = 100;

  /**
   * Builds a paginated collection list from all components.
   */
  public String buildCollectionList(final String baseUrl,
                                    final Iterable<FluentComponent> components,
                                    final int offset,
                                    final int limit) throws JsonProcessingException {
    // Collect unique namespace/name pairs with their highest version
    Map<String, CollectionEntry> collections = new LinkedHashMap<>();

    for (FluentComponent component : components) {
      String ns = component.namespace();
      String name = component.name();
      String version = component.version();
      String key = ns + "/" + name;

      CollectionEntry entry = collections.computeIfAbsent(key,
          k -> new CollectionEntry(ns, name));
      entry.addVersion(version);
    }

    List<CollectionEntry> allEntries = new ArrayList<>(collections.values());
    int total = allEntries.size();
    int effectiveLimit = limit > 0 ? limit : DEFAULT_PAGE_SIZE;
    int effectiveOffset = Math.max(0, Math.min(offset, total));

    List<GalaxyCollection> data = new ArrayList<>();
    int end = Math.min(effectiveOffset + effectiveLimit, total);

    for (int i = effectiveOffset; i < end; i++) {
      CollectionEntry entry = allEntries.get(i);
      GalaxyCollection gc = new GalaxyCollection();
      gc.setNamespace(entry.namespace);
      gc.setName(entry.name);
      gc.setDeprecated(false);

      String collectionPath = API_PREFIX + "/collections/index/" + entry.namespace + "/" + entry.name + "/";
      gc.setHref(baseUrl + collectionPath);
      gc.setVersionsUrl(baseUrl + collectionPath + "versions/");

      String highest = entry.getHighestVersion();
      if (highest != null) {
        String versionHref = baseUrl + collectionPath + "versions/" + highest + "/";
        gc.setHighestVersion(new GalaxyCollectionVersion(highest, versionHref));
      }

      data.add(gc);
    }

    String requestPath = API_PREFIX + "/collections/index/";
    GalaxyPaginationLinks links = buildLinks(baseUrl, requestPath, total, effectiveOffset, effectiveLimit);
    GalaxyPaginatedResponse<GalaxyCollection> response = new GalaxyPaginatedResponse<>(
        new GalaxyPaginationMeta(total), links, data);

    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
  }

  /**
   * Builds a single collection detail response.
   */
  public String buildCollectionDetail(final String baseUrl,
                                      final String namespace,
                                      final String name,
                                      final Iterable<FluentComponent> components) throws JsonProcessingException {
    Set<String> versions = new LinkedHashSet<>();
    for (FluentComponent component : components) {
      if (namespace.equals(component.namespace()) && name.equals(component.name())) {
        versions.add(component.version());
      }
    }

    String collectionPath = API_PREFIX + "/collections/index/" + namespace + "/" + name + "/";
    GalaxyCollection gc = new GalaxyCollection();
    gc.setNamespace(namespace);
    gc.setName(name);
    gc.setDeprecated(false);
    gc.setHref(baseUrl + collectionPath);
    gc.setVersionsUrl(baseUrl + collectionPath + "versions/");

    String highest = highestSemver(versions);
    if (highest != null) {
      String versionHref = baseUrl + collectionPath + "versions/" + highest + "/";
      gc.setHighestVersion(new GalaxyCollectionVersion(highest, versionHref));
    }

    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(gc);
  }

  /**
   * Builds a paginated version list for a specific collection.
   */
  public String buildVersionList(final String baseUrl,
                                 final String namespace,
                                 final String name,
                                 final Iterable<FluentComponent> components,
                                 final int offset,
                                 final int limit) throws JsonProcessingException {
    List<String> versions = new ArrayList<>();
    for (FluentComponent component : components) {
      if (namespace.equals(component.namespace()) && name.equals(component.name())) {
        versions.add(component.version());
      }
    }

    int total = versions.size();
    int effectiveLimit = limit > 0 ? limit : DEFAULT_PAGE_SIZE;
    int effectiveOffset = Math.max(0, Math.min(offset, total));

    String collectionPath = API_PREFIX + "/collections/index/" + namespace + "/" + name + "/";

    List<GalaxyCollectionVersion> data = new ArrayList<>();
    int end = Math.min(effectiveOffset + effectiveLimit, total);

    for (int i = effectiveOffset; i < end; i++) {
      String ver = versions.get(i);
      String versionHref = baseUrl + collectionPath + "versions/" + ver + "/";
      data.add(new GalaxyCollectionVersion(ver, versionHref));
    }

    String requestPath = collectionPath + "versions/";
    GalaxyPaginationLinks links = buildLinks(baseUrl, requestPath, total, effectiveOffset, effectiveLimit);
    GalaxyPaginatedResponse<GalaxyCollectionVersion> response = new GalaxyPaginatedResponse<>(
        new GalaxyPaginationMeta(total), links, data);

    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
  }

  /**
   * Builds a version detail response with download_url and artifact info.
   */
  public String buildVersionDetail(final String baseUrl,
                                   final String namespace,
                                   final String name,
                                   final String version,
                                   final FluentAsset asset) throws JsonProcessingException {
    String collectionPath = API_PREFIX + "/collections/index/" + namespace + "/" + name + "/";
    String versionPath = collectionPath + "versions/" + version + "/";
    String filename = namespace + "-" + name + "-" + version + ".tar.gz";
    String downloadPath = API_PREFIX + "/collections/artifacts/" + filename;

    GalaxyCollectionVersionDetail detail = new GalaxyCollectionVersionDetail();
    detail.setHref(baseUrl + versionPath);
    detail.setNamespace(namespace);
    detail.setName(name);
    detail.setVersion(version);
    detail.setDownloadUrl(baseUrl + downloadPath);

    String sha256 = extractSha256(asset);
    long size = extractSize(asset);
    detail.setArtifact(new GalaxyArtifact(filename, sha256, size));

    detail.setCollection(new GalaxyCollectionRef(
        baseUrl + collectionPath, namespace, name));

    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(detail);
  }

  public String toJson(final Object obj) throws JsonProcessingException {
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
  }

  private GalaxyPaginationLinks buildLinks(final String baseUrl,
                                           final String requestPath,
                                           final int total,
                                           final int offset,
                                           final int limit) {
    GalaxyPaginationLinks links = new GalaxyPaginationLinks();

    String base = baseUrl + requestPath;

    links.setFirst(base + "?offset=0&limit=" + limit);

    int lastOffset = Math.max(0, ((total - 1) / limit) * limit);
    links.setLast(base + "?offset=" + lastOffset + "&limit=" + limit);

    if (offset > 0) {
      int prevOffset = Math.max(0, offset - limit);
      links.setPrevious(base + "?offset=" + prevOffset + "&limit=" + limit);
    }

    if (offset + limit < total) {
      links.setNext(base + "?offset=" + (offset + limit) + "&limit=" + limit);
    }

    return links;
  }

  private String extractSha256(final FluentAsset asset) {
    return asset.blob()
        .map(blob -> {
          Map<String, String> checksums = blob.checksums();
          String sha = checksums.get("sha256");
          if (sha == null) {
            sha = checksums.get("SHA256");
          }
          return sha;
        })
        .orElse(null);
  }

  private long extractSize(final FluentAsset asset) {
    return asset.blob()
        .map(blob -> blob.blobSize())
        .orElse(0L);
  }

  static String highestSemver(final Set<String> versions) {
    String highest = null;
    int[] highestParts = null;

    for (String v : versions) {
      int[] parts = parseSemver(v);
      if (parts == null) {
        continue;
      }
      if (highestParts == null || compareSemver(parts, highestParts) > 0) {
        highest = v;
        highestParts = parts;
      }
    }
    return highest;
  }

  private static int[] parseSemver(final String version) {
    String[] parts = version.split("\\.");
    if (parts.length != 3) {
      return null;
    }
    try {
      return new int[]{
          Integer.parseInt(parts[0]),
          Integer.parseInt(parts[1]),
          Integer.parseInt(parts[2].replaceAll("[^0-9].*", ""))
      };
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  private static int compareSemver(final int[] a, final int[] b) {
    for (int i = 0; i < 3; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }
    return 0;
  }

  private static class CollectionEntry {
    final String namespace;
    final String name;
    final Set<String> versions = new LinkedHashSet<>();

    CollectionEntry(final String namespace, final String name) {
      this.namespace = namespace;
      this.name = name;
    }

    void addVersion(final String version) {
      versions.add(version);
    }

    String getHighestVersion() {
      return highestSemver(versions);
    }
  }
}
