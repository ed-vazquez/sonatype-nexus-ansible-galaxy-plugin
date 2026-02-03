package org.sonatype.nexus.plugins.ansiblegalaxy.rest;

import javax.inject.Named;

import org.sonatype.nexus.repository.rest.api.HostedRepositoryApiRequestToConfigurationConverter;

@Named
public class AnsibleGalaxyHostedRepositoryApiRequestToConfigurationConverter
    extends HostedRepositoryApiRequestToConfigurationConverter<AnsibleGalaxyHostedRepositoryApiRequest>
{
}
