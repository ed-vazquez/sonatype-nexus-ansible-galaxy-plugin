package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.GalaxyResponseBuilder;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.StreamPayload;
import org.sonatype.nexus.repository.view.payloads.StringPayload;

import static org.sonatype.nexus.repository.http.HttpMethods.*;

/**
 * Handler for Galaxy v3 API endpoints in a hosted repository.
 *
 * Routes:
 * 1. POST   /api/v3/artifacts/collections/                                    - Upload collection
 * 2. GET    /api/v3/plugin/ansible/content/published/collections/index/        - List collections
 * 3. GET    /api/v3/.../collections/index/{ns}/{name}/                         - Collection detail
 * 4. GET    /api/v3/.../collections/index/{ns}/{name}/versions/                - List versions
 * 5. GET    /api/v3/.../collections/index/{ns}/{name}/versions/{version}/      - Version detail
 * 6. GET    /api/v3/.../collections/artifacts/{filename}                       - Download artifact
 * 7. DELETE /api/v3/.../collections/index/{ns}/{name}/versions/{version}/      - Delete version
 */
@Named
@Singleton
public class AnsibleGalaxyHostedHandler
    extends ComponentSupport
    implements Handler
{
  private final GalaxyResponseBuilder responseBuilder;

  @Inject
  public AnsibleGalaxyHostedHandler(final GalaxyResponseBuilder responseBuilder) {
    this.responseBuilder = responseBuilder;
  }

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String method = context.getRequest().getAction();
    Map<String, String> tokens = context.getAttributes().require(TokenMatcher.State.class).getTokens();

    AnsibleGalaxyContentFacet contentFacet = context.getRepository()
        .facet(AnsibleGalaxyContentFacet.class);

    switch (method) {
      case GET:
      case HEAD:
        return handleGet(context, contentFacet, tokens);
      case POST:
        return handleUpload(context, contentFacet);
      case DELETE:
        return handleDelete(contentFacet, tokens);
      default:
        return HttpResponses.methodNotAllowed(method, GET, POST, DELETE);
    }
  }

  private Response handleGet(final Context context,
                             final AnsibleGalaxyContentFacet contentFacet,
                             final Map<String, String> tokens) throws Exception {
    // Route 6: Download artifact - has "filename" token
    if (tokens.containsKey("filename")) {
      return handleDownload(contentFacet, tokens);
    }

    String baseUrl = context.getRepository().getUrl();
    String namespace = tokens.get("namespace");
    String name = tokens.get("name");
    String version = tokens.get("version");

    int offset = parseIntParam(tokens.get("offset"), 0);
    int limit = parseIntParam(tokens.get("limit"), 0);

    // Route 5: Version detail - has namespace, name, and version
    if (namespace != null && name != null && version != null) {
      return handleVersionDetail(baseUrl, contentFacet, namespace, name, version);
    }

    // Route 4: Version list - has namespace and name (versions route)
    if (namespace != null && name != null && tokens.containsKey("version_marker")) {
      return handleVersionList(baseUrl, contentFacet, namespace, name, offset, limit);
    }

    // Route 3: Collection detail - has namespace and name
    if (namespace != null && name != null) {
      return handleCollectionDetail(baseUrl, contentFacet, namespace, name);
    }

    // Route 2: Collection list
    return handleCollectionList(baseUrl, contentFacet, offset, limit);
  }

  /**
   * Route 1: POST /api/v3/artifacts/collections/ - Upload collection tar.gz
   *
   * Accepts both raw binary uploads (Content-Type: application/gzip) and
   * multipart/form-data uploads (as sent by ansible-galaxy collection publish).
   */
  private Response handleUpload(final Context context,
                                final AnsibleGalaxyContentFacet contentFacet) throws IOException {
    Payload payload = context.getRequest().getPayload();
    if (payload == null) {
      return HttpResponses.badRequest("Request body is required");
    }

    String contentType = payload.getContentType();
    if (contentType != null && contentType.startsWith("multipart/form-data")) {
      payload = extractFileFromMultipart(payload);
    }

    contentFacet.putCollection(payload);
    return HttpResponses.created();
  }

  /**
   * Extracts the file part from a multipart/form-data request body.
   */
  private Payload extractFileFromMultipart(final Payload payload) throws IOException {
    String contentType = payload.getContentType();
    String boundary = null;
    for (String part : contentType.split(";")) {
      part = part.trim();
      if (part.startsWith("boundary=")) {
        boundary = part.substring("boundary=".length());
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
          boundary = boundary.substring(1, boundary.length() - 1);
        }
        break;
      }
    }
    if (boundary == null) {
      throw new IOException("No boundary found in multipart Content-Type");
    }

    byte[] body;
    try (InputStream is = payload.openInputStream();
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = is.read(buffer)) != -1) {
        baos.write(buffer, 0, read);
      }
      body = baos.toByteArray();
    }
    byte[] headerEnd = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
    byte[] closingBoundary = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

    // Find end of the first part's headers
    int headersEnd = indexOf(body, headerEnd, 0);
    if (headersEnd < 0) {
      throw new IOException("Could not find end of multipart part headers");
    }
    int contentStart = headersEnd + headerEnd.length;

    // Find the closing boundary after the content
    int contentEnd = indexOf(body, closingBoundary, contentStart);
    if (contentEnd < 0) {
      throw new IOException("Could not find closing multipart boundary");
    }

    byte[] fileContent = Arrays.copyOfRange(body, contentStart, contentEnd);
    return new StreamPayload(
        () -> new ByteArrayInputStream(fileContent),
        fileContent.length,
        "application/gzip");
  }

  private static int indexOf(final byte[] haystack, final byte[] needle, final int start) {
    for (int i = start; i <= haystack.length - needle.length; i++) {
      boolean match = true;
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Route 2: GET collection list (paginated)
   */
  private Response handleCollectionList(final String baseUrl,
                                        final AnsibleGalaxyContentFacet contentFacet,
                                        final int offset,
                                        final int limit) throws Exception {
    Iterable<FluentComponent> components = contentFacet.browseComponents();
    String json = responseBuilder.buildCollectionList(baseUrl, components, offset, limit);
    return HttpResponses.ok(new Content(new StringPayload(json, "application/json")));
  }

  /**
   * Route 3: GET collection detail
   */
  private Response handleCollectionDetail(final String baseUrl,
                                          final AnsibleGalaxyContentFacet contentFacet,
                                          final String namespace,
                                          final String name) throws Exception {
    Iterable<FluentComponent> components = contentFacet.browseComponents();

    boolean found = false;
    for (FluentComponent c : components) {
      if (namespace.equals(c.namespace()) && name.equals(c.name())) {
        found = true;
        break;
      }
    }

    if (!found) {
      return HttpResponses.notFound();
    }

    components = contentFacet.browseComponents();
    String json = responseBuilder.buildCollectionDetail(baseUrl, namespace, name, components);
    return HttpResponses.ok(new Content(new StringPayload(json, "application/json")));
  }

  /**
   * Route 4: GET version list (paginated)
   */
  private Response handleVersionList(final String baseUrl,
                                     final AnsibleGalaxyContentFacet contentFacet,
                                     final String namespace,
                                     final String name,
                                     final int offset,
                                     final int limit) throws Exception {
    Iterable<FluentComponent> components = contentFacet.browseComponents();
    String json = responseBuilder.buildVersionList(baseUrl, namespace, name, components, offset, limit);
    return HttpResponses.ok(new Content(new StringPayload(json, "application/json")));
  }

  /**
   * Route 5: GET version detail with download_url
   */
  private Response handleVersionDetail(final String baseUrl,
                                       final AnsibleGalaxyContentFacet contentFacet,
                                       final String namespace,
                                       final String name,
                                       final String version) throws Exception {
    String assetPath = AnsibleGalaxyContentFacetImpl.buildAssetPath(namespace, name, version);
    Optional<Content> content = contentFacet.get(assetPath);
    if (!content.isPresent()) {
      return HttpResponses.notFound();
    }

    // Find the asset for artifact metadata
    for (FluentAsset asset : contentFacet.browseAssets()) {
      if (assetPath.equals(asset.path())) {
        String json = responseBuilder.buildVersionDetail(baseUrl, namespace, name, version, asset);
        return HttpResponses.ok(new Content(new StringPayload(json, "application/json")));
      }
    }

    return HttpResponses.notFound();
  }

  /**
   * Route 6: GET artifact download
   */
  private Response handleDownload(final AnsibleGalaxyContentFacet contentFacet,
                                  final Map<String, String> tokens) {
    String filename = tokens.get("filename");
    String path = "/collections/artifacts/" + filename;
    Optional<Content> content = contentFacet.get(path);
    return content.map(HttpResponses::ok).orElseGet(HttpResponses::notFound);
  }

  /**
   * Route 7: DELETE version
   */
  private Response handleDelete(final AnsibleGalaxyContentFacet contentFacet,
                                final Map<String, String> tokens) {
    String namespace = tokens.get("namespace");
    String name = tokens.get("name");
    String version = tokens.get("version");

    String path = AnsibleGalaxyContentFacetImpl.buildAssetPath(namespace, name, version);
    boolean deleted = contentFacet.delete(path);
    return deleted ? HttpResponses.noContent() : HttpResponses.notFound();
  }

  private static int parseIntParam(final String value, final int defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
