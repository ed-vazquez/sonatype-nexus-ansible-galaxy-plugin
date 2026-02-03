package org.sonatype.nexus.plugins.ansiblegalaxy.rest;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Path;

import org.sonatype.nexus.repository.rest.api.RepositoriesApiResourceV1;

import static org.sonatype.nexus.plugins.ansiblegalaxy.rest.AnsibleGalaxyHostedRepositoriesApiResourceV1.RESOURCE_URI;

@Named
@Singleton
@Path(RESOURCE_URI)
public class AnsibleGalaxyHostedRepositoriesApiResourceV1
    extends AnsibleGalaxyHostedRepositoriesApiResource
{
  static final String RESOURCE_URI = RepositoriesApiResourceV1.RESOURCE_URI + "/ansible-galaxy/hosted";
}
