#!/bin/bash

git clone https://github.com/dplyukhin/akka.git
cd akka
sbt publishLocal | tee build.info
AKKA_VERSION=$(grep -o '[a-zA-Z0-9+-.]*-SNAPSHOT' build.info | head -1)
echo "AKKA_VERSION=$AKKA_VERSION"

cd ../..
sed -i "s/val akkaVersion =.*/val akkaVersion = \"$AKKA_VERSION\"/g" build.sbt

sbt publishLocal

rm -rf scripts/akka
echo "DONE"
