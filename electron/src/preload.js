/*
 * Copyright 2024 amarullz.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *______________________________________________________________________________
 *
 * Filename    : animetv.js
 * Description : Node/electron preload js
 *
 */
const os = require("os");
const process = require("process");
const { remote, contextBridge, ipcRenderer } = require("electron");
const send = (m, a) => ipcRenderer.send(m, a);
const on = (m, c) => ipcRenderer.on(m, c);
const CryptoJS = require("crypto-js");
const crypto = require("crypto");
const zlib = require("zlib");
const https = require("https");

const GOTAKU_API_SEED_V8 = Buffer.from([
  169, 97, 13, 229, 137, 106, 199, 91, 124, 53, 130, 103, 235, 216, 204,
  41, 161, 207, 4, 157, 109, 106, 130, 5, 57, 176, 155, 55, 18, 148, 80, 128
]);
const GOTAKU_API_SEED_V10 = Buffer.from([
  30, 99, 206, 236, 176, 26, 228, 147, 55, 35, 152, 123, 6, 17, 126, 179,
  40, 181, 197, 210, 194, 90, 232, 35, 247, 20, 130, 43, 71, 117, 123, 82
]);
const GOTAKU_API_SEED = GOTAKU_API_SEED_V10;
const GOTAKU_MANIFEST_SEED = Buffer.from("458e5403c40a73332706df406193dd6b040a2ea97a6bb59b52b294a61006e6ef", "hex");

function gotakuB64UrlEncode(buf) {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function gotakuB64UrlDecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  return Buffer.from(str, 'base64');
}

function gotakuHkdfSha256(ikm, salt, info, length) {
  if (!salt || salt.length === 0) salt = Buffer.alloc(32);
  const prk = crypto.createHmac('sha256', salt).update(ikm).digest();
  let prev = Buffer.alloc(0);
  const buffers = [];
  let currentLen = 0;
  let i = 1;
  while (currentLen < length) {
    const hmac = crypto.createHmac('sha256', prk);
    hmac.update(prev);
    if (info && info.length > 0) hmac.update(info);
    hmac.update(Buffer.from([i]));
    prev = hmac.digest();
    buffers.push(prev);
    currentLen += prev.length;
    i++;
  }
  return Buffer.concat(buffers).subarray(0, length);
}

function gotakuDecryptApi(buf) {
  if (buf.length <= 29 || (buf[0] !== 10 && buf[0] !== 8)) return null;
  const iv = buf.subarray(1, 13);
  const ciphertext = buf.subarray(13, buf.length - 16);
  const tag = buf.subarray(buf.length - 16);
  const seed = (buf[0] === 8) ? GOTAKU_API_SEED_V8 : GOTAKU_API_SEED_V10;
  const key = gotakuHkdfSha256(seed, Buffer.alloc(0), Buffer.from("api-seal|v1"), 32);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  const compressed = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  return zlib.inflateSync(compressed).toString('utf8');
}

function gotakuHttpFetch(url, headers) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: headers || {} }, (res) => {
      const chunks = [];
      res.on('data', d => chunks.push(d));
      res.on('end', () => resolve({ status: res.statusCode, data: Buffer.concat(chunks) }));
    }).on('error', reject);
  });
}

function httpPostJson(url, postData, headers) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const data = Buffer.from(typeof postData === 'string' ? postData : JSON.stringify(postData));
    const req = https.request(u, {
      method: 'POST',
      headers: Object.assign({
        'Content-Type': 'application/json',
        'Content-Length': data.length,
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      }, headers || {})
    }, (res) => {
      const chunks = [];
      res.on('data', d => chunks.push(d));
      res.on('end', () => resolve({ status: res.statusCode, data: Buffer.concat(chunks).toString('utf8') }));
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function reanimeElectronResolveStream(embedUrl) {
  const htmlRes = await gotakuHttpFetch(embedUrl, {
    'Referer': 'https://flixcloud.cc/',
    'Origin': 'https://flixcloud.cc',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
  });
  const html = htmlRes.data.toString('utf8');
  const dataMatch = html.match(/type:\s*"data",\s*data:\s*(\{.*?\})\s*,\s*uses:/s);
  if (!dataMatch) return null;

  let s = dataMatch[1]
    .replace(/([{,]\s*)([\w_]+)(\s*:)/g, '$1"$2"$3')
    .replace(/,\s*([}\]])/g, '$1')
    .replace(/:\s*undefined\b/g, ': null');
  const embedObj = JSON.parse(s);

  const subtitles = (embedObj.subtitles || []).filter(sub => (sub.url || '').startsWith('https://')).map(sub => ({
    url: sub.url,
    lang: sub.language || 'Unknown',
    default: !!sub.default
  }));

  const skip = {};
  if (embedObj.intro_chapter) {
    skip.intro = [embedObj.intro_chapter.start || 0, embedObj.intro_chapter.end || 0];
  }
  if (embedObj.outro_chapter) {
    skip.outro = [embedObj.outro_chapter.start || 0, embedObj.outro_chapter.end || 0];
  }

  delete embedObj.subtitles;
  delete embedObj.intro_chapter;
  delete embedObj.outro_chapter;
  delete embedObj.chapters;

  const tokenRes = await httpPostJson('https://enc-dec.app/api/dec-flixcloud?type=token', { data: embedObj }, {
    'Referer': 'https://enc-dec.app/'
  });
  const tokenDto = JSON.parse(tokenRes.data);
  if (tokenDto.status !== 200 || !tokenDto.result) return null;

  const m3u8Res = await gotakuHttpFetch('https://flixcloud.cc/api/m3u8/' + tokenDto.result.token, {
    'Referer': 'https://flixcloud.cc/',
    'Origin': 'https://flixcloud.cc',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
  });
  const m3u8Dto = JSON.parse(m3u8Res.data.toString('utf8'));

  const streamRes = await httpPostJson('https://enc-dec.app/api/dec-flixcloud?type=stream', {
    data: {
      context: tokenDto.result.context,
      stream_response: m3u8Dto
    }
  }, {
    'Referer': 'https://enc-dec.app/'
  });
  const streamDto = JSON.parse(streamRes.data);
  if (streamDto.status !== 200 || !streamDto.result) return null;

  const streamUrl = streamDto.result.stream;
  const wPayload = (streamDto.result.context && streamDto.result.context.w_payload) ? streamDto.result.context.w_payload : '';
  const parsedManifestUrl = 'https://enc-dec.app/api/parse-flixcloud?url=' + encodeURIComponent(streamUrl).replace(/\+/g, '%20') + '&w_payload=' + encodeURIComponent(wPayload).replace(/\+/g, '%20');

  return JSON.stringify({
    source: streamUrl,
    proxy_url: parsedManifestUrl,
    subtitles: subtitles,
    skip: skip
  });
}

async function gotakuElectronFetchAndDecrypt(url) {
  const res = await gotakuHttpFetch(url, {
    'Referer': 'https://gotaku.to/',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'application/json, text/plain, */*'
  });
  return gotakuDecryptApi(res.data);
}

async function gotakuElectronResolveStream(embedUrl) {
  if (embedUrl.startsWith('//')) embedUrl = 'https:' + embedUrl;
  else if (embedUrl.startsWith('/')) embedUrl = 'https://gotaku.to' + embedUrl;

  const htmlRes = await gotakuHttpFetch(embedUrl, {
    'Referer': 'https://gotaku.to/',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
  });
  const html = htmlRes.data.toString('utf8');
  const baseMatch = html.match(/data-manifest-base="([^"]+)"/);
  const stampMatch = html.match(/data-manifest-stamp="([^"]+)"/);
  if (!baseMatch || !stampMatch) return null;

  const manifestBase = baseMatch[1];
  const stamp = stampMatch[1];
  const nonce = crypto.randomBytes(16);
  const payload = JSON.stringify({ nonce: gotakuB64UrlEncode(nonce), stamp: stamp });
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', GOTAKU_MANIFEST_SEED, iv);
  const ct = Buffer.concat([cipher.update(payload, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  const reqPath = gotakuB64UrlEncode(Buffer.concat([iv, ct, tag]));

  const manifestRes = await gotakuHttpFetch(manifestBase + '/' + reqPath, {
    'Referer': embedUrl,
    'Accept': 'text/plain',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
  });
  const encManifest = manifestRes.data.toString('utf8').trim();
  const blob = gotakuB64UrlDecode(encManifest);
  const mIv = blob.subarray(0, 12);
  const mCt = blob.subarray(12, blob.length - 16);
  const mTag = blob.subarray(blob.length - 16);
  const mKey = gotakuHkdfSha256(GOTAKU_MANIFEST_SEED, nonce, Buffer.from("manifest-resp|v1"), 32);

  const mDecipher = crypto.createDecipheriv('aes-256-gcm', mKey, mIv);
  mDecipher.setAuthTag(mTag);
  const mPlain = Buffer.concat([mDecipher.update(mCt), mDecipher.final()]).toString('utf8');
  return mPlain;
}

/* Versions */
var versions;
var isOnDownloadUpdate = false;
try{
  versions=require("./version.js");
}catch(e){
  versions=require("../ver.js");
}

const vars={
  vars:{},
  config:{},
  window:null
};

async function invoke(c,d){
  return await ipcRenderer.invoke(c,d).then();
}

/* Config Loader */
(async function(){
  vars.config =ipcRenderer.sendSync('config-load','');
  vars.vars =ipcRenderer.sendSync('vars-load','');
})();

const api={
  /* app functions */
  isElectron(){ return true; },
  appQuit() {
    send("main", "quit");
  },
  reloadHome(){
    send("main", "home");
  },
  toggleFullscreen(){
    send("main", "toggle-fullscreen");
  },
  getFullscreen(){
    return vars.vars.fullscreen;
  },
  fullscreenCb(s){
    vars.vars.fullscreen=s;
  },
  varSet(k,v){ 
    vars.vars[k]=JSON.parse(JSON.stringify(v));
    invoke('vars-save',JSON.stringify(vars.vars));
  },
  varGet(k,def){
    if (k in vars.vars){
      return JSON.parse(JSON.stringify(vars.vars[k]));
    }
    return def;
  },

  /* videos */
  videoSetUrl(url){
    // console.warn("SET VIDEO: "+url);
    // invoke('set-url',url);
  },
  videoSetPosition(pos){},
  videoPlay(play){},
  videoGetDuration(){ return 0 },
  videoGetPosition(){ return 0 },
  videoBufferPercent() { return 0 },
  videoIsPlaying(){ return false },
  videoTracks(){},
  videoSetSpeed(speed){},
  videoSupportSpeed(){ return true },
  videoSetScale(type){},
  setStreamType(type, clean){
    vars.vars.stream_type=type;
    invoke('vars-save',JSON.stringify(vars.vars));
  },
  setStreamServer(mirror, clean){
    vars.vars.stream_server=mirror;
    invoke('vars-save',JSON.stringify(vars.vars));
  },
  getStreamType(){ return vars.vars.stream_type; },

  /* storage */
  storeGet(key, def){
    if (key in vars.config){
      return vars.config[key];
    }
    return def;
  },
  storeSet(key, value){
    vars.config[key]=value;
    invoke('config-save',JSON.stringify(vars.config));
  },
  storeDel(key){
    if (key in vars.config){
      delete vars.config[key];
      invoke('config-save',JSON.stringify(vars.config));
    }
  },

  /* profiles */
  profileGetSel(){ return vars.vars.profile_sel; },
  profileGetPrefix(){ return vars.vars.profile_prefix; },
  profileSetSel(v){ 
    vars.vars.profile_sel=v; 
    invoke('vars-save',JSON.stringify(vars.vars));
  },
  profileSetPrefix(v){
    vars.vars.profile_prefix=v;
    invoke('vars-save',JSON.stringify(vars.vars));
  },

  /* network functions */
  dns(){ return vars.vars.dns[vars.vars.sd]; },
  flix_dns(){ return "api.animeflix.dev"},
  getSd(){ return vars.vars.sd; },
  setSd(s){
    vars.vars.sd=s;
    vars.config.__sd=s;
    invoke('vars-save',JSON.stringify(vars.vars));
    invoke('config-save',JSON.stringify(vars.config));
  },
  setSdomain(s){
    vars.vars.sd_domain=s;
    invoke('vars-save',JSON.stringify(vars.vars));
  },
  getSdomain(){ return vars.vars.sd_domain; },

  /* networks config */
  setProgCache(v){},
  setDOH(v){},
  setHttpClient(v){
    vars.vars.httpclient=v;
    invoke('vars-save',JSON.stringify(vars.vars));
  },
  getCacheSz(){ return 100; },
  setCacheSz(s){},
  clearCache(){},

  /* versioning */
  dnsver(){ return 'Electron '+process.versions.electron; },
  getVersion(type){
    if (type==0)
      return versions.version;
    else if (type==2){
      if (versions.code){
        return versions.code;
      }
      return "500";
    }
    return versions.build+"/"+os.platform()+"-"+os.arch();
  },

  /* encryptions */
  sha1sum(value) { return CryptoJS.enc.Hex.stringify(CryptoJS.SHA1(value)); },
  aesDec(cipherText, key, ivhex) {
    var val=CryptoJS.AES.decrypt(
      {
        ciphertext: CryptoJS.enc.Base64.parse(cipherText),
        salt: ""
      }, 
      CryptoJS.enc.Utf8.parse(key),{
      iv:CryptoJS.enc.Hex.parse(ivhex)
    });
    return val.toString(CryptoJS.enc.Utf8);
  },
  vidEncode(vid, k1, k2){ return ""; },

  /* android specific */
  playClick(){},
  showToast(txt){},
  setBrightness(b){ return 50; },
  setVolume(b){ return 50; },

  /* misc */
  setLandscape(stat){},
  getHaveTouchscreen(){ return true },
  
  /* android will not implemented */
  getSysHeight(nav){ return 0 },
  haveMic(checkSpeech){ return false },
  installApk(url,isNightly){ 
    console.log("UPDATE URL: "+url);
    isOnDownloadUpdate=true;
    invoke('download-update',url);
  },
  isOnUpdate(){ return isOnDownloadUpdate },
  showIme(show){},
  getArg(name){ return ""; },
  clearArg(){},
  playNextPos(pos, duration){},
  playNextClear(){},
  playNextRegister(){},
  playNextMeta(t,d,p,u,i,sd){},
  videoSetMeta(title,artist,poster){
    var bd={
      title: title,
      artist: artist,
      album: title+" - "+artist,
      artwork: [
          { src: poster }
      ]
    };
    invoke('exec-js',
      'navigator.mediaSession.metadata=new MediaMetadata('+JSON.stringify(bd)+')'
    );
  },
  videoHaveNP(n,p){},
  voiceSearch(){},
  voiceClose(){},
  asyncPrompt(message, cbnum){},


  getGotakuProxyPort(){ return 0; },
  getGotakuProxyUrl(u, k, s){ return u; },
  gotakuFetchAndDecrypt(url) { return null; },
  gotakuFetchAndDecryptAsync(url, cbnum) {
    gotakuElectronFetchAndDecrypt(url).then(res => {
      if (window._gotakuCb) window._gotakuCb(cbnum, res);
    }).catch(() => {
      if (window._gotakuCb) window._gotakuCb(cbnum, null);
    });
  },
  gotakuResolveStream(embedUrl) { return null; },
  gotakuResolveStreamAsync(embedUrl, cbnum) {
    gotakuElectronResolveStream(embedUrl).then(res => {
      if (window._gotakuCb) window._gotakuCb(cbnum, res);
    }).catch(() => {
      if (window._gotakuCb) window._gotakuCb(cbnum, null);
    });
  },

  reanimeResolveStream(embedUrl) { return null; },
  reanimeResolveStreamAsync(embedUrl, cbnum) {
    reanimeElectronResolveStream(embedUrl).then(res => {
      if (window._reanimeCb) window._reanimeCb(cbnum, res);
    }).catch(() => {
      if (window._reanimeCb) window._reanimeCb(cbnum, null);
    });
  },

  /* will not implemented */
  malLogin(){},
  checkUpdate(){},

  /* logcat debug */
  logcatIsEnabled() { return false; },
  logcatSetEnabled(v) {},
  logcatGetLogs(l) { return "Desktop logging: inspect terminal output or Electron DevTools console."; },
  logcatGetPath() { return "N/A (Desktop)"; },
  logcatClear() {},
  logcatCopy() {},

  /* may supported */
  openIntentUri(u){
    invoke('intent-start',u);
  },
  
};

/* Register Client API */
contextBridge.exposeInMainWorld("_JSAPI", api);