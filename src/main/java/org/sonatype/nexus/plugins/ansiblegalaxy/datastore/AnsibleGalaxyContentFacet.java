package org.sonatype.nexus.plugins.ansiblegalaxy.datastore;

import java.io.IOException;
import java.util.Optional;

import org.sonatype.nexus.repository.Facet.Exposed;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

/**
 * Content facet for Ansible Galaxy hosted repositories.
 */
@Exposed
public interface AnsibleGalaxyContentFacet extends ContentFacet {

  /**
   * Retrieves content at the given path.
   */
  Optional<Content> get(String path);

  /**
   * Stores a collection tar.gz, extracting MANIFEST.json to determine namespace/name/version.
   * Returns the stored asset.
   */
  FluentAsset putCollection(Payload payload) throws IOException;

  /**
   * Stores a collection tar.gz with explicit metadata (used when namespace/name/version are already known).
   */
  FluentAsset putCollection(String path, Payload payload,
                            String namespace, String name, String version) throws IOException;

  /**
   * Deletes the asset at the given path.
   */
  boolean delete(String path);

  /**
   * Returns all collection assets in the repository.
   */
  Iterable<FluentAsset> browseAssets();

  /**
   * Returns all components in the repository.
   */
  Iterable<FluentComponent> browseComponents();
}
