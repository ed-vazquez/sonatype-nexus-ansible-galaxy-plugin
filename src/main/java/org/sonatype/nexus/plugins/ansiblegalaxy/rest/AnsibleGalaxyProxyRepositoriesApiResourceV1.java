package org.sonatype.nexus.plugins.ansiblegalaxy.rest;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Path;

import org.sonatype.nexus.repository.rest.api.RepositoriesApiResourceV1;

import static org.sonatype.nexus.plugins.ansiblegalaxy.rest.AnsibleGalaxyProxyRepositoriesApiResourceV1.RESOURCE_URI;

@Named
@Singleton
@Path(RESOURCE_URI)
public class AnsibleGalaxyProxyRepositoriesApiResourceV1
    extends AnsibleGalaxyProxyRepositoriesApiResource
{
  static final String RESOURCE_URI = RepositoriesApiResourceV1.RESOURCE_URI + "/ansible-galaxy/proxy";
}
