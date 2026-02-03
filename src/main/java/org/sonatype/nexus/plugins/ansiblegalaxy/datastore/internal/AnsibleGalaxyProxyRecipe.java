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
import org.sonatype.nexus.repository.httpclient.HttpClientFacet;
import org.sonatype.nexus.repository.purge.PurgeUnusedFacet;
import org.sonatype.nexus.repository.security.SecurityHandler;
import org.sonatype.nexus.repository.types.ProxyType;
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
 * Recipe for Ansible Galaxy proxy repositories.
 *
 * Defines routes for both short-form (CLI-constructed) and long-form (response body) URLs:
 *
 * Short form (CLI uses):
 *   /api/                                                        -> API root
 *   /api/v3/collections/{ns}/{name}/versions/?limit=100          -> version list
 *   /api/v3/collections/{ns}/{name}/versions/{ver}/              -> version detail
 *
 * Long form (response bodies contain):
 *   {PREFIX}/collections/index/                                  -> collection list
 *   {PREFIX}/collections/index/{ns}/{name}/                      -> collection detail
 *   {PREFIX}/collections/index/{ns}/{name}/{versions}/           -> version list
 *   {PREFIX}/collections/index/{ns}/{name}/versions/{ver}/       -> version detail
 *   {PREFIX}/collections/artifacts/{filename}                    -> artifact download
 */
@Named(AnsibleGalaxyProxyRecipe.NAME)
@Singleton
public class AnsibleGalaxyProxyRecipe
    extends RecipeSupport
{
  public static final String NAME = "ansiblegalaxy-proxy";

  private static final String PREFIX = "/api/v3/plugin/ansible/content/published";

  @Inject
  Provider<AnsibleGalaxySecurityFacet> securityFacet;

  @Inject
  Provider<AnsibleGalaxyContentFacet> contentFacet;

  @Inject
  Provider<ConfigurableViewFacet> viewFacet;

  @Inject
  Provider<HttpClientFacet> httpClientFacet;

  @Inject
  Provider<BrowseFacet> browseFacet;

  @Inject
  Provider<SearchFacet> searchFacet;

  @Inject
  Provider<ContentMaintenanceFacet> maintenanceFacet;

  @Inject
  Provider<PurgeUnusedFacet> purgeUnusedFacet;

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
  AnsibleGalaxyProxyHandler proxyHandler;

  @Inject
  public AnsibleGalaxyProxyRecipe(
      @Named(ProxyType.NAME) final Type type,
      @Named(AnsibleGalaxyFormat.NAME) final Format format) {
    super(type, format);
  }

  @Override
  public void apply(final Repository repository) throws Exception {
    repository.attach(securityFacet.get());
    repository.attach(configure(viewFacet.get()));
    repository.attach(contentFacet.get());
    repository.attach(httpClientFacet.get());
    repository.attach(maintenanceFacet.get());
    repository.attach(searchFacet.get());
    repository.attach(browseFacet.get());
    repository.attach(purgeUnusedFacet.get());
  }

  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder();

    // Route 1: GET /api/ — API root discovery (static response)
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher("/{api_root:api}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(proxyHandler)
        .create());

    // Route 2: GET /api/v3/collections/{ns}/{name}/versions/ — short-form version list
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher("/api/v3/collections/{namespace}/{name}/{version_marker:versions}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(proxyHandler)
        .create());

    // Route 3: GET /api/v3/collections/{ns}/{name}/versions/{version}/ — short-form version detail
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher("/api/v3/collections/{namespace}/{name}/versions/{version}/")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(proxyHandler)
        .create());

    // Route 4: GET {PREFIX}/collections/index/ — long-form collection list
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
        .handler(proxyHandler)
        .create());

    // Route 5: GET {PREFIX}/collections/index/{ns}/{name}/ — long-form collection detail
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
        .handler(proxyHandler)
        .create());

    // Route 6: GET {PREFIX}/collections/index/{ns}/{name}/versions/ — long-form version list
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
        .handler(proxyHandler)
        .create());

    // Route 7: GET {PREFIX}/collections/index/{ns}/{name}/versions/{version}/ — long-form version detail
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
        .handler(proxyHandler)
        .create());

    // Route 8: GET {PREFIX}/collections/artifacts/{filename} — artifact download (cached)
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
        .handler(proxyHandler)
        .create());

    builder.defaultHandlers(notFound());
    facet.configure(builder.create());
    return facet;
  }
}
