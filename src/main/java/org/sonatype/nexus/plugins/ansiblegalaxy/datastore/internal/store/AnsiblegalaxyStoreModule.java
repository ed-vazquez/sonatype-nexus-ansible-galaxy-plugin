package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal.store;

import javax.inject.Named;

import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.repository.content.store.FormatStoreModule;

@Named(AnsibleGalaxyFormat.NAME)
public class AnsiblegalaxyStoreModule
    extends FormatStoreModule<AnsiblegalaxyContentRepositoryDAO,
                              AnsiblegalaxyComponentDAO,
                              AnsiblegalaxyAssetDAO,
                              AnsiblegalaxyAssetBlobDAO> {
}
