package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal.browse;

import javax.inject.Named;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.repository.content.browse.store.FormatBrowseModule;

@Named(AnsibleGalaxyFormat.NAME)
public class AnsibleGalaxyBrowseModule
    extends FormatBrowseModule<AnsibleGalaxyBrowseNodeDAO> {
}
