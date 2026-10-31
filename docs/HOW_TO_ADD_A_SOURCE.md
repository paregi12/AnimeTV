# How to Add a New Source in AnimeTV

This guide provides a comprehensive, step-by-step walkthrough for adding a new anime streaming source to AnimeTV. It covers the complete architecture across both Android TV (Java + WebView + ExoPlayer) and Desktop (Electron).

---

## 1. System Architecture Overview

AnimeTV uses a hybrid runtime architecture:
- **UI & Catalog Layer**: Single-Page Application running inside an Android WebView (`app/src/main/assets/view/m.js`). It manages search, catalogue browsing, episode selection, and playback controls.
- **Native Host Layer**: Android Java (`AnimeView.java`, `MainActivity.java`) providing HTTP clients, DoH (DNS-over-HTTPS), secure keystores, and ExoPlayer.
- **Bridge Layer**: `@JavascriptInterface` bridge (`_JSAPI`) connecting JavaScript to Java.
- **Proxy Layer (Optional)**: A local loopback HTTP proxy server (e.g., `GotakuProxy.java`) running on `127.0.0.1` to decrypt or descramble proprietary HLS streams, inject headers, or descramble MPEG-TS packets before feeding them into ExoPlayer.
- **Desktop Runtime**: Electron (`electron/src/preload.js`, `electron/src/libs/common.js`) mirroring the native bridge.

```
+-------------------------------------------------------------+
|                     AnimeTV UI (m.js)                       |
|   - Catalog & Search Browsing   - Episode List & Tooltips   |
|   - Audio (Sub/Dub) Selection   - Player Controls & OSD     |
+------------------------------+------------------------------+
                               |
                   _JSAPI (Bridge Layer)
                               |
       +-----------------------+-----------------------+
       |                                               |
+------v-----------------------+       +---------------v------+
| Android Host (AnimeView.java)|       | Electron (preload.js)|
| - DoH & Native Network Calls |       | - Node.js fetch &    |
| - Crypto Helpers (AES, HKDF) |       |   crypto libraries   |
+--------------+---------------+       +----------------------+
               |
+--------------v---------------+
| Local HLS Proxy (If needed)  |
| - NanoHTTPD / ServerSocket   |
| - MPEG-TS descrambling       |
| - Playlist URL rewriting     |
+--------------+---------------+
               |
+--------------v---------------+
|     Native Media Player      |
| ExoPlayer (Android) / HTML5  |
+------------------------------+
```

---

## 2. Source Addition Workflow Checklist

Adding a new source involves 5 key steps:
1. **Assign Source ID & Register Domains** (Java, JavaScript, Electron).
2. **Implement Source Adapter in `m.js`** (`recent_parse`, `getTooltip`, `getView`, `loadVideo`, `getFilterUrl`).
3. **Register Source in `m.js` Dispatchers** (Search, Filter, View, Tooltips, Playback).
4. **Implement Native Cryptography / Local Proxy (if needed)** (Java & Electron).
5. **Verify Audio Tracks (Sub/Dub) and Episode Navigation**.

---

## 3. Step 1: Assign Source ID & Register Domains

Each source in AnimeTV is identified by an integer index (`__SD`):
- `1`: AnimeKAI
- `2`: Anix (Legacy)
- `3` & `4`: Aniwatch / HiAnime
- `5`: Animeflix
- `6`: KickAss (Kaas)
- `7`: Gojo
- `8`: Miruro
- `9`: Gotaku
- `10+`: Next New Source (e.g. `10`)

### A. Update `app/src/main/assets/view/m.js`

1. Define the source constant at the top of `m.js`:
```javascript
const __SD10 = (__SD == 10);
```

2. Add the source name to `__SOURCE_NAME`:
```javascript
const __SOURCE_NAME = [
  'AnimeKAI', 'Anix', 'Aniwatch', 'Aniwatch', 'Animeflix', 'KickAss', 'Gojo', 'Miruro', 'Gotaku', 'NewSource'
];
```

3. Add the primary and fallback domains to `__SOURCE_DOMAINS`:
```javascript
const __SOURCE_DOMAINS = [
  ['animekai.to', 'anikai.to', 'animekai.fo'],
  ['anix.to'],
  ['aniwatchtv.to'],
  ['aniwatchtv.to'],
  ['animeflix.live'],
  ['kaa.lt'],
  ['api.gojo.wtf'],
  ['www.miruro.tv'],
  ['gotaku.to'],
  ['newsource.to', 'newsource.is'] // Index 9 (Source 10)
];
```

4. Add domain health check logic in `SD_CHECK_DOMAIN`:
```javascript
function SD_CHECK_DOMAIN(sd, cb) {
  var sm = sd - 1;
  if (sm < 0 || sd > 10) return false;
  ...
  else if (sd == 10) {
    chk_url = '/api/v1/health';
    chk_json = 'status';
  }
}
```

### B. Update `app/src/main/java/com/amarullz/androidtv/animetvjmto/Conf.java`

Add the primary domain to `SOURCE_DOMAINS`:
```java
public static String[] SOURCE_DOMAINS = {
    "animekai.to", /* default */
    "animekai.to",
    "anix.to",
    "aniwatchtv.to",
    "aniwatchtv.to",
    "animeflix.live", "kaa.lt", "api.gojo.wtf", "www.miruro.tv",
    "gotaku.to",
    "newsource.to" // Source 10
};
```

### C. Update `electron/src/libs/common.js`

Add the domain to the DNS resolution list:
```javascript
exports.DNS_ALL = [
  ...
  "gotaku.to",
  "newsource.to"
];
```

---

## 4. Step 2: Implement Source Adapter in `m.js`

Create the source object module in `app/src/main/assets/view/m.js`. The object must implement the following core interface:

```javascript
var newsource = {
  dns: "newsource.to",
  cache: {},
  subtitle_origin: {
    "Origin": "https://newsource.to",
    "Referer": "https://newsource.to/"
  },

  // Map AnimeTV genre slugs to source genre IDs / parameters
  genres: {
    "action": "1",
    "adventure": "2",
    "comedy": "3",
    "drama": "4",
    "fantasy": "5",
    "horror": "6",
    "romance": "7",
    "sci-fi": "8"
  },

  getFilterOrigin: function() {
    return {
      "Origin": "https://" + (newsource.dns),
      "Referer": "https://" + (newsource.dns) + "/"
    };
  },

  getAnimeId: function(url) {
    if (!url) return null;
    var parts = url.split('#')[0].split('/');
    return parts[parts.length - 1];
  },

  // 1. Build Search / Catalog URL
  getFilterUrl: function(q, genres, sort, page, ses, year) {
    var params = [];
    if (page && page > 1) params.push("page=" + page);
    if (q) params.push("q=" + enc(q));
    if (genres && genres.length) {
      for (var i = 0; i < genres.length; i++) {
        params.push("genre[]=" + enc(genres[i]));
      }
    }
    if (year) params.push("year=" + enc(year));
    if (ses) params.push("season=" + enc((ses + '').toUpperCase()));
    
    var d = __SD_DOMAIN ? __SD_DOMAIN : newsource.dns;
    return "/__proxy/https://" + d + "/api/v1/search?" + params.join("&");
  },

  // 2. Parse Catalog / Search JSON or HTML into AnimeTV card items
  recent_parse: function(v) {
    var rd = [];
    try {
      var t = (typeof v === 'string') ? JSON.parse(v) : v;
      var list = t.data || (Array.isArray(t) ? t : []);
      for (var i = 0; i < list.length; i++) {
        var u = list[i];
        var d = {};
        d.url = "/title/" + u.id;
        d.title = u.name || u.title;
        d.title_jp = u.title_jp || u.name;
        d.tip = u.id;
        d.poster = u.poster_url || u.image;
        d.epavail = u.episodes_sub || 0;
        d.epdub = u.episodes_dub || 0;
        d.ep = d.epavail || d.epdub || u.total_episodes || 0;
        d.type = u.format || "TV";
        d.rating = u.score ? (u.score + "") : "";
        d.adult = !!u.is_adult;
        rd.push(d);
      }
    } catch (e) {
      console.log("ERR newsource.recent_parse: " + e);
    }
    return rd;
  },

  // 3. Tooltip / Anime Details Popup
  getTooltip: function(id, cb, url, isview) {
    if (!id && url) id = newsource.getAnimeId(url);
    if (!id) return;
    if (id in newsource.cache) {
      var cached = newsource.cache[id];
      requestAnimationFrame(function() { cb(JSON.parse(cached)); });
      return;
    }
    var d = __SD_DOMAIN ? __SD_DOMAIN : newsource.dns;
    $ap("https://" + d + "/api/v1/titles/" + id, function(r) {
      if (r.ok) {
        try {
          var res = JSON.parse(r.responseText);
          var m = res.data;
          var genreArr = [];
          var genreStr = "";
          if (m.genres) {
            genreArr = m.genres.map(function(g) { return { name: g.name, val: g.id }; });
            genreStr = m.genres.map(function(g) { return g.name; }).join(", ");
          }
          var o = {
            url: "/title/" + id,
            title: m.name,
            title_jp: m.name_jp || m.name,
            synopsis: m.synopsis || "",
            genres: genreArr,
            genre: genreStr,
            quality: null,
            ep: (m.episodes ? m.episodes.total : 0),
            rating: m.score ? (m.score + "") : "",
            ttid: id,
            poster: m.poster_url,
            banner: m.banner_url || null,
            tip: id,
            anilistId: m.anilist_id || null,
            malId: m.mal_id || null
          };
          newsource.cache[id] = JSON.stringify(o);
          cb(o);
          return;
        } catch (e) {}
      }
      cb(null);
    }, newsource.getFilterOrigin());
  },

  // 4. View & Episodes Loader
  getView: function(url, f) {
    var uid = ++_API.viewid;
    var ux = url.split('#');
    var id = newsource.getAnimeId(ux[0]);
    var epTarget = (ux.length == 2 && ux[1]) ? parseInt(ux[1]) : 1;

    var tipData = null;
    var epData = null;
    var finished = false;

    function finish() {
      if (finished || !tipData || !epData) return;
      finished = true;

      var epList = [];
      var episodes = epData.data || [];
      var dubCount = 0;

      for (var i = 0; i < episodes.length; i++) {
        var ep = episodes[i];
        var epNum = ep.number || (i + 1);
        var hasSub = (ep.sub !== undefined) ? !!ep.sub : true;
        var hasDub = !!ep.dub;
        if (hasDub) dubCount++;

        var oe = {
          ep: epNum,
          url: "/title/" + id + "#" + epNum,
          active: (epTarget == epNum),
          filler: !!ep.is_filler,
          title: ep.title ? ("Episode " + epNum + " - " + ep.title) : ("Episode " + epNum),
          epId: ep.id,
          has_sub: hasSub,
          has_dub: hasDub,
          streams: []
        };
        if (hasSub) oe.streams.push({ provider: 'NewSource Sub', id: ep.id });
        if (hasDub) {
          oe.streams.push({ provider: 'NewSource Dub', id: ep.id });
          oe.dub = oe.streams.length; // Truthy for microphone badge
        }
        epList.push(oe);
      }

      var activeIdx = 0;
      for (var j = 0; j < epList.length; j++) {
        if (epList[j].active) { activeIdx = j; break; }
      }
      if (epList.length > 0) epList[activeIdx].active = true;

      var o = {
        idMal: tipData.malId,
        anilistId: tipData.anilistId,
        title: tipData.title,
        title_jp: tipData.title_jp,
        synopsis: tipData.synopsis,
        genres: tipData.genres,
        banner: tipData.banner,
        rating: tipData.rating,
        ttid: id,
        url: "/title/" + id,
        poster: tipData.poster,
        status: true,
        epavail: epList.length,
        epdub: dubCount,
        type: tipData.type || "TV",
        ep: epList,
        epactive: activeIdx,
        servers: { dub: [], sub: [], softsub: [] },
        streamtype: "sub",
        stream_url: {}
      };

      if (epList.length > 0) {
        var currEp = epList[activeIdx];
        if (currEp.has_sub) {
          o.servers.sub.push(pb.serverobj('NewSource Sub', 0));
          o.stream_url.hard = currEp.epId;
        }
        if (currEp.has_dub) {
          o.servers.dub.push(pb.serverobj('NewSource Dub', 0));
          o.stream_url.dub = currEp.epId;
        }

        var preferDub = (_API.currentStreamType == 2) || (pb.cfg_data && pb.cfg_data.dubaudio && !_API.currentStreamTypeValue);
        if (preferDub && currEp.has_dub) {
          o.streamtype = "dub";
          o.playingStreamType = 2;
        } else if (currEp.has_sub) {
          o.streamtype = "sub";
          o.playingStreamType = 0;
        } else if (currEp.has_dub) {
          o.streamtype = "dub";
          o.playingStreamType = 2;
        }
      }

      f(o, uid);
    }

    newsource.getTooltip(id, function(tip) {
      tipData = tip || { title: id, genres: [] };
      finish();
    }, url, 1);

    var d = __SD_DOMAIN || newsource.dns;
    $ap("https://" + d + "/api/v1/titles/" + id + "/episodes", function(r) {
      epData = r.ok ? JSON.parse(r.responseText) : { data: [] };
      finish();
    }, newsource.getFilterOrigin());

    return uid;
  },

  // 5. Video Playback Resolver
  loadVideo: function(dt, f) {
    if (!dt || !dt.ep || dt.epactive === undefined) { f(null); return; }
    var oe = dt.ep[dt.epactive];
    if (!oe || !oe.epId) { f(null); return; }

    dt.servers = { dub: [], sub: [], softsub: [] };
    dt.stream_url = {};
    if (oe.has_sub) {
      dt.servers.sub.push(pb.serverobj('NewSource Sub', 0));
      dt.stream_url.hard = oe.epId;
    }
    if (oe.has_dub) {
      dt.servers.dub.push(pb.serverobj('NewSource Dub', 0));
      dt.stream_url.dub = oe.epId;
    }

    var preferDub = (_API.currentStreamType == 2) || (pb.cfg_data && pb.cfg_data.dubaudio && !_API.currentStreamTypeValue);
    var reqType = "sub";

    if (preferDub && oe.has_dub) {
      reqType = "dub";
      dt.streamtype = "dub";
      dt.playingStreamType = 2;
    } else if (oe.has_sub) {
      reqType = "sub";
      dt.streamtype = "sub";
      dt.playingStreamType = 0;
    } else if (oe.has_dub) {
      reqType = "dub";
      dt.streamtype = "dub";
      dt.playingStreamType = 2;
    }

    var d = __SD_DOMAIN || newsource.dns;
    var streamUrl = "https://" + d + "/api/v1/episodes/" + oe.epId + "/stream?type=" + reqType;

    $ap(streamUrl, function(r) {
      if (!r.ok) { f(null); return; }
      var res = JSON.parse(r.responseText);
      var v = {
        url: res.stream_url, // HLS m3u8 playlist or MP4 link
        subtitles: (res.subtitles || []).map(function(s) {
          return {
            u: s.file || s.url,
            d: (s.lang == 'English' ? 1 : 0),
            l: s.lang.toLowerCase(),
            i: s.lang.toLowerCase()
          };
        }),
        skip: res.skip || null // { intro: [start, end], outro: [start, end] }
      };
      f(v);
    }, newsource.getFilterOrigin());
  }
};
```

---

## 5. Step 3: Register Dispatch Hooks in `m.js`

Connect your source object to the main routing dispatchers in `app/src/main/assets/view/m.js`:

### 1. `_API.getAnimeId`
```javascript
else if (__SD10) {
  return newsource.getAnimeId(url);
}
```

### 2. `_API.filterorigin`
```javascript
else if (__SD10) {
  return newsource.getFilterOrigin();
}
```

### 3. `_API.filterurl`
```javascript
else if (__SD10) {
  return newsource.getFilterUrl(q, genres, sort, page, ses, year);
}
```

### 4. `_API.getView`
```javascript
else if (__SD10) {
  return newsource.getView(url, f);
}
```

### 5. `_API.getTooltip`
```javascript
else if (__SD10) {
  return newsource.getTooltip(id, cb, url, 0);
}
```

### 6. Subtitle Headers (`vtt.load`)
```javascript
else if (__SD10) {
  hdr = newsource.subtitle_origin;
}
```

### 7. Playback Initializer (`pb.init_video`)
```javascript
else if (__SD10) {
  newsource.loadVideo(pb.data, function(v) {
    if (!v || !v.url) {
      pb.playback_error('PLAYBACK ERROR', 'Loading video from source failed.');
      return;
    }
    pb.data.skip = [[0, 0], [0, 0]];
    if (v.skip) {
      if (v.skip.intro) pb.data.skip[0] = [v.skip.intro[0], v.skip.intro[1]];
      if (v.skip.outro) pb.data.skip[1] = [v.skip.outro[0], v.skip.outro[1]];
    }
    vtt.clear();
    pb.subtitles = [];
    if (v.subtitles && v.subtitles.length) {
      for (var i = 0; i < v.subtitles.length; i++) {
        var tk = v.subtitles[i];
        pb.subtitles.push({
          u: tk.u,
          d: tk.d,
          l: tk.l,
          i: tk.i
        });
      }
      vtt.init(pb.subtitles);
    }
    pb.updateStreamTypeInfo();
    pb.init_video_mp4upload(v.url);
    pb.cfg_update_el();
  });
}
```

### 8. Episode Click Navigation (`action_value`)
When episode URLs use relative paths (e.g., `/title/id#1` rather than `http...`), prefix them with `>` so `pb.action_handler` opens them:
```javascript
// In pb.init_episode:
if (__SD3 || __SD5 || __SD6 || __SD9 || __SD10) {
  action_value = '>' + d.url;
}

// In next episode handler:
if (__SD3 || __SD5 || __SD6 || __SD9 || __SD10) {
  action_value = '>' + epd.url;
}
```

### 9. Search & Catalog Parsers
```javascript
// In home.search.parse:
else if (__SD10) {
  rd = newsource.recent_parse(v);
}

// In home.search.genres:
else if (__SD10) {
  genrelist = newsource.genres;
}
```

---

## 6. Step 4: When & How to Implement a Native Proxy / Decryptor

### When is a Local Proxy Required?
A native local proxy is necessary if:
1. **Encrypted / Obfuscated HLS**: The video stream uses custom segment encryption (e.g. Gotaku's AES-CTR segment scrambling or custom XOR masks).
2. **Strict CDN Headers**: CDN blocks requests without strict `Referer`, `Origin`, or specific `User-Agent` headers that ExoPlayer cannot easily set per-segment.
3. **Dynamic Decryption**: AES keys or IVs must be derived on-the-fly using HKDF or tokens.

### Architecture of a Source Proxy Server
Refer to `GotakuProxy.java` as the primary reference implementation:
1. **Local Server**: Starts a lightweight HTTP server on `127.0.0.1:<port>` (using `ServerSocket` or `NanoHTTPD`).
2. **Lifecycle Hooks**:
   - `MainActivity.java`: Start proxy in `onCreate()` / stop in `onDestroy()`.
3. **Playlist Rewriter**:
   - Downloads the remote `.m3u8` playlist.
   - Rewrites URI lines: `https://cdn.example.com/seg1.ts` -> `http://127.0.0.1:<port>/seg?url=...&key=...`.
4. **Segment Streaming & Descrambling**:
   - Streams incoming bytes from the upstream CDN.
   - Applies decryption on-the-fly (e.g. skipping non-MPEG headers or decrypting AES-CTR blocks).
   - Validates MPEG-TS sync bytes (`0x47` every 188 bytes) to ensure zero corruption before passing to ExoPlayer.

### Java-JavaScript Bridge in `AnimeView.java`
Expose methods to `_JSAPI` using `@JavascriptInterface`:
```java
@JavascriptInterface
public void newSourceResolveAsync(String embedUrl, int cbnum) {
    new Thread(() -> {
        String result = resolveStream(embedUrl);
        mAnimeActivity.runOnUiThread(() -> {
            mAnimeView.evaluateJavascript("window._newSourceCb(" + cbnum + ", " + result + ");", null);
        });
    }).start();
}
```

---

## 7. Step 5: Testing & Verification Checklist

Before pushing code or building an APK, verify each of the following:

- [ ] **Syntax Check**: Run `node --check app/src/main/assets/view/m.js` to ensure no JS syntax errors.
- [ ] **Source Switching**: In settings, select the new source and ensure the home screen reloads with the source's catalogue.
- [ ] **Catalog Loading**: Home page displays featured anime, recent updates, and covers.
- [ ] **Search & Filters**:
  - Typing a search query returns matching results.
  - Selecting a genre filters the catalog correctly.
- [ ] **Anime Details**:
  - Clicking a title opens the details page with synopsis, genres, and cover banner.
- [ ] **Episode List**:
  - Episode numbers and titles appear.
  - Filler episodes show the filler tag.
  - Episodes with Dub show the `<c>mic</c>` badge.
- [ ] **Playback**:
  - Video stream loads and plays in ExoPlayer.
  - Subtitles appear and sync correctly.
  - Intro/outro skip buttons appear if skip timestamps are available.
- [ ] **Audio Selection (Sub/Dub)**:
  - If an episode has Dub, the stream selection menu exposes "Dub".
  - Switching to "Dub" reloads playback with English audio and updates the OSD to `<c>mic</c> DUB`.
  - Switching back to "Hardsub" restores the sub stream.
  - Global `dubaudio` toggle automatically selects Dub audio.
- [ ] **Episode Navigation**:
  - Clicking another episode in the episode list switches to that episode.
  - Next episode button triggers playback of the following episode.
- [ ] **CI / Build**:
  - Push changes to GitHub and trigger the debug APK build workflow in GitHub Actions.
  - **Never run `./gradlew` commands inside Termux**.
