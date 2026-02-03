package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal.store;

import javax.inject.Named;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.repository.content.store.FormatStoreModule;

@Named(AnsibleGalaxyFormat.NAME)
public class AnsibleGalaxyStoreModule
    extends FormatStoreModule<AnsibleGalaxyContentRepositoryDAO,
                              AnsibleGalaxyComponentDAO,
                              AnsibleGalaxyAssetDAO,
                              AnsibleGalaxyAssetBlobDAO> {
}
