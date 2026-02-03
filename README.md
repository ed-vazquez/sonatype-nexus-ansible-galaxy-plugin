# Nexus Repository Ansible Galaxy Plugin

Sonatype Nexus Repository Manager 3 plugin that adds **hosted** and **proxy** repository support for [Ansible Galaxy](https://galaxy.ansible.com/) collections.

## Features

- **Hosted repositories** for publishing and serving private Ansible Galaxy collections
- **Proxy repositories** for caching collections from upstream Galaxy servers (e.g. `galaxy.ansible.com`)
- Dynamic Galaxy v3 API metadata generation (collection lists, version lists, version details)
- URL rewriting for proxy repos so `download_url` and pagination links route through Nexus
- Artifact caching with namespace/name/version component tracking
- SHA-256 checksum verification
- REST API for repository management
- Nexus-native browse, search, cleanup policies, and security integration

## Requirements

- Nexus Repository Manager 3.75.0+
- Java 17

## Installation

1. Download the latest release JAR from [Releases](https://github.com/ed-vazquez/sonatype-nexus-ansible-galaxy-plugin/releases), or build from source:
   ```bash
   make package
   ```
2. Copy `target/nexus-repository-ansiblegalaxy-*.jar` to `<nexus-dir>/deploy/`
3. Restart Nexus Repository Manager

## Usage

### Hosted Repository

#### Create Repository

```bash
curl -u admin:admin123 -X POST \
  http://localhost:8081/service/rest/v1/repositories/ansible-galaxy/hosted \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "ansible-galaxy-hosted",
    "online": true,
    "storage": {
      "blobStoreName": "default",
      "strictContentTypeValidation": true,
      "writePolicy": "ALLOW"
    }
  }'
```

#### Upload Collection

```bash
curl -u admin:admin123 -X POST \
  http://localhost:8081/repository/ansible-galaxy-hosted/api/v3/artifacts/collections/ \
  --data-binary @mynamespace-mycollection-1.0.0.tar.gz
```

#### Install with ansible-galaxy CLI

```bash
ansible-galaxy collection install mynamespace.mycollection \
  -s http://localhost:8081/repository/ansible-galaxy-hosted/api/
```

### Proxy Repository

#### Create Repository

```bash
curl -u admin:admin123 -X POST \
  http://localhost:8081/service/rest/v1/repositories/ansible-galaxy/proxy \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "ansible-galaxy-proxy",
    "online": true,
    "storage": {
      "blobStoreName": "default",
      "strictContentTypeValidation": true
    },
    "proxy": {
      "remoteUrl": "https://galaxy.ansible.com",
      "contentMaxAge": 1440,
      "metadataMaxAge": 1440
    },
    "negativeCache": {
      "enabled": true,
      "timeToLive": 1440
    },
    "httpClient": {
      "blocked": false,
      "autoBlock": true
    }
  }'
```

#### Install through Proxy

```bash
ansible-galaxy collection install community.general \
  -s http://localhost:8081/repository/ansible-galaxy-proxy/api/
```

Collections are transparently fetched from the upstream Galaxy server and cached locally. Subsequent requests are served from cache.

## API Reference

### Hosted Repository Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v3/artifacts/collections/` | Upload collection tar.gz |
| `GET` | `/api/v3/plugin/ansible/content/published/collections/index/` | List collections |
| `GET` | `/api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/` | Collection detail |
| `GET` | `/api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/versions/` | List versions |
| `GET` | `/api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/versions/{ver}/` | Version detail |
| `GET` | `/api/v3/plugin/ansible/content/published/collections/artifacts/{filename}` | Download artifact |
| `DELETE` | `/api/v3/plugin/ansible/content/published/collections/index/{ns}/{name}/versions/{ver}/` | Delete version |

### Proxy Repository Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/` | API root discovery |
| `GET` | `/api/v3/collections/{ns}/{name}/versions/` | Version list (short form) |
| `GET` | `/api/v3/collections/{ns}/{name}/versions/{ver}/` | Version detail (short form) |
| `GET` | `/api/v3/plugin/ansible/content/published/collections/artifacts/{filename}` | Download artifact (cached) |

Both short-form and long-form URL patterns are supported. The `ansible-galaxy` CLI uses short-form paths, while response bodies contain long-form paths.

### REST Management API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/service/rest/v1/repositories/ansible-galaxy/hosted` | Create hosted repository |
| `PUT` | `/service/rest/v1/repositories/ansible-galaxy/hosted/{name}` | Update hosted repository |
| `POST` | `/service/rest/v1/repositories/ansible-galaxy/proxy` | Create proxy repository |
| `PUT` | `/service/rest/v1/repositories/ansible-galaxy/proxy/{name}` | Update proxy repository |

## Development

### Build

```bash
make compile    # Compile
make test       # Unit tests
make verify     # Full build with tests and packaging
make package    # Build JAR (skip tests)
make clean      # Clean build artifacts
```

### Integration Tests

```bash
make integration-test
```

Requires Docker. Starts a real Nexus instance, deploys the plugin, and runs HTTP-level tests.

### Project Structure

```
src/main/java/org/sonatype/nexus/plugins/ansiblegalaxy/
  datastore/
    AnsibleGalaxyContentFacet.java          # Content facet interface
    internal/
      AnsibleGalaxyContentFacetImpl.java    # Content storage implementation
      AnsibleGalaxyHostedHandler.java       # Hosted request handler
      AnsibleGalaxyHostedRecipe.java        # Hosted repository recipe
      AnsibleGalaxyProxyHandler.java        # Proxy request handler
      AnsibleGalaxyProxyRecipe.java         # Proxy repository recipe
      browse/                               # Browse node support
      store/                                # DAO layer
  internal/
    AnsibleGalaxyFormat.java                # Format definition
    AnsibleGalaxySecurityFacet.java         # Security facet
    GalaxyResponseBuilder.java              # JSON response building
    GalaxyUpstreamClient.java               # Upstream Galaxy API client
  model/                                    # Data models
  rest/                                     # REST API resources
```

## License

[Apache License 2.0](LICENSE)
