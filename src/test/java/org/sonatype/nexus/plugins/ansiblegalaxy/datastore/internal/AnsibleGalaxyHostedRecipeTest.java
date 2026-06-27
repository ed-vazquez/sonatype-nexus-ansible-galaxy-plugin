package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxySecurityFacet;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.browse.BrowseFacet;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.search.SearchFacet;
import org.sonatype.nexus.repository.http.PartialFetchHandler;
import org.sonatype.nexus.repository.security.SecurityHandler;
import org.sonatype.nexus.repository.types.HostedType;
import org.sonatype.nexus.repository.view.ConfigurableViewFacet;
import org.sonatype.nexus.repository.view.handlers.ConditionalRequestHandler;
import org.sonatype.nexus.repository.view.handlers.ContentHeadersHandler;
import org.sonatype.nexus.repository.view.handlers.ExceptionHandler;
import org.sonatype.nexus.repository.view.handlers.HandlerContributor;
import org.sonatype.nexus.repository.view.handlers.LastDownloadedHandler;
import org.sonatype.nexus.repository.view.handlers.TimingHandler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

public class AnsibleGalaxyHostedRecipeTest
    extends TestSupport
{
  @Mock private Repository repository;
  @Mock private AnsibleGalaxySecurityFacet ansibleGalaxySecurityFacet;
  @Mock private AnsibleGalaxyContentFacet ansibleGalaxyContentFacet;
  @Mock private ConfigurableViewFacet viewFacet;
  @Mock private BrowseFacet browseFacet;
  @Mock private SearchFacet searchFacet;
  @Mock private ContentMaintenanceFacet maintenanceFacet;
  @Mock private TimingHandler timingHandler;
  @Mock private SecurityHandler securityHandler;
  @Mock private ExceptionHandler exceptionHandler;
  @Mock private HandlerContributor handlerContributor;
  @Mock private ConditionalRequestHandler conditionalRequestHandler;
  @Mock private PartialFetchHandler partialFetchHandler;
  @Mock private ContentHeadersHandler contentHeadersHandler;
  @Mock private LastDownloadedHandler lastDownloadedHandler;
  @Mock private AnsibleGalaxyHostedHandler hostedHandler;

  private AnsibleGalaxyHostedRecipe underTest;

  @Before
  public void setUp() {
    underTest = new AnsibleGalaxyHostedRecipe(new HostedType(), new AnsibleGalaxyFormat());

    underTest.securityFacet = () -> ansibleGalaxySecurityFacet;
    underTest.contentFacet = () -> ansibleGalaxyContentFacet;
    underTest.viewFacet = () -> viewFacet;
    underTest.browseFacet = () -> browseFacet;
    underTest.searchFacet = () -> searchFacet;
    underTest.maintenanceFacet = () -> maintenanceFacet;
    underTest.timingHandler = timingHandler;
    underTest.securityHandler = securityHandler;
    underTest.exceptionHandler = exceptionHandler;
    underTest.handlerContributor = handlerContributor;
    underTest.conditionalRequestHandler = conditionalRequestHandler;
    underTest.partialFetchHandler = partialFetchHandler;
    underTest.contentHeadersHandler = contentHeadersHandler;
    underTest.lastDownloadedHandler = lastDownloadedHandler;
    underTest.hostedHandler = hostedHandler;
  }

  @Test
  public void recipeNameIsCorrect() {
    assertThat(AnsibleGalaxyHostedRecipe.NAME, is("ansiblegalaxy-hosted"));
  }

  @Test
  public void applyAttachesContentFacet() throws Exception {
    underTest.apply(repository);
    verify(repository).attach(ansibleGalaxyContentFacet);
  }

  @Test
  public void applyAttachesBrowseFacet() throws Exception {
    underTest.apply(repository);
    verify(repository).attach(browseFacet);
  }

  @Test
  public void applyAttachesSearchFacet() throws Exception {
    underTest.apply(repository);
    verify(repository).attach(searchFacet);
  }

  @Test
  public void applyAttachesViewFacet() throws Exception {
    underTest.apply(repository);
    verify(repository).attach(viewFacet);
  }
}
