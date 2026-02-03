# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-02-03

### Added
- Hosted repository support for Ansible Galaxy collections in Nexus Repository Manager 3
- Proxy repository support for caching collections from upstream Galaxy servers
- Dynamic Galaxy v3 API metadata generation (collection lists, version lists, version details)
- URL rewriting for proxy repositories (download_url, href, pagination links)
- Artifact caching with namespace/name/version component tracking
- Multi-provider collection upload via MANIFEST.json extraction
- SHA-256 checksum verification
- REST API for hosted and proxy repository management
- HTTP endpoints for collection operations
- Nexus-native browse, search, cleanup policies, and security integration
- GitHub Actions CI (build & unit tests, integration tests, release, CodeQL)
- Dependabot for automated dependency updates
