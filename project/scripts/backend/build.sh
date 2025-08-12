#!/bin/sh -e

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 RUN_MODE"
  exit 1
fi

run_mode="$1"

scala_build() {
  sbt "Reload/update ; compile"
}


scala_test() {
  sbt "test"
}

case "$run_mode" in
  "build")
    scala_build
    ;;
  "test")
    scala_test
    ;;
esac
