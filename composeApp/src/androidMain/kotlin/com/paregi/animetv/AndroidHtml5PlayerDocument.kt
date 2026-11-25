package com.paregi.animetv

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.paregi.animetv.domain.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class Html5PlaybackBridge(
    @Volatile var allowedSubtitleUrls: Set<String>,
    private val headers: Map<String, String>,
    private val onState: (positionMs: Long, durationMs: Long, bufferedMs: Long, playing: Boolean, ended: Boolean) -> Unit,
    private val onError: ((String) -> Unit)? = null,
    private val onVideoInfo: ((height: Int, audioLanguages: Set<String>, videoHeights: List<Int>) -> Unit)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun update(
        positionSeconds: Double,
        durationSeconds: Double,
        bufferedSeconds: Double,
        paused: Boolean,
        ended: Boolean
    ) {
        val positionMs = (positionSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.times(1000.0) ?: 0.0).toLong()
        val durationMs = (durationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.times(1000.0) ?: 0.0).toLong()
        val bufferedMs = (bufferedSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.times(1000.0) ?: 0.0).toLong()
        mainHandler.post { onState(positionMs, durationMs, bufferedMs, !paused, ended) }
    }

    @JavascriptInterface
    fun error(message: String) {
        mainHandler.post { onError?.invoke(message) }
    }

    @JavascriptInterface
    fun videoInfo(height: Int, audioCsv: String, heightsCsv: String) {
        val audioSet = audioCsv.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val heights = heightsCsv.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        mainHandler.post { onVideoInfo?.invoke(height.coerceAtLeast(0), audioSet, heights) }
    }

    @JavascriptInterface
    fun fetchSubtitle(url: String): String {
        if (url !in allowedSubtitleUrls) return ""
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("")
    }
}

internal fun subtitleTracksJsonArray(subtitles: List<SubtitleTrack>): JSONArray = JSONArray().apply {
    subtitles.forEach { subtitle ->
        put(
            JSONObject()
                .put("src", subtitle.url)
                .put("srclang", subtitle.language)
                .put("label", subtitle.label)
                .put("kind", "subtitles")
                .put("default", subtitle.defaultSelected)
        )
    }
}

internal fun html5PlayerDocument(
    url: String,
    headers: Map<String, String>,
    subtitles: List<SubtitleTrack>,
    startPositionMs: Long,
    primary1Hex: String = "#440066",
    accent1Hex: String = "#CC55FF",
    fontSizeTier: Int = 2
): String {
    val headerObject = JSONObject().apply { headers.forEach { (name, value) -> put(name, value) } }
    val subtitleArray = subtitleTracksJsonArray(subtitles)
    val configuration = JSONObject()
        .put("src", url)
        .put("headers", headerObject)
        .put("subtitles", subtitleArray)
        .put("start", startPositionMs.coerceAtLeast(0) / 1000.0)
        .put("primary1", primary1Hex)
        .put("accent1", accent1Hex)
        .put("fontSizeTier", fontSizeTier.coerceIn(0, 3))
    return """
        <!doctype html>
        <html><head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>
            :root{--fs-7vw:.7vw;--fs-20vw:2vw;--fs-25vw:2.5vw;--fs-30vw:3vw}
            html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}
            .video-js{position:absolute;inset:0;width:100%;height:100%;background:#000}
            .video-js video{width:100%;height:100%;object-fit:contain}
            .scale-0 video{object-fit:contain!important}
            .scale-1 video{object-fit:cover!important}
            .scale-2 video{object-fit:fill!important}
            .vjs-control-bar,.vjs-big-play-button,.vjs-loading-spinner,.vjs-error-display{display:none!important}
            @font-face{font-family:AnimeFira;font-weight:300;src:url('subtitle-fonts/fira_sans_300.ttf') format('truetype')}
            @font-face{font-family:AnimeFira;font-weight:400;src:url('subtitle-fonts/fira_sans.ttf') format('truetype')}
            @font-face{font-family:AnimeFira;font-weight:700;src:url('subtitle-fonts/fira_sans_700.ttf') format('truetype')}
            @font-face{font-family:AnimeFira;font-weight:900;src:url('subtitle-fonts/fira_sans_900.ttf') format('truetype')}
            @font-face{font-family:AnimeItim;src:url('subtitle-fonts/itim.ttf') format('truetype')}
            @font-face{font-family:AnimeSalsa;src:url('subtitle-fonts/salsa.ttf') format('truetype')}
            @font-face{font-family:AnimeOutfit;font-weight:100 900;src:url('subtitle-fonts/outfit.ttf') format('truetype')}
            @font-face{font-family:AnimeMerriweather;font-weight:300 900;src:url('subtitle-fonts/merriweather.ttf') format('truetype')}
            @font-face{font-family:AnimeCantora;src:url('subtitle-fonts/cantora_one.ttf') format('truetype')}
            @font-face{font-family:AnimeOpenSans;font-weight:300 800;src:url('subtitle-fonts/open_sans.ttf') format('truetype')}
            @font-face{font-family:AnimeRuluko;src:url('subtitle-fonts/ruluko.ttf') format('truetype')}
            @font-face{font-family:AnimeComicNeue;font-weight:300;src:url('subtitle-fonts/comic_neue_300.ttf') format('truetype')}
            @font-face{font-family:AnimeComicNeue;font-weight:400;src:url('subtitle-fonts/comic_neue.ttf') format('truetype')}
            @font-face{font-family:AnimeComicNeue;font-weight:700;src:url('subtitle-fonts/comic_neue_700.ttf') format('truetype')}
            video::cue{font-family:var(--caption-font,sans-serif);font-size:var(--caption-size,3vw);line-height:var(--caption-line-height,1.15);font-weight:var(--caption-weight,400);color:var(--caption-color,#fff);background:var(--caption-bg,transparent);text-shadow:var(--caption-outline,none)}
          </style>
          <script>window.HELP_IMPROVE_VIDEOJS=false;</script>
          <script src="video.min.js"></script>
        </head><body><video-js id="player" playsinline></video-js>
        <script>
          (function(){
            var config=${configuration};
            var activeQuality=0;
            var activeSubtitleIndex=-1;
            var activeStyle=0;
            var activeFontTier=Math.max(0,Math.min(3,Number(config.fontSizeTier)||0));
            var pendingAudioLanguage='';
            var convertedTracks=[];
            var registeredTracks={};
            var sourceType='application/x-mpegURL';
            var lowerSrc=String(config.src||'').split('?')[0].split('#')[0].toLowerCase();
            if(/\.mp4$/i.test(lowerSrc)||lowerSrc.indexOf('/video.mp4')>-1){
              sourceType='video/mp4';
            }else if(/\.mpd$/i.test(lowerSrc)||String(config.src||'').indexOf('#dash')>-1){
              sourceType='application/dash+xml';
            }
            if(videojs.Vhs&&videojs.Vhs.xhr){
              videojs.Vhs.xhr.beforeRequest=function(options){
                options.headers=Object.assign({},options.headers||{},config.headers||{});
                return options;
              };
            }
            var video=videojs('player',{
              controls:false,autoplay:true,preload:'auto',responsive:false,
              sources:[{src:config.src,type:sourceType}],tracks:[]
            });
            var landscapeTierVars=[
              {f7:0.7,f20:2.0,f25:2.5,f30:3.0},
              {f7:0.9,f20:2.2,f25:2.7,f30:3.0},
              {f7:1.1,f20:2.4,f25:2.9,f30:3.4},
              {f7:1.2,f20:2.5,f25:3.0,f30:3.5}
            ];
            var portraitTierVars=[
              {f7:0.9,f20:2.2,f25:2.7,f30:3.0},
              {f7:1.1,f20:2.4,f25:2.9,f30:3.4},
              {f7:1.4,f20:2.7,f25:3.2,f30:3.7},
              {f7:1.6,f20:2.9,f25:3.4,f30:3.9}
            ];
            function isPortraitViewport(){
              return (window.matchMedia&&window.matchMedia('(orientation: portrait)').matches)||window.innerHeight>window.innerWidth;
            }
            function applyFontSizeTier(tier){
              activeFontTier=Math.max(0,Math.min(3,Number(tier)||0));
              var portrait=isPortraitViewport();
              var unit=portrait?'vh':'vw';
              var entry=(portrait?portraitTierVars:landscapeTierVars)[activeFontTier];
              var root=document.documentElement;
              root.style.setProperty('--fs-7vw',entry.f7+unit);
              root.style.setProperty('--fs-20vw',entry.f20+unit);
              root.style.setProperty('--fs-25vw',entry.f25+unit);
              root.style.setProperty('--fs-30vw',entry.f30+unit);
            }
            function normalizeVttTime(ts){
              var raw=String(ts||'').trim().replace(',','.');
              var parts=raw.split(':');
              while(parts.length<3)parts.unshift('00');
              var h=parts[0].padStart(2,'0');
              var m=parts[1].padStart(2,'0');
              var secParts=parts[2].split('.');
              var s=(secParts[0]||'00').padStart(2,'0');
              var ms=(secParts[1]||'000').padEnd(3,'0').slice(0,3);
              return h+':'+m+':'+s+'.'+ms;
            }
            function srtToVtt(raw){
              var lines=String(raw||'').replace(/\r/g,'').split('\n');
              var out=['WEBVTT',''];
              for(var i=0;i<lines.length;i++){
                var ln=lines[i];
                if(ln.indexOf('-->')>0){
                  var parts=ln.split('-->');
                  var start=normalizeVttTime(parts[0]);
                  var rightParts=parts[1].trim().split(/\s+/);
                  var end=normalizeVttTime(rightParts[0]);
                  var extra=rightParts.slice(1).join(' ');
                  out.push(start+' --> '+end+(extra?' '+extra:''));
                }else{
                  ln=ln.replace(/\{b\}/g,'<b>').replace(/\{\/b\}/g,'</b>')
                       .replace(/\{i\}/g,'<i>').replace(/\{\/i\}/g,'</i>')
                       .replace(/\{u\}/g,'<u>').replace(/\{\/u\}/g,'</u>')
                       .replace(/\{[\*=][^}]*\}/g,'');
                  out.push(ln);
                }
              }
              return out.join('\n');
            }
            function assToVtt(raw){
              var lines=String(raw||'').replace(/\r/g,'').split('\n');
              var out=['WEBVTT',''];
              var inEvents=false;
              var format=null;
              for(var i=0;i<lines.length;i++){
                var line=lines[i];
                var trimmed=line.trim();
                if(!inEvents){
                  if(trimmed.toLowerCase()==='[events]')inEvents=true;
                  continue;
                }
                if(trimmed.startsWith('[')){inEvents=false;continue;}
                var colon=line.indexOf(':');
                if(colon<0)continue;
                var key=line.slice(0,colon).trim().toLowerCase();
                var rest=line.slice(colon+1);
                if(key==='format'){
                  format=rest.toLowerCase().split(',').map(function(s){return s.trim();});
                }else if(key==='dialogue'&&format){
                  var vals=rest.trim().split(',');
                  var row={};
                  for(var j=0;j<format.length;j++){
                    row[format[j]]=(j===format.length-1)?vals.slice(j).join(','):(vals[j]||'');
                  }
                  if(row.text&&row.start&&row.end){
                    var txt=row.text.replace(/\{[^}]*\}/g,'').replace(/\\N/gi,'\n').trim();
                    if(txt&&txt.toLowerCase().indexOf('animeflix')<0){
                      out.push(normalizeVttTime(row.start)+' --> '+normalizeVttTime(row.end));
                      out.push(txt);
                      out.push('');
                    }
                  }
                }
              }
              return out.join('\n');
            }
            function convertToVttText(raw,src){
              var text=String(raw||'').trim();
              if(!text)return '';
              if(/^WEBVTT\b/i.test(text))return text;
              if(/\.ass(?:$|\?)|\.ssa(?:$|\?)/i.test(src)||/^\[Script Info\]/im.test(text)||/^\[Events\]/im.test(text)){
                return assToVtt(text);
              }
              return srtToVtt(text);
            }
            function applySubtitle(index){
              activeSubtitleIndex=Number(index);
              var targetTrack=(activeSubtitleIndex>=0)?(registeredTracks[activeSubtitleIndex]||null):null;
              var targetId=(activeSubtitleIndex>=0)?('animetv-sub-'+activeSubtitleIndex):null;
              var textTracks=video.textTracks();
              for(var i=0;i<textTracks.length;i++){
                var track=textTracks[i];
                if(track.kind!=='subtitles'&&track.kind!=='captions')continue;
                var matches=(activeSubtitleIndex>=0)&&((targetTrack&&track===targetTrack)||(targetId&&track.id===targetId));
                track.mode=matches?'showing':'disabled';
              }
            }
            function loadSingleSubtitle(sub,idx){
              if(!sub||!sub.src||registeredTracks[idx]||convertedTracks[idx])return;
              function registerVtt(vttText){
                var srcUrl=sub.src;
                if(vttText){
                  var blob=new Blob([vttText],{type:'text/vtt'});
                  srcUrl=URL.createObjectURL(blob);
                }
                convertedTracks[idx]=srcUrl;
                var trackEl=video.addRemoteTextTrack({
                  id:'animetv-sub-'+idx,src:srcUrl,srclang:sub.srclang,label:sub.label,kind:'subtitles',default:false
                },false);
                if(trackEl&&trackEl.track){
                  registeredTracks[idx]=trackEl.track;
                }
                applySubtitle(activeSubtitleIndex);
              }
              fetch(sub.src).then(function(r){
                if(!r.ok)throw new Error('HTTP '+r.status);
                return r.text();
              }).then(function(txt){
                registerVtt(convertToVttText(txt,sub.src));
              }).catch(function(){
                var fetched='';
                try{fetched=AnimeTV.fetchSubtitle(sub.src)||'';}catch(e){}
                registerVtt(fetched?convertToVttText(fetched,sub.src):'');
              });
            }
            function loadAllSubtitles(){
              var list=config.subtitles||[];
              list.forEach(function(sub,idx){
                loadSingleSubtitle(sub,idx);
              });
            }
            function syncSubtitles(raw){
              var next=Array.isArray(raw)?raw:JSON.parse(String(raw||'[]'));
              var current=config.subtitles||[];
              for(var i=current.length;i<next.length;i++){
                current.push(next[i]);
                loadSingleSubtitle(next[i],i);
              }
              config.subtitles=current;
            }
            function sortedQualityHeights(){
              var levels=video.qualityLevels?video.qualityLevels():null;
              if(!levels||!levels.length)return [];
              var seen={},res=[];
              for(var i=0;i<levels.length;i++){
                var h=Number(levels[i].height)||0;
                if(h>0&&!seen[h]){seen[h]=true;res.push(h);}
              }
              res.sort(function(a,b){return b-a;});
              return res;
            }
            function applyQuality(value){
              activeQuality=Math.max(0,Math.min(3,Number(value)||0));
              var levels=video.qualityLevels?video.qualityLevels():null;
              if(!levels||!levels.length)return;
              var res=sortedQualityHeights();
              if(activeQuality===0||res.length===0){
                for(var i=0;i<levels.length;i++)levels[i].enabled=true;
                return;
              }
              var targetHeight=res[Math.min(activeQuality-1,res.length-1)];
              for(var i=0;i<levels.length;i++){
                levels[i].enabled=(Number(levels[i].height)===targetHeight);
              }
            }
            function applyAudioLanguage(value){
              pendingAudioLanguage=String(value||'').toLowerCase().trim();
              var tracks=video.audioTracks?video.audioTracks():null;
              if(!tracks||!tracks.length)return;
              var req=pendingAudioLanguage;
              var matched=false;
              for(var i=0;i<tracks.length;i++){
                var tr=tracks[i];
                var lbl=(tr.label||'').toLowerCase().trim();
                var lang=(tr.language||'').toLowerCase().trim();
                var hit=req&&(lbl===req||lang===req||lbl.indexOf(req)>=0||lbl.slice(0,3)===req.slice(0,3)||lang.slice(0,3)===req.slice(0,3));
                tr.enabled=!!hit;
                if(hit)matched=true;
              }
              if(!matched&&tracks.length>0){
                for(var j=0;j<tracks.length;j++){
                  tracks[j].enabled=(j===0);
                }
              }
            }
            function collectAudioLangs(){
              var tracks=video.audioTracks?video.audioTracks():null;
              if(!tracks||!tracks.length)return '';
              var out=[];
              for(var i=0;i<tracks.length;i++){
                var t=tracks[i];
                var lang=(t.language||'').toLowerCase().trim();
                var lbl=(t.label||'').toLowerCase().trim().slice(0,3);
                if(lang)out.push(lang);
                if(lbl)out.push(lbl);
              }
              return out.join(',');
            }
            function reportVideoInfo(){
              try{
                var h=video.videoHeight?Number(video.videoHeight())||0:0;
                var heights=sortedQualityHeights().join(',');
                var audios=collectAudioLangs();
                AnimeTV.videoInfo(h,audios,heights);
              }catch(e){}
            }
            function styleDigit(value,divisor,count){
              var digit=Math.floor(Math.max(0,Number(value)||0)/divisor)%10;
              return digit<count?digit:0;
            }
            function parseHexRgb(hex){
              var clean=String(hex||'#ffffff').replace('#','');
              if(clean.length!==6)clean='ffffff';
              var rgb=parseInt(clean,16);
              return [(rgb>>16)&255,(rgb>>8)&255,rgb&255];
            }
            function fmtNum(v){
              return Number(v.toFixed(4)).toString();
            }
            function applySubtitleStyle(value){
              activeStyle=Number(value)||0;
              var root=document.documentElement;
              var fontIndex=styleDigit(activeStyle,1,10);
              var sizeIndex=styleDigit(activeStyle,10,4);
              var weightIndex=styleDigit(activeStyle,100,4);
              var backgroundIndex=styleDigit(activeStyle,1000,5);
              var colorIndex=styleDigit(activeStyle,10000,9);
              var opacityIndex=styleDigit(activeStyle,100000,9);
              var outlineIndex=styleDigit(activeStyle,1000000,5);
              var fonts=[
                'serif','AnimeFira','AnimeItim','AnimeSalsa','AnimeOutfit',
                'AnimeMerriweather','AnimeCantora','AnimeOpenSans','AnimeRuluko','AnimeComicNeue'
              ];
              var lineHeights=[1.15,1.15,1.15,1.1,1.1,1.15,1.1,1.1,1.1,1.2];
              var portrait=isPortraitViewport();
              var unit=portrait?'vh':'vw';
              var tierEntry=(portrait?portraitTierVars:landscapeTierVars)[activeFontTier];
              var sizes=[
                tierEntry.f30+unit,
                tierEntry.f25+unit,
                tierEntry.f20+unit,
                fmtNum(tierEntry.f30+tierEntry.f7/3)+unit
              ];
              var weights=['400','700','900','300'];
              var backgrounds=[0,.25,.5,.75,1];
              var colors=['#ffffff','#000000','#00ffff','#ffff00','#ff0000','#00ff00','#6666ff',config.primary1||'#440066',config.accent1||'#cc55ff'];
              var opacities=[1,.95,.9,.85,.8,.75,.7,.65,.6];
              var opacity=opacities[opacityIndex];
              var rgb=parseHexRgb(colors[colorIndex]);
              var color='rgba('+rgb[0]+','+rgb[1]+','+rgb[2]+','+opacity+')';
              var background='rgba(0,0,0,'+fmtNum(backgrounds[backgroundIndex]*opacity)+')';
              var outlineWidths=[0,0.2,0.4,0.6,0.8];
              var outlineW=outlineWidths[outlineIndex];
              var shadowRgb=(colorIndex===1||colorIndex===6||colorIndex===7)?'255,255,255':'0,0,0';
              var shadowColor='rgba('+shadowRgb+','+opacity+')';
              var shadows=[];
              if(outlineW===0){
                shadows.push('0 0 0.1vw '+shadowColor);
              }else{
                var steps=16;
                var radii=(outlineW>=0.4)?[outlineW,outlineW*0.5]:[outlineW];
                for(var ri=0;ri<radii.length;ri++){
                  var r=radii[ri];
                  for(var k=0;k<steps;k++){
                    var ang=(k*2*Math.PI)/steps;
                    var dx=fmtNum(Math.cos(ang)*r);
                    var dy=fmtNum(Math.sin(ang)*r);
                    shadows.push(dx+'vw '+dy+'vw 0 '+shadowColor);
                  }
                }
                var dropOffset=[0,0.15,0.15,0.2,0.25][outlineIndex];
                shadows.push(dropOffset+'vw '+dropOffset+'vw 0.05vw rgba(10,10,10,'+fmtNum(0.5*opacity)+')');
              }
              root.style.setProperty('--caption-font',fonts[fontIndex]);
              root.style.setProperty('--caption-size',sizes[sizeIndex]);
              root.style.setProperty('--caption-line-height',String(lineHeights[fontIndex]));
              root.style.setProperty('--caption-weight',weights[weightIndex]);
              root.style.setProperty('--caption-color',color);
              root.style.setProperty('--caption-bg',background);
              root.style.setProperty('--caption-outline',shadows.length?shadows.join(','):'none');
            }
            window.animeTvCommand=function(command,value){
              try{
                if(command==='play')video.play();
                else if(command==='pause')video.pause();
                else if(command==='seek')video.currentTime(Number(value)||0);
                else if(command==='speed')video.playbackRate(Number(value)||1);
                else if(command==='quality')applyQuality(value);
                else if(command==='subtitle')applySubtitle(value);
                else if(command==='syncSubtitles')syncSubtitles(value);
                else if(command==='audio')applyAudioLanguage(value);
                else if(command==='scale'){
                  var fit=Number(value)===1?'cover':Number(value)===2?'fill':'contain';
                  document.documentElement.className='scale-'+(Number(value)||0);
                  var element=document.querySelector('video');if(element)element.style.objectFit=fit;
                }else if(command==='subtitleStyle'){
                  applySubtitleStyle(value);
                }else if(command==='fontSizeTier'){
                  applyFontSizeTier(value);
                  applySubtitleStyle(activeStyle);
                }else if(command==='themeColors'){
                  var parts=String(value||'').split(',');
                  if(parts[0])config.primary1=parts[0];
                  if(parts[1])config.accent1=parts[1];
                  applySubtitleStyle(activeStyle);
                }
              }catch(error){}
            };
            applyFontSizeTier(activeFontTier);
            applySubtitleStyle(activeStyle);
            window.addEventListener('resize',function(){
              applyFontSizeTier(activeFontTier);
              applySubtitleStyle(activeStyle);
            });
            if(video.textTracks&&video.textTracks()){
              video.textTracks().on('addtrack',function(ev){
                var t=ev&&ev.track;
                if(t&&(t.kind==='subtitles'||t.kind==='captions')){
                  applySubtitle(activeSubtitleIndex);
                }
              });
            }
            if(video.audioTracks&&video.audioTracks()){
              video.audioTracks().on('addtrack',function(){
                applyAudioLanguage(pendingAudioLanguage);
                reportVideoInfo();
              });
              video.audioTracks().on('change',reportVideoInfo);
            }
            loadAllSubtitles();
            video.on('loadedmetadata',function(){
              if(config.start>0)video.currentTime(config.start);
              applyQuality(activeQuality);
              if(pendingAudioLanguage)applyAudioLanguage(pendingAudioLanguage);
              reportVideoInfo();
            });
            video.on('loadeddata',function(){
              if(pendingAudioLanguage)applyAudioLanguage(pendingAudioLanguage);
              reportVideoInfo();
            });
            video.on('resize',reportVideoInfo);
            if(video.qualityLevels&&video.qualityLevels()){
              video.qualityLevels().on('addqualitylevel',function(){
                applyQuality(activeQuality);
                reportVideoInfo();
              });
            }
            video.on('error',function(){
              try{AnimeTV.error(video.error()?(video.error().message||'Playback error'):'Playback error');}catch(error){}
            });
            setInterval(function(){
              try{
                var bufEnd=video.bufferedEnd?video.bufferedEnd():0;
                AnimeTV.update(video.currentTime()||0,video.duration()||0,bufEnd||0,video.paused(),video.ended());
              }catch(error){}
            },400);
          })();
        </script></body></html>
    """.trimIndent()
}
