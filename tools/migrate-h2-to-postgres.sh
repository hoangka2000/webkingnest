#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
H2_JAR="$(ls -1 "${HOME}/.m2/repository/com/h2database/h2/"*/h2-*.jar | tail -n 1)"
PG_JAR="$(ls -1 "${HOME}/.m2/repository/org/postgresql/postgresql/"*/postgresql-*.jar | tail -n 1)"

if [[ -z "${H2_JAR}" || -z "${PG_JAR}" ]]; then
  echo "Missing JDBC driver jars. Run: mvn dependency:resolve"
  exit 1
fi

CLASSPATH="${H2_JAR}:${PG_JAR}:${ROOT_DIR}/tools"

echo "Using H2 jar: ${H2_JAR}"
echo "Using PG jar: ${PG_JAR}"

javac -cp "${H2_JAR}:${PG_JAR}" "${ROOT_DIR}/tools/H2ToPostgresMigrator.java"
java -cp "${CLASSPATH}" H2ToPostgresMigrator

