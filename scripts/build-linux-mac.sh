#!/bin/bash

git clone https://github.com/dplyukhin/akka.git
cd akka
sbt publishLocal | tee build.info
AKKA_VERSION=$(grep -o '[a-zA-Z0-9+-.]*-SNAPSHOT' build.info | head -1)
echo "[build_script] AKKA_VERSION=$AKKA_VERSION"

cd ../..
sed -i "s/val akkaVersion =.*/val akkaVersion = \"$AKKA_VERSION\"/g" build.sbt

sbt publishLocal

rm -rf scripts/akka
echo "[build_script] DONE"
