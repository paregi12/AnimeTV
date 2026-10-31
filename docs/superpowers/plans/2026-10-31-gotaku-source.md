# Gotaku Source Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Gotaku (`gotaku.to`) as AnimeTV's 9th streaming source with full catalog browsing, AniList/MAL metadata sync, and native hardware-accelerated ExoPlayer video playback via an in-app NanoHTTPD streaming proxy.

**Architecture:** A lightweight local HTTP proxy (`GotakuProxy.java` on `127.0.0.1`) runs inside the Android app to rewrite HLS playlists and decrypt AES-CTR scrambled segment prefixes on the fly for ExoPlayer. The shared frontend (`m.js`) implements the Gotaku source adapter for search, catalog, metadata, and manifest handshakes.

**Tech Stack:** Java, Android Leanback, ExoPlayer, NanoHTTPD, Web Cryptography / Node crypto, Vanilla JS (`m.js`).

## Global Constraints
- Minimal comments. Only comment non-obvious logic.
- Short, simple commit messages. No bullet lists in commit body.
- Source index: 9 (`__SD == 9`).
- Local proxy binds strictly to `127.0.0.1`.

---

### Task 1: Enable NanoHTTPD Dependency

**Files:**
- Modify: `app/build.gradle:59`

**Interfaces:**
- Produces: `org.nanohttpd.protocols.http.NanoHTTPD` available to Android compilation.

- [ ] **Step 1: Uncomment NanoHTTPD in app/build.gradle**

In `app/build.gradle`, locate:
```groovy
//    implementation 'org.nanohttpd:nanohttpd:2.3.1'
```
and change it to:
```groovy
    implementation 'org.nanohttpd:nanohttpd:2.3.1'
```

- [ ] **Step 2: Commit**

```bash
git add app/build.gradle
git commit -m "Enable NanoHTTPD dependency in app/build.gradle"
```

---

### Task 2: Implement Java Cryptography Engine (`GotakuCrypto.java`)

**Files:**
- Create: `app/src/main/java/com/amarullz/androidtv/animetvjmto/utils/GotakuCrypto.java`

**Interfaces:**
- Produces:
  - `GotakuCrypto.hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length)` -> `byte[]`
  - `GotakuCrypto.decryptApi(byte[] body, String apiVersion, byte[] apiSeed, String hkdfInfo)` -> `String`
  - `GotakuCrypto.decryptManifest(String b64Body, byte[] nonce)` -> `String`
  - `GotakuCrypto.encryptManifest(String stamp, byte[] nonce)` -> `String`
  - `GotakuCrypto.segmentCounter(String url, boolean fullPath)` -> `byte[]`
  - `GotakuCrypto.decryptSegmentPrefix(byte[] data, byte[] keySeed, String url, int segmentBytes, long rangeStart)` -> `void`

- [ ] **Step 1: Write `GotakuCrypto.java`**

Implement HKDF-SHA256, AES-GCM decryption/encryption, Zlib decompression (InflaterInputStream), and AES-CTR keystream counter generation.

- [ ] **Step 2: Verify `GotakuCrypto.java` compilation and tests**

Run verification check on Java syntax and cryptographic operations.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/amarullz/androidtv/animetvjmto/utils/GotakuCrypto.java
git commit -m "Add GotakuCrypto cryptography engine"
```

---

### Task 3: Implement Native Streaming Proxy (`GotakuProxy.java`)

**Files:**
- Create: `app/src/main/java/com/amarullz/androidtv/animetvjmto/GotakuProxy.java`

**Interfaces:**
- Consumes: `GotakuCrypto`
- Produces:
  - `GotakuProxy.startServer()` -> starts NanoHTTPD on `127.0.0.1:0`
  - `GotakuProxy.stopServer()` -> stops NanoHTTPD
  - `GotakuProxy.getPort()` -> `int`
  - `GotakuProxy.createProxyPlaylistUrl(String m3u8Url, String keySeed, int segmentBytes)` -> `String`

- [ ] **Step 1: Write `GotakuProxy.java`**

Subclass `NanoHTTPD("127.0.0.1", 0)`.
Handle:
- `/proxy/playlist.m3u8`: Fetches remote m3u8, decrypts master playlist if encrypted, rewrites `.ts` chunk lines to `/proxy/segment.ts?url=...&key=...&sb=...`.
- `/proxy/segment.ts`: Fetches segment, applies `GotakuCrypto.decryptSegmentPrefix`, and streams MPEG-TS with Range header support.
- `/proxy/subtitles.vtt`: Proxies subtitle tracks.

- [ ] **Step 2: Verify compilation**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/amarullz/androidtv/animetvjmto/GotakuProxy.java
git commit -m "Add GotakuProxy NanoHTTPD streaming server"
```

---

### Task 4: Integrate Proxy Lifecycle in Android Activity (`MainActivity.java` & `AnimeView.java`)

**Files:**
- Modify: `app/src/main/java/com/amarullz/androidtv/animetvjmto/MainActivity.java`
- Modify: `app/src/main/java/com/amarullz/androidtv/animetvjmto/AnimeView.java`

**Interfaces:**
- Produces: `_JSAPI.getGotakuProxyPort()` returning the active local proxy port to WebView JavaScript.

- [ ] **Step 1: Instantiate GotakuProxy in `MainActivity.java`**

Start proxy on activity create/resume, stop proxy on destroy.

- [ ] **Step 2: Add `@JavascriptInterface getGotakuProxyPort()` to `AnimeView.java`**

Expose proxy port so `m.js` can construct `http://127.0.0.1:<port>/proxy/playlist.m3u8`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/amarullz/androidtv/animetvjmto/MainActivity.java app/src/main/java/com/amarullz/androidtv/animetvjmto/AnimeView.java
git commit -m "Integrate GotakuProxy lifecycle and JSAPI bridge"
```

---

### Task 5: Register Gotaku & Implement Catalog Adapter in `m.js`

**Files:**
- Modify: `app/src/main/assets/view/m.js`

**Interfaces:**
- Produces:
  - `__SOURCE_NAME`: includes `'Gotaku'` at index 8 (SD = 9)
  - `__SOURCE_DOMAINS`: includes `['gotaku.to']`
  - `const gotaku = { ... }` object implementing `getAnimeId`, `getFilterOrigin`, `getFilterUrl`, `recent_parse`, `getTooltip`.

- [ ] **Step 1: Register Source 9 in `m.js`**

Update `__SOURCE_NAME`, `__SOURCE_DOMAINS`, and declare `const __SD9 = (__SD == 9);`.

- [ ] **Step 2: Implement `gotaku` Catalog & Details Adapter**

Implement:
- `gotaku.getFilterUrl(...)`: builds query for `/api/v1/titles`.
- `gotaku.recent_parse(data)`: parses home screen title cards.
- `gotaku.getTooltip(id, cb, url)`: queries `/api/v1/titles/{id}` and maps `anilist_id` + `links.mal`.

- [ ] **Step 3: Wire into `_API` router in `m.js`**

Connect `_API.animeId`, `_API.filterorigin`, `_API.filterurl`, `_API.getTooltip` for `__SD9`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/view/m.js
git commit -m "Add Gotaku catalog and details adapter to m.js"
```

---

### Task 6: Implement Episode & Manifest Stream Resolver in `m.js`

**Files:**
- Modify: `app/src/main/assets/view/m.js`

**Interfaces:**
- Produces:
  - `gotaku.getView(url, cb)`: Resolves episode list and returns playable stream URL.
  - Integration with `_JSAPI.getGotakuProxyPort()` on Android to pass proxy URL to ExoPlayer.

- [ ] **Step 1: Implement `gotaku.getView`**

Fetch `/api/v1/titles/{id}/episodes` and `/api/v1/episodes/{epId}/embed`.
Extract `data-manifest-base` and `data-manifest-stamp` from embed HTML.
Execute `ManifestHandshake` via WebCrypto or local proxy bridge.

- [ ] **Step 2: Wire playback hand-off**

On Android: wrap CDN m3u8 through `http://127.0.0.1:{proxyPort}/proxy/playlist.m3u8?...`.
On Electron: pass stream URL directly with headers.
Pass `skip.intro` and `skip.outro` timestamps into AnimeTV player controls.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/view/m.js
git commit -m "Implement Gotaku episode and stream resolver in m.js"
```

---

### Task 7: End-to-End Verification

- [ ] **Step 1: Syntax & Lint Verification**

Verify no syntax errors in `m.js`, `GotakuProxy.java`, `GotakuCrypto.java`.

- [ ] **Step 2: Source Switching & Catalog Test**

Test source switching to Gotaku in AnimeTV interface, verify home screen rows and search results render correctly.

- [ ] **Step 3: Playback Test**

Verify episode click loads playlist, local proxy unscrambles TS segments, and ExoPlayer begins playback.
