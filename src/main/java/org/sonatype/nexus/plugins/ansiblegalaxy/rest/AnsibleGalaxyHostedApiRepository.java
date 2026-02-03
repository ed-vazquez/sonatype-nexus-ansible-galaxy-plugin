package org.sonatype.nexus.plugins.ansiblegalaxy.rest;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.repository.rest.api.model.CleanupPolicyAttributes;
import org.sonatype.nexus.repository.rest.api.model.ComponentAttributes;
import org.sonatype.nexus.repository.rest.api.model.HostedStorageAttributes;
import org.sonatype.nexus.repository.rest.api.model.SimpleApiHostedRepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AnsibleGalaxyHostedApiRepository
    extends SimpleApiHostedRepository
{
  @JsonCreator
  public AnsibleGalaxyHostedApiRepository(
      @JsonProperty("name") final String name,
      @JsonProperty("url") final String url,
      @JsonProperty("online") final Boolean online,
      @JsonProperty("storage") final HostedStorageAttributes storage,
      @JsonProperty("cleanup") final CleanupPolicyAttributes cleanup,
      @JsonProperty("component") final ComponentAttributes component) {
    super(name, AnsibleGalaxyFormat.NAME, url, online, storage, cleanup, component);
  }
}
