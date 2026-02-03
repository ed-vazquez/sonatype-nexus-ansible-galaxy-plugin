package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

public class GalaxyResponseBuilderTest
    extends TestSupport
{
  private static final String BASE_URL = "http://nexus:8081/repository/ansible-galaxy-test";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private FluentComponent component1;
  @Mock private FluentComponent component2;
  @Mock private FluentComponent component3;

  @Mock private FluentAsset asset1;
  @Mock private AssetBlob blob1;

  private GalaxyResponseBuilder underTest;

  @Before
  public void setUp() {
    underTest = new GalaxyResponseBuilder();

    when(component1.namespace()).thenReturn("testns");
    when(component1.name()).thenReturn("testcol");
    when(component1.version()).thenReturn("1.0.0");

    when(component2.namespace()).thenReturn("testns");
    when(component2.name()).thenReturn("testcol");
    when(component2.version()).thenReturn("2.0.0");

    when(component3.namespace()).thenReturn("otherns");
    when(component3.name()).thenReturn("othercol");
    when(component3.version()).thenReturn("1.0.0");

    when(asset1.path()).thenReturn("/collections/artifacts/testns-testcol-1.0.0.tar.gz");
    when(asset1.blob()).thenReturn(Optional.of(blob1));
    when(blob1.checksums()).thenReturn(Collections.singletonMap("sha256", "abc123def456"));
    when(blob1.blobSize()).thenReturn(12345L);
  }

  @Test
  public void buildCollectionListGroupsByNamespaceAndName() throws Exception {
    String json = underTest.buildCollectionList(BASE_URL,
        Arrays.asList(component1, component2, component3), 0, 100);

    JsonNode root = MAPPER.readTree(json);
    assertThat(root.get("meta").get("count").asInt(), is(2));
    assertThat(root.get("data").size(), is(2));

    JsonNode first = root.get("data").get(0);
    assertThat(first.get("namespace").asText(), is("testns"));
    assertThat(first.get("name").asText(), is("testcol"));
    assertThat(first.get("highest_version").get("version").asText(), is("2.0.0"));
  }

  @Test
  public void buildCollectionListSupportsPagination() throws Exception {
    String json = underTest.buildCollectionList(BASE_URL,
        Arrays.asList(component1, component2, component3), 0, 1);

    JsonNode root = MAPPER.readTree(json);
    assertThat(root.get("meta").get("count").asInt(), is(2));
    assertThat(root.get("data").size(), is(1));
    assertThat(root.get("links").get("next"), is(notNullValue()));
  }

  @Test
  public void buildCollectionListReturnsEmptyForNoComponents() throws Exception {
    String json = underTest.buildCollectionList(BASE_URL,
        Collections.emptyList(), 0, 100);

    JsonNode root = MAPPER.readTree(json);
    assertThat(root.get("meta").get("count").asInt(), is(0));
    assertThat(root.get("data").size(), is(0));
  }

  @Test
  public void buildCollectionDetailReturnsCorrectStructure() throws Exception {
    String json = underTest.buildCollectionDetail(BASE_URL,
        "testns", "testcol", Arrays.asList(component1, component2));

    JsonNode root = MAPPER.readTree(json);
    assertThat(root.get("namespace").asText(), is("testns"));
    assertThat(root.get("name").asText(), is("testcol"));
    assertThat(root.get("highest_version").get("version").asText(), is("2.0.0"));
    assertThat(root.get("versions_url").asText(), containsString("/versions/"));
  }

  @Test
  public void buildVersionListFiltersCorrectCollection() throws Exception {
    String json = underTest.buildVersionList(BASE_URL,
        "testns", "testcol",
        Arrays.asList(component1, component2, component3), 0, 100);

    JsonNode root = MAPPER.readTree(json);
    assertThat(root.get("meta").get("count").asInt(), is(2));
    assertThat(root.get("data").size(), is(2));
  }

  @Test
  public void buildVersionDetailIncludesDownloadUrlAndArtifact() throws Exception {
    String json = underTest.buildVersionDetail(BASE_URL,
        "testns", "testcol", "1.0.0", asset1);

    JsonNode root = MAPPER.readTree(json);
    assertThat(root.get("namespace").asText(), is("testns"));
    assertThat(root.get("name").asText(), is("testcol"));
    assertThat(root.get("version").asText(), is("1.0.0"));
    assertThat(root.get("download_url").asText(), containsString("/collections/artifacts/testns-testcol-1.0.0.tar.gz"));

    JsonNode artifact = root.get("artifact");
    assertThat(artifact.get("filename").asText(), is("testns-testcol-1.0.0.tar.gz"));
    assertThat(artifact.get("sha256").asText(), is("abc123def456"));
    assertThat(artifact.get("size").asLong(), is(12345L));

    assertThat(root.get("collection").get("namespace").asText(), is("testns"));
    assertThat(root.get("collection").get("name").asText(), is("testcol"));
  }

  @Test
  public void highestSemverReturnsHighestVersion() {
    Set<String> versions = new LinkedHashSet<>();
    versions.add("1.0.0");
    versions.add("2.1.0");
    versions.add("1.5.3");
    versions.add("2.0.9");

    assertThat(GalaxyResponseBuilder.highestSemver(versions), is("2.1.0"));
  }

  @Test
  public void highestSemverReturnsNullForEmpty() {
    assertThat(GalaxyResponseBuilder.highestSemver(new LinkedHashSet<>()), is(nullValue()));
  }
}
