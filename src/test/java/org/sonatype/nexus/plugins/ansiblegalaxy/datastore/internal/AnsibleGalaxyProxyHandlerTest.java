package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.GalaxyUpstreamClient;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.httpclient.HttpClientFacet;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.common.collect.NestedAttributesMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.repository.http.HttpStatus.*;

public class AnsibleGalaxyProxyHandlerTest
    extends TestSupport
{
  @Mock private Context context;
  @Mock private Request request;
  @Mock private Repository repository;
  @Mock private AnsibleGalaxyContentFacet contentFacet;
  @Mock private HttpClientFacet httpClientFacet;
  @Mock private GalaxyUpstreamClient upstreamClient;
  @Mock private Content content;
  @Mock private TokenMatcher.State tokenState;

  private AnsibleGalaxyProxyHandler underTest;

  @Before
  public void setUp() {
    underTest = new AnsibleGalaxyProxyHandler(upstreamClient);

    when(context.getRequest()).thenReturn(request);
    when(context.getRepository()).thenReturn(repository);
    when(repository.facet(AnsibleGalaxyContentFacet.class)).thenReturn(contentFacet);
    when(repository.facet(HttpClientFacet.class)).thenReturn(httpClientFacet);
    when(repository.getUrl()).thenReturn("http://nexus/repository/galaxy-proxy");

    // Mock configuration with proxy.remoteUrl
    org.sonatype.nexus.repository.config.Configuration config =
        org.mockito.Mockito.mock(org.sonatype.nexus.repository.config.Configuration.class);
    NestedAttributesMap proxyAttrs = org.mockito.Mockito.mock(NestedAttributesMap.class);
    when(config.attributes("proxy")).thenReturn(proxyAttrs);
    when(proxyAttrs.require("remoteUrl", String.class)).thenReturn("https://galaxy.ansible.com");
    when(repository.getConfiguration()).thenReturn(config);

    AttributesMap attributes = new AttributesMap();
    attributes.set(TokenMatcher.State.class, tokenState);
    when(context.getAttributes()).thenReturn(attributes);
    when(request.getPath()).thenReturn("/api/v3/collections/");
  }

  private void setTokens(Map<String, String> tokens) {
    when(tokenState.getTokens()).thenReturn(tokens);
  }

  // -- Unsupported method --

  @Test
  public void nonGetMethodReturns405() throws Exception {
    when(request.getAction()).thenReturn("POST");
    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(METHOD_NOT_ALLOWED));
  }

  // -- API root --

  @Test
  public void apiRootReturnsStaticJson() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("api_root", "");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- Artifact download: cache hit --

  @Test
  public void artifactDownloadReturnsCachedContent() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("filename", "community-general-5.0.0.tar.gz");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/collections/artifacts/community-general-5.0.0.tar.gz"))
        .thenReturn(Optional.of(content));

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
    verify(upstreamClient, never()).fetchArtifact(any(), anyString(), anyString());
  }

  // -- Artifact download: cache miss, upstream found --

  @Test
  public void artifactDownloadFetchesFromUpstreamOnCacheMiss() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("filename", "community-general-5.0.0.tar.gz");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");

    // Cache miss
    when(contentFacet.get("/collections/artifacts/community-general-5.0.0.tar.gz"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(content));
    when(upstreamClient.fetchArtifact(httpClientFacet, "https://galaxy.ansible.com",
        "community-general-5.0.0.tar.gz")).thenReturn(content);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
    verify(contentFacet).putCollection(eq("/collections/artifacts/community-general-5.0.0.tar.gz"),
        eq(content), eq("community"), eq("general"), eq("5.0.0"));
  }

  // -- Artifact download: cache miss, upstream not found --

  @Test
  public void artifactDownloadReturns404WhenUpstreamNotFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("filename", "noexist-noexist-1.0.0.tar.gz");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/collections/artifacts/noexist-noexist-1.0.0.tar.gz"))
        .thenReturn(Optional.empty());
    when(upstreamClient.fetchArtifact(httpClientFacet, "https://galaxy.ansible.com",
        "noexist-noexist-1.0.0.tar.gz")).thenReturn(null);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  // -- Version detail --

  @Test
  public void versionDetailProxiesToUpstream() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "community");
    tokens.put("name", "general");
    tokens.put("version", "5.0.0");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(upstreamClient.fetchVersionDetail(httpClientFacet, "https://galaxy.ansible.com",
        "http://nexus/repository/galaxy-proxy", "community", "general", "5.0.0"))
        .thenReturn("{\"version\": \"5.0.0\"}");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- Version list --

  @Test
  public void versionListProxiesToUpstream() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "community");
    tokens.put("name", "general");
    tokens.put("version_marker", "versions");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(upstreamClient.fetchVersionList(eq(httpClientFacet), eq("https://galaxy.ansible.com"),
        eq("http://nexus/repository/galaxy-proxy"), eq("community"), eq("general"), any()))
        .thenReturn("{\"data\": []}");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- Collection detail --

  @Test
  public void collectionDetailProxiesToUpstream() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "community");
    tokens.put("name", "general");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(upstreamClient.fetchCollectionDetail(httpClientFacet, "https://galaxy.ansible.com",
        "http://nexus/repository/galaxy-proxy", "community", "general"))
        .thenReturn("{\"name\": \"general\"}");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- Collection list --

  @Test
  public void collectionListProxiesToUpstream() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(upstreamClient.fetchCollectionList(eq(httpClientFacet), eq("https://galaxy.ansible.com"),
        eq("http://nexus/repository/galaxy-proxy"), any()))
        .thenReturn("{\"data\": []}");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- parseFilename tests --

  @Test
  public void parseFilenameStandard() {
    String[] parts = AnsibleGalaxyProxyHandler.parseFilename("community-general-5.0.0.tar.gz");
    assertThat(parts[0], is("community"));
    assertThat(parts[1], is("general"));
    assertThat(parts[2], is("5.0.0"));
  }

  @Test
  public void parseFilenameWithDashInName() {
    String[] parts = AnsibleGalaxyProxyHandler.parseFilename("ansible-my_collection-1.2.3.tar.gz");
    assertThat(parts[0], is("ansible"));
    assertThat(parts[1], is("my_collection"));
    assertThat(parts[2], is("1.2.3"));
  }

  @Test
  public void parseFilenameReturnsNullForInvalid() {
    assertThat(AnsibleGalaxyProxyHandler.parseFilename(null) == null, is(true));
    assertThat(AnsibleGalaxyProxyHandler.parseFilename("notarball.zip") == null, is(true));
    assertThat(AnsibleGalaxyProxyHandler.parseFilename("onlyname.tar.gz") == null, is(true));
  }
}
