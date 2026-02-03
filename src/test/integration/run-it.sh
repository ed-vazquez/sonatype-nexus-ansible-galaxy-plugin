#!/usr/bin/env bash
#
# Integration test for the Ansible Galaxy hosted repository plugin.
#
# Builds the plugin, installs it into a Nexus 3 Docker container,
# and runs HTTP-level tests against Galaxy v3 API endpoints:
#   - Create ansible-galaxy-hosted repository via REST API
#   - Upload a collection tar.gz
#   - List collections
#   - Get collection detail
#   - List versions
#   - Get version detail (includes download_url)
#   - Download artifact and verify checksum
#   - Delete version and verify 404
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

# Cleanup temp files
rm -rf "$TMPDIR" "$RESPONSE_FILE" "$DOWNLOAD_FILE"

echo ""
echo "========================================"
echo "  Results: $PASSED passed, $FAILED failed"
echo "========================================"

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi
