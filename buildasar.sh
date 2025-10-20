#!/bin/sh
npm run build-linux && npm run build-linux-arm64 && mkdir -p ./electron/builds/update-asar && gzip < ./electron/builds/animetv-linux-x64/resources/app.asar > ./electron/builds/update-asar/animetv-`cat ./electron/builds/version.txt`.asar.gz
