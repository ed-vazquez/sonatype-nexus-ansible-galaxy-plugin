package org.sonatype.nexus.plugins.ansiblegalaxy.rest;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.repository.rest.api.model.CleanupPolicyAttributes;
import org.sonatype.nexus.repository.rest.api.model.ComponentAttributes;
import org.sonatype.nexus.repository.rest.api.model.HostedRepositoryApiRequest;
import org.sonatype.nexus.repository.rest.api.model.HostedStorageAttributes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"format", "type"})
public class AnsibleGalaxyHostedRepositoryApiRequest
    extends HostedRepositoryApiRequest
{
  @JsonCreator
  public AnsibleGalaxyHostedRepositoryApiRequest(
      @JsonProperty("name") final String name,
      @JsonProperty("online") final Boolean online,
      @JsonProperty("storage") final HostedStorageAttributes storage,
      @JsonProperty("cleanup") final CleanupPolicyAttributes cleanup,
      @JsonProperty("component") final ComponentAttributes componentAttributes) {
    super(name, AnsibleGalaxyFormat.NAME, online, storage, cleanup, componentAttributes);
  }
}
