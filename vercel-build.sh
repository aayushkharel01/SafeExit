#!/usr/bin/env bash
set -euo pipefail

./gradlew :composeApp:jsBrowserProductionWebpack --no-daemon

rm -rf vercel-output
mkdir -p vercel-output
cp composeApp/build/processedResources/js/main/index.html vercel-output/index.html
cp composeApp/build/processedResources/js/main/skiko.js vercel-output/skiko.js
cp composeApp/build/processedResources/js/main/skiko.mjs vercel-output/skiko.mjs
cp composeApp/build/processedResources/js/main/skiko.wasm vercel-output/skiko.wasm
cp composeApp/build/processedResources/js/main/skikod8.mjs vercel-output/skikod8.mjs
cp composeApp/build/kotlin-webpack/js/productionExecutable/composeApp.js vercel-output/composeApp.js
