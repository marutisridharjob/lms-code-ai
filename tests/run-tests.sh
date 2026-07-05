#!/usr/bin/env bash
# Runs the LMS Code integration tests: mock LM Studio server + real client layer.
# Prerequisite: mvn clean verify (needs the built plugin jar and the Tycho p2 cache).
set -euo pipefail
cd "$(dirname "$0")/.."

PLUGIN_JAR=$(ls com.lmscode.ai/target/com.lmscode.ai-*-SNAPSHOT.jar 2>/dev/null | head -1)
if [[ -z "${PLUGIN_JAR}" ]]; then
  echo "Plugin jar not found — run 'mvn clean verify' first." >&2
  exit 2
fi
GSON_JAR=com.lmscode.ai/lib/gson-2.10.1.jar

# Eclipse platform bundles from the Tycho p2 cache (needed only for class
# resolution of preference-related references; nothing Eclipse runs here).
ECLIPSE_CP=$(find ~/.m2/repository/p2 -name '*.jar' 2>/dev/null | tr '\n' ':' || true)

mkdir -p tests/bin
javac -cp "${PLUGIN_JAR}:${GSON_JAR}" -d tests/bin tests/src/LmsCodeIntegrationTest.java
java -cp "tests/bin:${PLUGIN_JAR}:${GSON_JAR}:${ECLIPSE_CP}" LmsCodeIntegrationTest
