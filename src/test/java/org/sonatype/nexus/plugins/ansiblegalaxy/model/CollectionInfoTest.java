package org.sonatype.nexus.plugins.ansiblegalaxy.model;

import org.sonatype.goodies.testsupport.TestSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class CollectionInfoTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void deserializesFromManifestJson() throws Exception {
    String json = "{"
        + "\"namespace\": \"testns\","
        + "\"name\": \"testcol\","
        + "\"version\": \"1.2.3\","
        + "\"description\": \"A test collection\""
        + "}";

    CollectionInfo info = MAPPER.readValue(json, CollectionInfo.class);

    assertThat(info.getNamespace(), is("testns"));
    assertThat(info.getName(), is("testcol"));
    assertThat(info.getVersion(), is("1.2.3"));
    assertThat(info.getDescription(), is("A test collection"));
  }

  @Test
  public void ignoresUnknownFields() throws Exception {
    String json = "{"
        + "\"namespace\": \"testns\","
        + "\"name\": \"testcol\","
        + "\"version\": \"1.0.0\","
        + "\"license\": [\"GPL-3.0-or-later\"],"
        + "\"authors\": [\"Author\"]"
        + "}";

    CollectionInfo info = MAPPER.readValue(json, CollectionInfo.class);

    assertThat(info.getNamespace(), is("testns"));
    assertThat(info.getName(), is("testcol"));
    assertThat(info.getVersion(), is("1.0.0"));
    assertThat(info.getDescription(), is(nullValue()));
  }

  @Test
  public void constructorSetsFields() {
    CollectionInfo info = new CollectionInfo("ns", "col", "2.0.0");

    assertThat(info.getNamespace(), is("ns"));
    assertThat(info.getName(), is("col"));
    assertThat(info.getVersion(), is("2.0.0"));
  }
}
