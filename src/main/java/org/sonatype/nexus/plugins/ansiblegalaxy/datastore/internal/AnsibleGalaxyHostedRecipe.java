package org.sonatype.nexus.plugins.ansiblegalaxy.datastore.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.sonatype.nexus.plugins.ansiblegalaxy.datastore.AnsibleGalaxyContentFacet;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxyFormat;
import org.sonatype.nexus.plugins.ansiblegalaxy.internal.AnsibleGalaxySecurityFacet;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.RecipeSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.content.browse.BrowseFacet;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.search.SearchFacet;
import org.sonatype.nexus.repository.http.PartialFetchHandler;
import org.sonatype.nexus.repository.security.SecurityHandler;
import org.sonatype.nexus.repository.types.HostedType;
import org.sonatype.nexus.repository.view.ConfigurableViewFacet;
import org.sonatype.nexus.repository.view.Route;
import org.sonatype.nexus.repository.view.Router;
import org.sonatype.nexus.repository.view.ViewFacet;
import org.sonatype.nexus.repository.view.handlers.ConditionalRequestHandler;
import org.sonatype.nexus.repository.view.handlers.ContentHeadersHandler;
import org.sonatype.nexus.repository.view.handlers.ExceptionHandler;
import org.sonatype.nexus.repository.view.handlers.HandlerContributor;
import org.sonatype.nexus.repository.view.handlers.LastDownloadedHandler;
import org.sonatype.nexus.repository.view.handlers.TimingHandler;
import org.sonatype.nexus.repository.view.matchers.ActionMatcher;
import org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import static org.sonatype.nexus.repository.http.HttpHandlers.notFound;
import static org.sonatype.nexus.repository.http.HttpMethods.*;

/**
 * Recipe for Ansible Galaxy hosted repositories.
 *
 * Defines 7 Galaxy v3 API routes:
 * 1. POST   upload collection
 * 2. GET    list collections (paginated)
 * 3. GET    collection detail
 * 4. GET    list versions (paginated)
 * 5. GET    version detail
 * 6. GET    download artifact
 * 7. DELETE delete version
 */
@Named(AnsibleGalaxyHostedRecipe.NAME)
@Singleton
public class AnsibleGalaxyHostedRecipe
    extends RecipeSupport
{
  public static final String NAME = "ansible-galaxy-hosted";

  private static final String PREFIX = "/api/v3/plugin/ansible/content/published";

  @Inject
  Provider<AnsibleGalaxySecurityFacet> securityFacet;

  @Inject
  Provider<AnsibleGalaxyContentFacet> contentFacet;

  @Inject
  Provider<ConfigurableViewFacet> viewFacet;

  @Inject
  Provider<BrowseFacet> browseFacet;

  @Inject
  Provider<SearchFacet> searchFacet;

  @Inject
  Provider<ContentMaintenanceFacet> maintenanceFacet;

  @Inject
  TimingHandler timingHandler;

  @Inject
  SecurityHandler securityHandler;

  @Inject
  ExceptionHandler exceptionHandler;

  @Inject
  HandlerContributor handlerContributor;

  @Inject
  ConditionalRequestHandler conditionalRequestHandler;

  @Inject
  PartialFetchHandler partialFetchHandler;

  @Inject
  ContentHeadersHandler contentHeadersHandler;

  @Inject
  LastDownloadedHandler lastDownloadedHandler;

  @Inject
  AnsibleGalaxyHostedHandler hostedHandler;

  @Inject
  public AnsibleGalaxyHostedRecipe(
      @Named(HostedType.NAME) final Type type,
      @Named(AnsibleGalaxyFormat.NAME) final Format format) {
    super(type, format);
  }

  @Override
  public void apply(final Repository repository) throws Exception {
    repository.attach(securityFacet.get());
    repository.attach(configure(viewFacet.get()));
    repository.attach(contentFacet.get());
    repository.attach(maintenanceFacet.get());
    repository.attach(searchFacet.get());
    repository.attach(browseFacet.get());
  }

  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder();

    // Route 1: POST /api/v3/artifacts/collections/ — upload collection tar.gz
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(POST),
            new TokenMatcher("/api/v3/artifacts/collections/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(hostedHandler)
        .create());

    // Route 2: GET .../collections/index/ — list collections (paginated)
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher(PREFIX + "/collections/index/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // Route 3: GET .../collections/index/{namespace}/{name}/ — collection detail
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher(PREFIX + "/collections/index/{namespace}/{name}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // Route 4: GET .../collections/index/{namespace}/{name}/versions/ — list versions (paginated)
    // Uses a version_marker token to distinguish from collection detail
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher(PREFIX + "/collections/index/{namespace}/{name}/{version_marker:versions}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // Route 5: GET .../collections/index/{namespace}/{name}/versions/{version}/ — version detail
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher(PREFIX + "/collections/index/{namespace}/{name}/versions/{version}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // Route 6: GET .../collections/artifacts/{filename} — download artifact
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher(PREFIX + "/collections/artifacts/{filename}")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // Route 7: DELETE .../collections/index/{namespace}/{name}/versions/{version}/ — delete version
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(DELETE),
            new TokenMatcher(PREFIX + "/collections/index/{namespace}/{name}/versions/{version}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(hostedHandler)
        .create());

    builder.defaultHandlers(notFound());
    facet.configure(builder.create());
    return facet;
  }
}
