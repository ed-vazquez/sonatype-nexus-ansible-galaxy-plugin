package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.plugins.ansiblegalaxy.model.CollectionInfo;
import org.sonatype.nexus.repository.Facet.Exposed;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import static java.util.Arrays.asList;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;

/**
 * Ansible Galaxy content facet implementation that stores collection tar.gz files,
 * extracting MANIFEST.json to determine namespace/name/version.
 */
@Exposed
@Named(AnsibleGalaxyFormat.NAME)
public class AnsibleGalaxyContentFacetImpl extends ContentFacetSupport
    implements org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String ASSET_KIND = "collection-tarball";

  @Inject
  public AnsibleGalaxyContentFacetImpl(
      @Named(AnsibleGalaxyFormat.NAME) final FormatStoreManager formatStoreManager) {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public FluentAsset putCollection(final Payload payload) throws IOException {
    try (TempBlob tempBlob = blobs().ingest(payload, asList(SHA256))) {
      CollectionInfo info = extractCollectionInfo(tempBlob);
      if (info == null || info.getNamespace() == null || info.getName() == null || info.getVersion() == null) {
        throw new IOException("Unable to extract collection metadata from MANIFEST.json");
      }

      String path = buildAssetPath(info.getNamespace(), info.getName(), info.getVersion());

      FluentComponent component = components()
          .name(info.getName())
          .namespace(info.getNamespace())
          .version(info.getVersion())
          .getOrCreate();

      return assets()
          .path(path)
          .kind(ASSET_KIND)
          .component(component)
          .blob(tempBlob)
          .save();
    }
  }

  @Override
  public FluentAsset putCollection(final String path, final Payload payload,
                                   final String namespace, final String name,
                                   final String version) throws IOException {
    try (TempBlob tempBlob = blobs().ingest(payload, asList(SHA256))) {
      FluentComponent component = components()
          .name(name)
          .namespace(namespace)
          .version(version)
          .getOrCreate();

      return assets()
          .path(path)
          .kind(ASSET_KIND)
          .component(component)
          .blob(tempBlob)
          .save();
    }
  }

  @Override
  public boolean delete(final String path) {
    return assets().path(path).find()
        .map(asset -> {
          asset.delete();
          return true;
        })
        .orElse(false);
  }

  @Override
  public Iterable<FluentAsset> browseAssets() {
    return assets().browse(Integer.MAX_VALUE, null);
  }

  @Override
  public Iterable<FluentComponent> browseComponents() {
    return components().browse(Integer.MAX_VALUE, null);
  }

  /**
   * Extracts CollectionInfo from the MANIFEST.json file inside the collection tar.gz.
   */
  private CollectionInfo extractCollectionInfo(final TempBlob tempBlob) throws IOException {
    try (InputStream is = tempBlob.get();
         BufferedInputStream bis = new BufferedInputStream(is);
         GzipCompressorInputStream gzis = new GzipCompressorInputStream(bis);
         TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

      TarArchiveEntry entry;
      while ((entry = tais.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        // MANIFEST.json is typically at {namespace}-{name}-{version}/MANIFEST.json
        String entryName = entry.getName();
        if (entryName.endsWith("/MANIFEST.json") || entryName.equals("MANIFEST.json")) {
          JsonNode root = OBJECT_MAPPER.readTree(tais);
          JsonNode collectionInfo = root.get("collection_info");
          if (collectionInfo != null) {
            return OBJECT_MAPPER.treeToValue(collectionInfo, CollectionInfo.class);
          }
        }
      }
    }
    return null;
  }

  static String buildAssetPath(final String namespace, final String name, final String version) {
    return String.format("/collections/artifacts/%s-%s-%s.tar.gz", namespace, name, version);
  }
}
