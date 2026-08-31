package org.sonatype.nexus.plugins.ansiblegalaxy.internal;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AnsibleGalaxyFormatTest
    extends TestSupport
{
  @Test
  public void formatNameIsAnsibleGalaxy() {
    AnsibleGalaxyFormat format = new AnsibleGalaxyFormat();
    assertThat(format.getValue(), is("ansiblegalaxy"));
  }

  @Test
  public void nameConstantMatchesValue() {
    assertThat(AnsibleGalaxyFormat.NAME, is("ansiblegalaxy"));
    AnsibleGalaxyFormat format = new AnsibleGalaxyFormat();
    assertThat(format.getValue(), is(AnsibleGalaxyFormat.NAME));
  }
}
