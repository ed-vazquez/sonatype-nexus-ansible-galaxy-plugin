package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.httpclient.HttpClientFacet;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.payloads.StreamPayload;

/**
 * Wraps HttpClientFacet to fetch upstream Galaxy API responses and rewrite URLs
 * so that download_url and href fields point back through the proxy repository.
 */
@Named
@Singleton
public class GalaxyUpstreamClient
    extends ComponentSupport
{
  /**
   * The path prefix used by galaxy.ansible.com for v3 API content.
   */
  static final String UPSTREAM_PREFIX = "/api/v3/plugin/ansible/content/published";

  /**
   * Fetch the version list from upstream, rewriting hrefs and pagination links.
   *
   * @param httpClient the HTTP client facet from the proxy repository
   * @param remoteUrl  the upstream base URL (e.g. https://galaxy.ansible.com)
   * @param repoUrl    the local proxy repository URL (e.g. http://nexus/repository/galaxy-proxy)
   * @param namespace  the collection namespace
   * @param name       the collection name
   * @param queryString the query string from the original request (may be null)
   * @return rewritten JSON response body
   */
  public String fetchVersionList(final HttpClientFacet httpClient,
                                 final String remoteUrl,
                                 final String repoUrl,
                                 final String namespace,
                                 final String name,
                                 final String queryString) throws IOException {
    String path = UPSTREAM_PREFIX + "/collections/index/" + namespace + "/" + name + "/versions/";
    String url = buildUpstreamUrl(remoteUrl, path);
    if (queryString != null && !queryString.isEmpty()) {
      url = url + "?" + queryString;
    }

    String json = fetchJson(httpClient, url);
    return rewriteUrls(json, extractBaseUrl(remoteUrl), repoUrl);
  }

  /**
   * Fetch version detail from upstream, rewriting download_url and hrefs.
   */
  public String fetchVersionDetail(final HttpClientFacet httpClient,
                                   final String remoteUrl,
                                   final String repoUrl,
                                   final String namespace,
                                   final String name,
                                   final String version) throws IOException {
    String path = UPSTREAM_PREFIX + "/collections/index/" + namespace + "/" + name + "/versions/" + version + "/";
    String url = buildUpstreamUrl(remoteUrl, path);

    String json = fetchJson(httpClient, url);
    return rewriteUrls(json, extractBaseUrl(remoteUrl), repoUrl);
  }

  /**
   * Fetch collection detail from upstream, rewriting hrefs.
   */
  public String fetchCollectionDetail(final HttpClientFacet httpClient,
                                      final String remoteUrl,
                                      final String repoUrl,
                                      final String namespace,
                                      final String name) throws IOException {
    String path = UPSTREAM_PREFIX + "/collections/index/" + namespace + "/" + name + "/";
    String url = buildUpstreamUrl(remoteUrl, path);

    String json = fetchJson(httpClient, url);
    return rewriteUrls(json, extractBaseUrl(remoteUrl), repoUrl);
  }

  /**
   * Fetch collection list from upstream, rewriting hrefs.
   */
  public String fetchCollectionList(final HttpClientFacet httpClient,
                                    final String remoteUrl,
                                    final String repoUrl,
                                    final String queryString) throws IOException {
    String path = UPSTREAM_PREFIX + "/collections/index/";
    String url = buildUpstreamUrl(remoteUrl, path);
    if (queryString != null && !queryString.isEmpty()) {
      url = url + "?" + queryString;
    }

    String json = fetchJson(httpClient, url);
    return rewriteUrls(json, extractBaseUrl(remoteUrl), repoUrl);
  }

  /**
   * Fetch raw artifact bytes from upstream (no rewriting needed).
   */
  public Content fetchArtifact(final HttpClientFacet httpClient,
                               final String remoteUrl,
                               final String filename) throws IOException {
    String path = UPSTREAM_PREFIX + "/collections/artifacts/" + filename;
    String url = buildUpstreamUrl(remoteUrl, path);

    HttpClient client = httpClient.getHttpClient();
    HttpGet request = new HttpGet(url);
    HttpResponse response = client.execute(request);

    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      EntityUtils.consumeQuietly(response.getEntity());
      return null;
    }

    HttpEntity entity = response.getEntity();
    if (entity == null) {
      return null;
    }

    long contentLength = entity.getContentLength();
    String contentType = entity.getContentType() != null
        ? entity.getContentType().getValue()
        : "application/gzip";

    InputStream inputStream = entity.getContent();
    return new Content(new StreamPayload(
        () -> inputStream,
        contentLength >= 0 ? contentLength : -1,
        contentType));
  }

  /**
   * Build the full upstream URL for a given local path.
   * Strips any trailing slash from remoteUrl before appending path.
   */
  String buildUpstreamUrl(final String remoteUrl, final String path) {
    String base = remoteUrl.endsWith("/")
        ? remoteUrl.substring(0, remoteUrl.length() - 1)
        : remoteUrl;
    return base + path;
  }

  /**
   * Rewrite all URLs in a JSON string from the upstream host to the local repo URL.
   * <p>
   * Handles:
   * - download_url fields pointing to upstream
   * - href fields
   * - versions_url fields
   * - pagination links (next, previous, first, last)
   * <p>
   * The upstream base URL is the scheme+host portion (e.g. https://galaxy.ansible.com).
   * All occurrences of upstreamBaseUrl + UPSTREAM_PREFIX are replaced with repoUrl + UPSTREAM_PREFIX.
   * This covers all URL patterns since they all share the same prefix.
   */
  String rewriteUrls(final String json, final String upstreamBaseUrl, final String repoUrl) {
    // Replace upstream base + prefix with local repo URL + prefix
    // This rewrites all absolute URLs in one pass
    String upstreamFull = upstreamBaseUrl + UPSTREAM_PREFIX;
    String localFull = repoUrl + UPSTREAM_PREFIX;
    return json.replace(upstreamFull, localFull);
  }

  /**
   * Extract the base URL (scheme + host + port) from a full URL.
   */
  private String extractBaseUrl(final String url) {
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      int port = uri.getPort();

      StringBuilder sb = new StringBuilder();
      sb.append(scheme).append("://").append(host);
      if (port > 0 && port != 80 && port != 443) {
        sb.append(':').append(port);
      }
      return sb.toString();
    }
    catch (Exception e) {
      // Fallback: strip path from URL
      int idx = url.indexOf("://");
      if (idx >= 0) {
        int pathStart = url.indexOf('/', idx + 3);
        if (pathStart >= 0) {
          return url.substring(0, pathStart);
        }
      }
      return url;
    }
  }

  private String fetchJson(final HttpClientFacet httpClient, final String url) throws IOException {
    HttpClient client = httpClient.getHttpClient();
    HttpGet request = new HttpGet(url);
    request.setHeader("Accept", "application/json");

    HttpResponse response = client.execute(request);
    int statusCode = response.getStatusLine().getStatusCode();
    HttpEntity entity = response.getEntity();

    if (statusCode != HttpStatus.SC_OK) {
      String body = entity != null ? EntityUtils.toString(entity) : "";
      throw new IOException("Upstream returned HTTP " + statusCode + " for " + url + ": " + body);
    }

    if (entity == null) {
      throw new IOException("Upstream returned empty response for " + url);
    }

    return EntityUtils.toString(entity);
  }
}
