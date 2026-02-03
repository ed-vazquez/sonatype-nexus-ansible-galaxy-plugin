#!/usr/bin/env bash
#
# Integration tests for the Ansible Galaxy repository plugin (hosted + proxy).
#
# Builds the plugin, installs it into a Nexus 3 Docker container,
# and runs HTTP-level tests against Galaxy v3 API endpoints.
#
# Hosted tests:
#   - Create ansible-galaxy-hosted repository via REST API
#   - Upload a collection tar.gz
#   - List collections, collection detail, version list, version detail
#   - Download artifact and verify checksum
#   - Delete version and verify 404
#
# Proxy tests:
#   - Create ansible-galaxy-proxy repository pointing at galaxy.ansible.com
#   - API root discovery
#   - Version list via short-form URL, verify URL rewriting
#   - Version detail, verify download_url is rewritten to proxy
#   - Download artifact through proxy (upstream fetch)
#   - Download again (verify served from cache with matching checksum)
#   - Version list via long-form URL
#
# Usage: ./src/test/integration/run-it.sh
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
NEXUS_VERSION="3.75.0"
CONTAINER_NAME="nexus-ansiblegalaxy-it-$$"
NEXUS_PORT=8081
MAX_WAIT=120
PASSED=0
FAILED=0

section_open() {
  if [ "${CI:-}" = "true" ]; then
    echo "::group::$1"
  else
    echo ""
    echo "=== $1 ==="
  fi
}

section_close() {
  if [ "${CI:-}" = "true" ]; then
    echo "::endgroup::"
  fi
}

cleanup() {
  section_open "Cleanup"
  docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
  section_close
}
trap cleanup EXIT

fail() {
  echo "  FAIL: $1"
  if [ "${CI:-}" = "true" ]; then
    echo "::error::$1"
  fi
  FAILED=$((FAILED + 1))
}

pass() {
  echo "  PASS: $1"
  PASSED=$((PASSED + 1))
}

assert_status() {
  local expected="$1" actual="$2" desc="$3"
  if [ "$actual" -eq "$expected" ]; then
    pass "$desc (HTTP $actual)"
  else
    fail "$desc — expected HTTP $expected, got HTTP $actual"
  fi
}

JAR="$PROJECT_DIR/target/nexus-repository-ansiblegalaxy-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR" ]; then
  echo "Plugin JAR already exists: $(basename "$JAR"), skipping build."
else
  section_open "Building plugin"
  docker run --rm \
    -v "$PROJECT_DIR":/build -w /build \
    maven:3.9-eclipse-temurin-17 \
    mvn clean package -s /build/.mvn/maven-settings.xml -DskipTests -q

  if [ ! -f "$JAR" ]; then
    echo "ERROR: Plugin JAR not found at $JAR"
    exit 1
  fi
  echo "Plugin JAR built: $(basename "$JAR")"
  section_close
fi

section_open "Starting Nexus container"
docker run -d \
  --name "$CONTAINER_NAME" \
  -p "$NEXUS_PORT:8081" \
  -v "$JAR:/opt/sonatype/nexus/deploy/nexus-repository-ansiblegalaxy-1.0.0-SNAPSHOT.jar:ro" \
  "sonatype/nexus3:$NEXUS_VERSION"

echo "Waiting for Nexus to start (up to ${MAX_WAIT}s)..."
ELAPSED=0
until curl -sf "http://localhost:$NEXUS_PORT/service/rest/v1/status" >/dev/null 2>&1; do
  sleep 5
  ELAPSED=$((ELAPSED + 5))
  if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
    echo "ERROR: Nexus did not start within ${MAX_WAIT}s"
    docker logs "$CONTAINER_NAME" 2>&1 | tail -30
    exit 1
  fi
  printf "  %ds...\n" "$ELAPSED"
done
echo "Nexus is ready."

# Get admin password
ADMIN_PASS=$(docker exec "$CONTAINER_NAME" cat /nexus-data/admin.password 2>/dev/null || echo "admin123")
AUTH="admin:$ADMIN_PASS"
section_close

section_open "Creating ansible-galaxy-hosted repository"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X POST "http://localhost:$NEXUS_PORT/service/rest/v1/repositories/ansible-galaxy/hosted" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "galaxy-test",
    "online": true,
    "storage": {
      "blobStoreName": "default",
      "strictContentTypeValidation": false,
      "writePolicy": "ALLOW"
    }
  }')

if [ "$HTTP_CODE" -eq 201 ] || [ "$HTTP_CODE" -eq 200 ]; then
  pass "Create repository (HTTP $HTTP_CODE)"
else
  fail "Create repository — HTTP $HTTP_CODE (plugin may not have loaded)"
  echo "Checking Nexus logs for plugin errors:"
  docker logs "$CONTAINER_NAME" 2>&1 | grep -i -E '(ansible|galaxy|ERROR|WARN.*bundle)' | tail -20
  section_close
  echo ""
  echo "Results: $PASSED passed, $FAILED failed"
  exit 1
fi

REPO_URL="http://localhost:$NEXUS_PORT/repository/galaxy-test"
API_PREFIX="$REPO_URL/api/v3/plugin/ansible/content/published"
section_close

# Create a dummy collection tar.gz with a valid MANIFEST.json
section_open "Preparing test collection"
TMPDIR=$(mktemp -d)
COLLECTION_DIR="$TMPDIR/testns-testcol-1.0.0"
mkdir -p "$COLLECTION_DIR"
cat > "$COLLECTION_DIR/MANIFEST.json" <<'EOF'
{
  "collection_info": {
    "namespace": "testns",
    "name": "testcol",
    "version": "1.0.0",
    "description": "A test collection"
  }
}
EOF
echo "placeholder" > "$COLLECTION_DIR/README.md"
COLLECTION_TAR="$TMPDIR/testns-testcol-1.0.0.tar.gz"
tar -czf "$COLLECTION_TAR" -C "$TMPDIR" "testns-testcol-1.0.0"
COLLECTION_SHA256=$(shasum -a 256 "$COLLECTION_TAR" 2>/dev/null || sha256sum "$COLLECTION_TAR")
COLLECTION_SHA256=$(echo "$COLLECTION_SHA256" | awk '{print $1}')
echo "  Collection tar.gz: $(basename "$COLLECTION_TAR") (sha256: $COLLECTION_SHA256)"

# Also prepare a v2.0.0
COLLECTION_DIR2="$TMPDIR/testns-testcol-2.0.0"
mkdir -p "$COLLECTION_DIR2"
cat > "$COLLECTION_DIR2/MANIFEST.json" <<'EOF'
{
  "collection_info": {
    "namespace": "testns",
    "name": "testcol",
    "version": "2.0.0",
    "description": "A test collection v2"
  }
}
EOF
echo "placeholder v2" > "$COLLECTION_DIR2/README.md"
COLLECTION_TAR2="$TMPDIR/testns-testcol-2.0.0.tar.gz"
tar -czf "$COLLECTION_TAR2" -C "$TMPDIR" "testns-testcol-2.0.0"
section_close

section_open "Test: Upload collection v1.0.0"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X POST "$REPO_URL/api/v3/artifacts/collections/" \
  -F "file=@$COLLECTION_TAR")
assert_status 201 "$HTTP_CODE" "POST upload testns-testcol-1.0.0"
section_close

section_open "Test: Upload collection v2.0.0"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X POST "$REPO_URL/api/v3/artifacts/collections/" \
  -F "file=@$COLLECTION_TAR2")
assert_status 201 "$HTTP_CODE" "POST upload testns-testcol-2.0.0"
section_close

section_open "Test: List collections"
RESPONSE_FILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$API_PREFIX/collections/index/")
assert_status 200 "$HTTP_CODE" "GET list collections"

if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
if r.get('meta', {}).get('count', 0) < 1:
    errors.append('count should be >= 1')
data = r.get('data', [])
found = any(d.get('namespace') == 'testns' and d.get('name') == 'testcol' for d in data)
if not found:
    errors.append('testns/testcol not found in data')
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "Collection list JSON structure is valid"
  else
    fail "Collection list validation: $VALIDATION"
  fi
fi
section_close

section_open "Test: Get collection detail"
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$API_PREFIX/collections/index/testns/testcol/")
assert_status 200 "$HTTP_CODE" "GET collection detail testns/testcol"

if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
if r.get('namespace') != 'testns':
    errors.append(f\"namespace: expected 'testns', got '{r.get('namespace')}'\")
if r.get('name') != 'testcol':
    errors.append(f\"name: expected 'testcol', got '{r.get('name')}'\")
hv = r.get('highest_version', {}).get('version')
if hv != '2.0.0':
    errors.append(f\"highest_version: expected '2.0.0', got '{hv}'\")
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "Collection detail JSON is valid"
  else
    fail "Collection detail validation: $VALIDATION"
  fi
fi
section_close

section_open "Test: List versions"
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$API_PREFIX/collections/index/testns/testcol/versions/")
assert_status 200 "$HTTP_CODE" "GET list versions testns/testcol"

if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
versions = {d.get('version') for d in r.get('data', [])}
if '1.0.0' not in versions:
    errors.append('missing version 1.0.0')
if '2.0.0' not in versions:
    errors.append('missing version 2.0.0')
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "Version list JSON is valid"
  else
    fail "Version list validation: $VALIDATION"
  fi
fi
section_close

section_open "Test: Get version detail"
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$API_PREFIX/collections/index/testns/testcol/versions/1.0.0/")
assert_status 200 "$HTTP_CODE" "GET version detail testns/testcol/1.0.0"

if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
if r.get('version') != '1.0.0':
    errors.append(f\"version: expected '1.0.0', got '{r.get('version')}'\")
if not r.get('download_url'):
    errors.append('missing download_url')
if not r.get('artifact', {}).get('filename'):
    errors.append('missing artifact.filename')
if not r.get('artifact', {}).get('sha256'):
    errors.append('missing artifact.sha256')
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "Version detail JSON is valid"
  else
    fail "Version detail validation: $VALIDATION"
  fi
  echo "  Version detail content:"
  python3 -m json.tool "$RESPONSE_FILE" 2>/dev/null | sed 's/^/    /'
fi
section_close

section_open "Test: Download artifact"
DOWNLOAD_FILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$DOWNLOAD_FILE" -w '%{http_code}' \
  "$API_PREFIX/collections/artifacts/testns-testcol-1.0.0.tar.gz")
assert_status 200 "$HTTP_CODE" "GET download testns-testcol-1.0.0.tar.gz"

DOWNLOAD_SHA256=$(shasum -a 256 "$DOWNLOAD_FILE" 2>/dev/null || sha256sum "$DOWNLOAD_FILE")
DOWNLOAD_SHA256=$(echo "$DOWNLOAD_SHA256" | awk '{print $1}')
if [ "$COLLECTION_SHA256" = "$DOWNLOAD_SHA256" ]; then
  pass "Downloaded file checksum matches upload"
else
  fail "Checksum mismatch: uploaded=$COLLECTION_SHA256 downloaded=$DOWNLOAD_SHA256"
fi
section_close

section_open "Test: Not found"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$API_PREFIX/collections/index/noexist/noexist/")
assert_status 404 "$HTTP_CODE" "GET nonexistent collection detail"

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$API_PREFIX/collections/index/testns/testcol/versions/9.9.9/")
assert_status 404 "$HTTP_CODE" "GET nonexistent version detail"

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$API_PREFIX/collections/artifacts/noexist-noexist-1.0.0.tar.gz")
assert_status 404 "$HTTP_CODE" "GET nonexistent artifact download"
section_close

section_open "Test: Delete version"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X DELETE \
  "$API_PREFIX/collections/index/testns/testcol/versions/2.0.0/")
assert_status 204 "$HTTP_CODE" "DELETE testns/testcol/2.0.0"

# Verify deleted version returns 404
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$API_PREFIX/collections/index/testns/testcol/versions/2.0.0/")
assert_status 404 "$HTTP_CODE" "GET deleted version returns 404"

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  "$API_PREFIX/collections/artifacts/testns-testcol-2.0.0.tar.gz")
assert_status 404 "$HTTP_CODE" "GET deleted artifact returns 404"

# Verify version list no longer includes deleted version
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$API_PREFIX/collections/index/testns/testcol/versions/")
if command -v python3 >/dev/null 2>&1; then
  HAS_200=$(python3 -c "
import json
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
versions = {d.get('version') for d in r.get('data', [])}
print('2.0.0' not in versions)
" 2>&1)
  if [ "$HAS_200" = "True" ]; then
    pass "Version list no longer includes deleted version"
  else
    fail "Version list still includes deleted version 2.0.0"
  fi
fi
section_close

######################################################################
# Proxy repository tests
######################################################################

section_open "Creating ansible-galaxy-proxy repository"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
  -u "$AUTH" \
  -X POST "http://localhost:$NEXUS_PORT/service/rest/v1/repositories/ansible-galaxy/proxy" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "galaxy-proxy-test",
    "online": true,
    "storage": {
      "blobStoreName": "default",
      "strictContentTypeValidation": false
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
  }')

if [ "$HTTP_CODE" -eq 201 ] || [ "$HTTP_CODE" -eq 200 ]; then
  pass "Create proxy repository (HTTP $HTTP_CODE)"
else
  fail "Create proxy repository — HTTP $HTTP_CODE"
  echo "Checking Nexus logs for plugin errors:"
  docker logs "$CONTAINER_NAME" 2>&1 | grep -i -E '(ansible|galaxy|proxy|ERROR|WARN.*bundle)' | tail -20
  section_close
  echo ""
  echo "Results: $PASSED passed, $FAILED failed"
  exit 1
fi

PROXY_URL="http://localhost:$NEXUS_PORT/repository/galaxy-proxy-test"
PROXY_PREFIX="$PROXY_URL/api/v3/plugin/ansible/content/published"
section_close

# Use a small, well-known collection for proxy tests
PROXY_NS="ansible"
PROXY_NAME="netcommon"

section_open "Test: Proxy API root discovery"
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$PROXY_URL/api/")
assert_status 200 "$HTTP_CODE" "GET proxy /api/ root"

if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
av = r.get('available_versions', {})
if 'v3' not in av:
    errors.append('missing v3 in available_versions')
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "API root JSON structure is valid"
  else
    fail "API root validation: $VALIDATION"
  fi
fi
section_close

section_open "Test: Proxy version list (short-form URL)"
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$PROXY_URL/api/v3/collections/$PROXY_NS/$PROXY_NAME/versions/?limit=5")
assert_status 200 "$HTTP_CODE" "GET proxy version list $PROXY_NS/$PROXY_NAME"

if command -v python3 >/dev/null 2>&1; then
  VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
if r.get('meta', {}).get('count', 0) < 1:
    errors.append('count should be >= 1')
data = r.get('data', [])
if len(data) < 1:
    errors.append('data should have at least 1 version')
# Verify URLs are rewritten to point at proxy, not galaxy.ansible.com
for item in data:
    href = item.get('href', '')
    if 'galaxy.ansible.com' in href:
        errors.append(f'href not rewritten: {href}')
        break
links = r.get('links', {})
for link_name in ['first', 'last', 'next']:
    link = links.get(link_name, '')
    if link and 'galaxy.ansible.com' in link:
        errors.append(f'pagination link {link_name} not rewritten: {link}')
        break
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
  if [ "$?" -eq 0 ]; then
    pass "Proxy version list JSON is valid with rewritten URLs"
  else
    fail "Proxy version list validation: $VALIDATION"
  fi
fi
section_close

section_open "Test: Proxy version detail (short-form URL)"
# Get the first version from the version list
if command -v python3 >/dev/null 2>&1; then
  PROXY_VERSION=$(python3 -c "
import json
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
data = r.get('data', [])
if data:
    print(data[0].get('version', ''))
" 2>&1)
else
  PROXY_VERSION="2.0.0"
fi

if [ -n "$PROXY_VERSION" ]; then
  HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
    "$PROXY_URL/api/v3/collections/$PROXY_NS/$PROXY_NAME/versions/$PROXY_VERSION/")
  assert_status 200 "$HTTP_CODE" "GET proxy version detail $PROXY_NS/$PROXY_NAME/$PROXY_VERSION"

  if command -v python3 >/dev/null 2>&1; then
    VALIDATION=$(python3 -c "
import json, sys
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
errors = []
if r.get('version') != '$PROXY_VERSION':
    errors.append(f\"version: expected '$PROXY_VERSION', got '{r.get('version')}'\")
download_url = r.get('download_url', '')
if not download_url:
    errors.append('missing download_url')
elif 'galaxy.ansible.com' in download_url:
    errors.append(f'download_url not rewritten: {download_url}')
elif 'galaxy-proxy-test' not in download_url:
    errors.append(f'download_url does not point to proxy repo: {download_url}')
href = r.get('href', '')
if href and 'galaxy.ansible.com' in href:
    errors.append(f'href not rewritten: {href}')
if errors:
    print('FAIL: ' + '; '.join(errors))
    sys.exit(1)
print('OK')
" 2>&1)
    if [ "$?" -eq 0 ]; then
      pass "Proxy version detail has rewritten download_url"
    else
      fail "Proxy version detail validation: $VALIDATION"
    fi
    echo "  Version detail content:"
    python3 -m json.tool "$RESPONSE_FILE" 2>/dev/null | head -20 | sed 's/^/    /'
  fi
else
  fail "Could not determine proxy version to test"
fi
section_close

section_open "Test: Proxy artifact download (first request — upstream fetch)"
# Extract download path from version detail
if command -v python3 >/dev/null 2>&1; then
  ARTIFACT_PATH=$(python3 -c "
import json
with open('$RESPONSE_FILE') as f:
    r = json.load(f)
url = r.get('download_url', '')
# Extract path after the repo URL
prefix = 'http://localhost:$NEXUS_PORT/repository/galaxy-proxy-test'
if url.startswith(prefix):
    print(url[len(prefix):])
" 2>&1)
fi

if [ -n "${ARTIFACT_PATH:-}" ]; then
  PROXY_DOWNLOAD=$(mktemp)
  HTTP_CODE=$(curl -s -o "$PROXY_DOWNLOAD" -w '%{http_code}' \
    "$PROXY_URL$ARTIFACT_PATH")
  assert_status 200 "$HTTP_CODE" "GET proxy artifact download (first request)"

  PROXY_SHA256=$(shasum -a 256 "$PROXY_DOWNLOAD" 2>/dev/null || sha256sum "$PROXY_DOWNLOAD")
  PROXY_SHA256=$(echo "$PROXY_SHA256" | awk '{print $1}')
  PROXY_SIZE=$(wc -c < "$PROXY_DOWNLOAD" | tr -d ' ')

  if [ "$PROXY_SIZE" -gt 1000 ]; then
    pass "Downloaded artifact is non-trivial ($PROXY_SIZE bytes)"
  else
    fail "Downloaded artifact seems too small ($PROXY_SIZE bytes)"
  fi
else
  fail "Could not extract artifact download path from version detail"
fi
section_close

section_open "Test: Proxy artifact download (second request — should serve from cache)"
if [ -n "${ARTIFACT_PATH:-}" ]; then
  PROXY_DOWNLOAD2=$(mktemp)
  HTTP_CODE=$(curl -s -o "$PROXY_DOWNLOAD2" -w '%{http_code}' \
    "$PROXY_URL$ARTIFACT_PATH")
  assert_status 200 "$HTTP_CODE" "GET proxy artifact download (cached)"

  PROXY_SHA256_2=$(shasum -a 256 "$PROXY_DOWNLOAD2" 2>/dev/null || sha256sum "$PROXY_DOWNLOAD2")
  PROXY_SHA256_2=$(echo "$PROXY_SHA256_2" | awk '{print $1}')

  if [ "$PROXY_SHA256" = "$PROXY_SHA256_2" ]; then
    pass "Cached artifact checksum matches first download"
  else
    fail "Cache checksum mismatch: first=$PROXY_SHA256 second=$PROXY_SHA256_2"
  fi
  rm -f "$PROXY_DOWNLOAD" "$PROXY_DOWNLOAD2"
else
  fail "Skipped — no artifact path"
fi
section_close

section_open "Test: Proxy version list (long-form URL)"
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' \
  "$PROXY_PREFIX/collections/index/$PROXY_NS/$PROXY_NAME/versions/?limit=5")
assert_status 200 "$HTTP_CODE" "GET proxy version list (long-form URL)"
section_close

# Cleanup temp files
rm -rf "$TMPDIR" "$RESPONSE_FILE" "$DOWNLOAD_FILE"

echo ""
echo "========================================"
echo "  Results: $PASSED passed, $FAILED failed"
echo "========================================"

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi
