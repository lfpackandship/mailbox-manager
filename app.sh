#!/usr/bin/env bash
set -e

COMMAND="${1:-run}"

case "$COMMAND" in
  run)
    mvn -q clean javafx:run
    ;;
  build)
    mvn clean package
    ;;
  clean)
    mvn clean
    ;;
  *)
    echo "Usage: ./app.sh [run|build|clean]"
    exit 1
    ;;
esac

