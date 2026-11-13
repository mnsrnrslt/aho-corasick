#!/usr/bin/env bash
# Usage: ./run.sh [test|bench|all]   (default: all)
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out
mkdir -p out
javac -d out $(find src -name '*.java')

case "${1:-all}" in
  test)  java -cp out ahocorasick.AhoCorasickTest ;;
  bench) java -Xms512m -Xmx512m -cp out ahocorasick.Benchmark ;;
  all)   java -cp out ahocorasick.AhoCorasickTest
         java -Xms512m -Xmx512m -cp out ahocorasick.Benchmark ;;
  *)     echo "Usage: $0 [test|bench|all]"; exit 1 ;;
esac
