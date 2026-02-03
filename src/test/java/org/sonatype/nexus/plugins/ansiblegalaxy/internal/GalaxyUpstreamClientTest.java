package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Unit tests for URL building and rewriting logic in {@link GalaxyUpstreamClient}.
 */
public class GalaxyUpstreamClientTest
    extends TestSupport
{
  private GalaxyUpstreamClient underTest;

  @Before
  public void setUp() {
    underTest = new GalaxyUpstreamClient();
  }

  // -- buildUpstreamUrl tests --

  @Test
  public void buildUpstreamUrlNoTrailingSlash() {
    String result = underTest.buildUpstreamUrl(
        "https://galaxy.ansible.com",
        "/api/v3/plugin/ansible/content/published/collections/index/");
    assertThat(result, is("https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/index/"));
  }

  @Test
  public void buildUpstreamUrlWithTrailingSlash() {
    String result = underTest.buildUpstreamUrl(
        "https://galaxy.ansible.com/",
        "/api/v3/plugin/ansible/content/published/collections/index/");
    assertThat(result, is("https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/index/"));
  }

  // -- rewriteUrls tests --

  @Test
  public void rewriteDownloadUrl() {
    String json = "{\"download_url\": \"https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-5.0.0.tar.gz\"}";
    String result = underTest.rewriteUrls(json,
        "https://galaxy.ansible.com",
        "http://nexus:8081/repository/galaxy-proxy");
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-5.0.0.tar.gz"));
    assertThat(result, not(containsString("galaxy.ansible.com")));
  }

  @Test
  public void rewriteHrefFields() {
    String json = "{\"href\": \"https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/index/community/general/\"}";
    String result = underTest.rewriteUrls(json,
        "https://galaxy.ansible.com",
        "http://nexus:8081/repository/galaxy-proxy");
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/index/community/general/"));
    assertThat(result, not(containsString("galaxy.ansible.com")));
  }

  @Test
  public void rewritePaginationLinks() {
    String json = "{\"links\": {\"next\": \"https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/?offset=100&limit=100\"}}";
    String result = underTest.rewriteUrls(json,
        "https://galaxy.ansible.com",
        "http://nexus:8081/repository/galaxy-proxy");
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/?offset=100&limit=100"));
    assertThat(result, not(containsString("galaxy.ansible.com")));
  }

  @Test
  public void rewriteMultipleUrlsInOneJson() {
    String json = "{"
        + "\"href\": \"https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/5.0.0/\","
        + "\"download_url\": \"https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-5.0.0.tar.gz\","
        + "\"collection\": {\"href\": \"https://galaxy.ansible.com/api/v3/plugin/ansible/content/published/collections/index/community/general/\"}"
        + "}";
    String result = underTest.rewriteUrls(json,
        "https://galaxy.ansible.com",
        "http://nexus:8081/repository/galaxy-proxy");
    assertThat(result, not(containsString("galaxy.ansible.com")));
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/index/community/general/versions/5.0.0/"));
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/artifacts/community-general-5.0.0.tar.gz"));
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/index/community/general/"));
  }

  @Test
  public void rewritePreservesNonUpstreamContent() {
    String json = "{\"name\": \"general\", \"namespace\": \"community\", \"version\": \"5.0.0\"}";
    String result = underTest.rewriteUrls(json,
        "https://galaxy.ansible.com",
        "http://nexus:8081/repository/galaxy-proxy");
    assertThat(result, is(json));
  }

  @Test
  public void rewriteHandlesUpstreamWithPort() {
    String json = "{\"href\": \"https://custom-galaxy.example.com:8443/api/v3/plugin/ansible/content/published/collections/index/\"}";
    String result = underTest.rewriteUrls(json,
        "https://custom-galaxy.example.com:8443",
        "http://nexus:8081/repository/galaxy-proxy");
    assertThat(result, containsString("http://nexus:8081/repository/galaxy-proxy/api/v3/plugin/ansible/content/published/collections/index/"));
    assertThat(result, not(containsString("custom-galaxy.example.com")));
  }
}
