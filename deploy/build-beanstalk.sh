#!/usr/bin/env bash
# Builds one Elastic Beanstalk source bundle: the React app packaged inside the Spring Boot jar,
# plus the Procfile and .ebextensions. Output: backend/target/healthdekho-beanstalk.zip
#
# Tests are not run here (they need the test database); run `mvn test` and `npm test` before releasing.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/backend/target/beanstalk"

echo "Building the web app"
(cd "$root/frontend" && npm ci && npm run build)

echo "Building the API jar with the web app inside"
(cd "$root/backend" && mvn -q -Pbundle-frontend -DskipTests clean package)

rm -rf "$out" && mkdir -p "$out"
cp "$root"/backend/target/insurance-backend-*.jar "$out/healthdekho.jar"
cp -r "$root/deploy/beanstalk/." "$out/"
(cd "$out" && rm -f ../healthdekho-beanstalk.zip && zip -qr ../healthdekho-beanstalk.zip .)

echo "Done: backend/target/healthdekho-beanstalk.zip"
