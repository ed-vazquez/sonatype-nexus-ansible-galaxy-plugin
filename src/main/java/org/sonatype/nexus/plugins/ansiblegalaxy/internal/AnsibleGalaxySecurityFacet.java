package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.SecurityFacetSupport;
import org.sonatype.nexus.repository.security.VariableResolverAdapter;

@Named
public class AnsibleGalaxySecurityFacet extends SecurityFacetSupport {

  @Inject
  public AnsibleGalaxySecurityFacet(
      final AnsibleGalaxyFormatSecurityContributor securityContributor,
      @Named("simple") final VariableResolverAdapter variableResolverAdapter,
      final ContentPermissionChecker contentPermissionChecker) {
    super(securityContributor, variableResolverAdapter, contentPermissionChecker);
  }
}
