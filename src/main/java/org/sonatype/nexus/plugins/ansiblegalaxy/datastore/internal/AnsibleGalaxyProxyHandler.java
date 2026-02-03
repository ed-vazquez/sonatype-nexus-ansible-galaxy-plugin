package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.GalaxyUpstreamClient;
import org.sonatype.nexus.repository.httpclient.HttpClientFacet;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.StringPayload;

import static org.sonatype.nexus.repository.http.HttpMethods.*;

/**
 * Handler for Galaxy v3 API endpoints in a proxy repository.
 *
 * Dispatches by token presence:
 * - "filename" token        -> artifact download (cache permanently as component+asset)
 * - "version" token         -> version detail (fetch upstream, rewrite, return JSON)
 * - "version_marker" token  -> version list (fetch upstream, rewrite)
 * - "namespace"+"name"      -> collection detail (fetch upstream, rewrite)
 * - no tokens               -> collection list (fetch upstream, rewrite)
 * - path is /api/           -> return static API root JSON
 *
 * For artifact downloads: check local cache first via ContentFacet.get(). On miss,
 * fetch from upstream, store with ContentFacet.putCollection(), return content.
 *
 * For metadata endpoints: always fetch from upstream (metadata can change), rewrite URLs.
 */
@Named
@Singleton
public class AnsibleGalaxyProxyHandler
    extends ComponentSupport
    implements Handler
{
  static final String API_ROOT_JSON =
      "{\"available_versions\":{\"v3\":\"v3/\"},\"current_version\":\"v3\"}";

  private final GalaxyUpstreamClient upstreamClient;

  @Inject
  public AnsibleGalaxyProxyHandler(final GalaxyUpstreamClient upstreamClient) {
    this.upstreamClient = upstreamClient;
  }

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String method = context.getRequest().getAction();

    switch (method) {
      case GET:
      case HEAD:
        return handleGet(context);
      default:
        return HttpResponses.methodNotAllowed(method, GET);
    }
  }

  private Response handleGet(final Context context) throws Exception {
    Map<String, String> tokens = context.getAttributes()
        .require(TokenMatcher.State.class).getTokens();

    String repoUrl = context.getRepository().getUrl();
    String remoteUrl = context.getRepository().getConfiguration()
        .attributes("proxy").require("remoteUrl", String.class);
    HttpClientFacet httpClient = context.getRepository().facet(HttpClientFacet.class);
    AnsibleGalaxyContentFacet contentFacet = context.getRepository()
        .facet(AnsibleGalaxyContentFacet.class);

    // Check for API root discovery
    if (tokens.containsKey("api_root")) {
      return jsonResponse(API_ROOT_JSON);
    }

    // Route: artifact download (has "filename" token) — cached
    if (tokens.containsKey("filename")) {
      return handleArtifactDownload(contentFacet, httpClient, remoteUrl, tokens);
    }

    String queryString = extractQueryString(context);
    String namespace = tokens.get("namespace");
    String name = tokens.get("name");
    String version = tokens.get("version");

    // Route: version detail
    if (namespace != null && name != null && version != null) {
      String json = upstreamClient.fetchVersionDetail(httpClient, remoteUrl, repoUrl,
          namespace, name, version);
      return jsonResponse(json);
    }

    // Route: version list
    if (namespace != null && name != null && tokens.containsKey("version_marker")) {
      String json = upstreamClient.fetchVersionList(httpClient, remoteUrl, repoUrl,
          namespace, name, queryString);
      return jsonResponse(json);
    }

    // Route: collection detail
    if (namespace != null && name != null) {
      String json = upstreamClient.fetchCollectionDetail(httpClient, remoteUrl, repoUrl,
          namespace, name);
      return jsonResponse(json);
    }

    // Route: collection list
    String json = upstreamClient.fetchCollectionList(httpClient, remoteUrl, repoUrl, queryString);
    return jsonResponse(json);
  }

  /**
   * Handle artifact download: check cache first, on miss fetch from upstream and store.
   */
  private Response handleArtifactDownload(final AnsibleGalaxyContentFacet contentFacet,
                                          final HttpClientFacet httpClient,
                                          final String remoteUrl,
                                          final Map<String, String> tokens) throws IOException {
    String filename = tokens.get("filename");
    String path = "/collections/artifacts/" + filename;

    // Check local cache first
    Optional<Content> cached = contentFacet.get(path);
    if (cached.isPresent()) {
      log.debug("Serving cached artifact: {}", filename);
      return HttpResponses.ok(cached.get());
    }

    // Cache miss — fetch from upstream
    log.debug("Cache miss for artifact: {}, fetching from upstream", filename);
    Content upstream = upstreamClient.fetchArtifact(httpClient, remoteUrl, filename);
    if (upstream == null) {
      return HttpResponses.notFound();
    }

    // Extract namespace/name/version from filename: {ns}-{name}-{version}.tar.gz
    String[] parts = parseFilename(filename);
    if (parts != null) {
      contentFacet.putCollection(path, upstream, parts[0], parts[1], parts[2]);
      // Re-fetch from store to get proper Content with blob metadata
      Optional<Content> stored = contentFacet.get(path);
      if (stored.isPresent()) {
        return HttpResponses.ok(stored.get());
      }
    }

    // Fallback: return upstream content directly
    return HttpResponses.ok(upstream);
  }

  /**
   * Parse a Galaxy artifact filename into [namespace, name, version].
   * Expected format: {namespace}-{name}-{version}.tar.gz
   */
  static String[] parseFilename(final String filename) {
    if (filename == null || !filename.endsWith(".tar.gz")) {
      return null;
    }
    String base = filename.substring(0, filename.length() - ".tar.gz".length());
    // Split on '-' but namespace-name might contain dashes
    // Galaxy convention: first segment is namespace, second is name, last is version
    int firstDash = base.indexOf('-');
    if (firstDash < 0) {
      return null;
    }
    int lastDash = base.lastIndexOf('-');
    if (lastDash <= firstDash) {
      return null;
    }

    String namespace = base.substring(0, firstDash);
    String name = base.substring(firstDash + 1, lastDash);
    String version = base.substring(lastDash + 1);

    if (namespace.isEmpty() || name.isEmpty() || version.isEmpty()) {
      return null;
    }
    return new String[]{namespace, name, version};
  }

  private String extractQueryString(final Context context) {
    String url = context.getRequest().getPath();
    int qIdx = url.indexOf('?');
    return qIdx >= 0 ? url.substring(qIdx + 1) : null;
  }

  private Response jsonResponse(final String json) {
    return HttpResponses.ok(new Content(new StringPayload(json, "application/json")));
  }
}
