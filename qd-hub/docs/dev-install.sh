#!/usr/bin/env bash
# Dev script: place fresh qd-hub into local dxfeed-bin folder for testing
set pipefail
script_dir=$(dirname "$(realpath "$0")")
SRC_DIR=$(realpath "$script_dir/../../")
TARGET_DIR=$(realpath "$SRC_DIR/../qd-hub-bin")

[ -d "$TARGET_DIR" ] || { echo "Invalid target dir $TARGET_DIR"; exit 1; }
[ -f "$SRC_DIR/qd-hub/pom.xml" ] || { echo "Invalid src dir $SRC_DIR"; exit 1; }

VERSION=$(cd "$SRC_DIR" && mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

echo "SRC_DIR=$SRC_DIR"
echo "TARGET_DIR=$TARGET_DIR"
echo "VERSION=$VERSION"
set -x

unzip -ju "$SRC_DIR/dxfeed-bin/target/dxfeed-bin-$VERSION-assembly.zip" lib/\* -d "$TARGET_DIR"
cp -v "$SRC_DIR/qd-hub/target/qd-hub-$VERSION.jar" "$TARGET_DIR/qd-hub.jar"



