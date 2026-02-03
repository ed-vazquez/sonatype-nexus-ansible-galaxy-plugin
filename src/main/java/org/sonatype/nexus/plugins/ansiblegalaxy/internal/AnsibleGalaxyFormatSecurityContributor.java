package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.security.RepositoryFormatSecurityContributor;

@Named
@Singleton
public class AnsibleGalaxyFormatSecurityContributor extends RepositoryFormatSecurityContributor {

  @Inject
  public AnsibleGalaxyFormatSecurityContributor(
      @Named(AnsibleGalaxyFormat.NAME) final Format format) {
    super(format);
  }
}
