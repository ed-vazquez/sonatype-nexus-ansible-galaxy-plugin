package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.Format;

@Named(AnsibleGalaxyFormat.NAME)
@Singleton
public class AnsibleGalaxyFormat extends Format {

  public static final String NAME = "ansible-galaxy";

  public AnsibleGalaxyFormat() {
    super(NAME);
  }
}
