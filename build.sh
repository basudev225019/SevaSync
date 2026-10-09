#!/usr/bin/env bash
# Builds target/sevasync.jar with the plain JDK (no Maven needed).  Usage: ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
rm -rf target/classes && mkdir -p target/classes
javac --release 17 -Xlint:all -d target/classes $(find src/main/java -name '*.java')
jar --create --file target/sevasync.jar --main-class com.sevasync.SevaSyncApp -C target/classes .
echo "Built target/sevasync.jar"
echo "  console : java -jar target/sevasync.jar [--demo]"
echo "  REST API: java -jar target/sevasync.jar serve --port 8080 [--demo]"
