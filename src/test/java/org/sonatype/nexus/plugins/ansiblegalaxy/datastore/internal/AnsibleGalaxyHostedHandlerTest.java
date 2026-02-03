package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.GalaxyResponseBuilder;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.repository.http.HttpStatus.*;

public class AnsibleGalaxyHostedHandlerTest
    extends TestSupport
{
  @Mock private Context context;
  @Mock private Request request;
  @Mock private Repository repository;
  @Mock private AnsibleGalaxyContentFacet contentFacet;
  @Mock private Content content;
  @Mock private Payload payload;
  @Mock private TokenMatcher.State tokenState;
  @Mock private FluentAsset asset;
  @Mock private AssetBlob blob;
  @Mock private FluentComponent component;

  private GalaxyResponseBuilder responseBuilder;
  private AnsibleGalaxyHostedHandler underTest;

  @Before
  public void setUp() {
    responseBuilder = new GalaxyResponseBuilder();
    underTest = new AnsibleGalaxyHostedHandler(responseBuilder);

    when(context.getRequest()).thenReturn(request);
    when(context.getRepository()).thenReturn(repository);
    when(repository.facet(AnsibleGalaxyContentFacet.class)).thenReturn(contentFacet);
    when(repository.getUrl()).thenReturn("http://nexus/repository/ansible-galaxy-test");
    when(context.getAttributes()).thenReturn(new org.sonatype.nexus.common.collect.AttributesMap());
    context.getAttributes().set(TokenMatcher.State.class, tokenState);
  }

  private void setTokens(Map<String, String> tokens) {
    when(tokenState.getTokens()).thenReturn(tokens);
  }

  // -- POST upload tests --

  @Test
  public void postUploadReturns201OnSuccess() throws Exception {
    setTokens(Collections.emptyMap());
    when(request.getAction()).thenReturn("POST");
    when(request.getPayload()).thenReturn(payload);
    when(contentFacet.putCollection(payload)).thenReturn(asset);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(CREATED));
    verify(contentFacet).putCollection(payload);
  }

  @Test
  public void postUploadReturns400WhenNoPayload() throws Exception {
    setTokens(Collections.emptyMap());
    when(request.getAction()).thenReturn("POST");
    when(request.getPayload()).thenReturn(null);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(BAD_REQUEST));
  }

  // -- GET download tests --

  @Test
  public void getDownloadReturns200WhenFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("filename", "testns-testcol-1.0.0.tar.gz");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/collections/artifacts/testns-testcol-1.0.0.tar.gz"))
        .thenReturn(Optional.of(content));

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  @Test
  public void getDownloadReturns404WhenNotFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("filename", "testns-testcol-9.9.9.tar.gz");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/collections/artifacts/testns-testcol-9.9.9.tar.gz"))
        .thenReturn(Optional.empty());

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  // -- GET collection list --

  @Test
  public void getCollectionListReturns200() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.browseComponents()).thenReturn(Collections.emptyList());

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- GET collection detail --

  @Test
  public void getCollectionDetailReturns404WhenNotFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "noexist");
    tokens.put("name", "noexist");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.browseComponents()).thenReturn(Collections.emptyList());

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  @Test
  public void getCollectionDetailReturns200WhenFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "testns");
    tokens.put("name", "testcol");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(component.namespace()).thenReturn("testns");
    when(component.name()).thenReturn("testcol");
    when(component.version()).thenReturn("1.0.0");
    when(contentFacet.browseComponents()).thenReturn(Arrays.asList(component));

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- GET version detail --

  @Test
  public void getVersionDetailReturns404WhenNotFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "testns");
    tokens.put("name", "testcol");
    tokens.put("version", "9.9.9");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/collections/artifacts/testns-testcol-9.9.9.tar.gz"))
        .thenReturn(Optional.empty());

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  @Test
  public void getVersionDetailReturns200WhenFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "testns");
    tokens.put("name", "testcol");
    tokens.put("version", "1.0.0");
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/collections/artifacts/testns-testcol-1.0.0.tar.gz"))
        .thenReturn(Optional.of(content));

    when(asset.path()).thenReturn("/collections/artifacts/testns-testcol-1.0.0.tar.gz");
    when(asset.blob()).thenReturn(Optional.of(blob));
    when(blob.checksums()).thenReturn(Collections.singletonMap("sha256", "abc123"));
    when(blob.blobSize()).thenReturn(12345L);
    when(contentFacet.browseAssets()).thenReturn(Arrays.asList(asset));

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  // -- DELETE tests --

  @Test
  public void deleteReturns204OnSuccess() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "testns");
    tokens.put("name", "testcol");
    tokens.put("version", "1.0.0");
    setTokens(tokens);
    when(request.getAction()).thenReturn("DELETE");
    when(contentFacet.delete("/collections/artifacts/testns-testcol-1.0.0.tar.gz")).thenReturn(true);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NO_CONTENT));
  }

  @Test
  public void deleteReturns404WhenNotFound() throws Exception {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("namespace", "testns");
    tokens.put("name", "testcol");
    tokens.put("version", "9.9.9");
    setTokens(tokens);
    when(request.getAction()).thenReturn("DELETE");
    when(contentFacet.delete("/collections/artifacts/testns-testcol-9.9.9.tar.gz")).thenReturn(false);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  // -- Unsupported method --

  @Test
  public void unsupportedMethodReturns405() throws Exception {
    setTokens(Collections.emptyMap());
    when(request.getAction()).thenReturn("PATCH");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(METHOD_NOT_ALLOWED));
  }
}
