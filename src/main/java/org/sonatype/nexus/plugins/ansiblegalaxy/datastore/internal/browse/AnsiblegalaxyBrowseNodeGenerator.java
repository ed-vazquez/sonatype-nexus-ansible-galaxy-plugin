package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal.browse;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.repository.content.browse.AssetPathBrowseNodeGenerator;

@Singleton
@Named(AnsibleGalaxyFormat.NAME)
public class AnsiblegalaxyBrowseNodeGenerator
    extends AssetPathBrowseNodeGenerator {
}
