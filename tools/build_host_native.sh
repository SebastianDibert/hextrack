#!/usr/bin/env bash
# Builds the native engine as a host (Linux/macOS) shared library for the JVM host tests.
set -euo pipefail
cd "$(dirname "$0")/.."
JH="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which java)")")")}"
OS_INC=linux; [[ "$(uname)" == "Darwin" ]] && OS_INC=darwin
mkdir -p build/host
g++ -std=c++17 -O2 -ffast-math -Wall -Wno-unused-parameter -fPIC -shared \
  -I"$JH/include" -I"$JH/include/$OS_INC" \
  app/src/main/cpp/jni_bridge.cpp app/src/main/cpp/engine.cpp app/src/main/cpp/voice.cpp app/src/main/cpp/dsp.cpp \
  -o build/host/libhextrak_host.so -lpthread
echo "built build/host/libhextrak_host.so"
