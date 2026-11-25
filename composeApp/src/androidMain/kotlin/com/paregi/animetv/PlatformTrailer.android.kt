package com.paregi.animetv

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val TRAILER_BRIDGE_NAME = "AnimeTvTrailer"

private const val TRAILER_OBSERVER_JS = """
(function() {
  if (window.__animetvTrailerBound) return;
  window.__animetvTrailerBound = true;
  var lastState = '';
  function emit(state) {
    if (!state || state === lastState) return;
    lastState = state;
    try {
      if (window.$TRAILER_BRIDGE_NAME) {
        window.$TRAILER_BRIDGE_NAME.onState(state);
      }
    } catch (e) {}
  }
  function mapYtCode(code) {
    if (code === 1) return 'yt-play';
    if (code === 2) return 'yt-pause';
    if (code === 0) return 'yt-end';
    return null;
  }
  window.addEventListener('message', function(e) {
    try {
      var data = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
      if (!data) return;
      if (data.event === 'onStateChange') {
        emit(mapYtCode(data.info));
      } else if (data.event === 'infoDelivery' && data.info && typeof data.info.playerState === 'number') {
        emit(mapYtCode(data.info.playerState));
      } else if (data.event === 'onError') {
        emit('yt-error');
      } else if (data.vcmd) {
        emit(data.vcmd);
      }
    } catch (err) {}
  });
  var origPost = window.parent && window.parent.postMessage ? window.parent.postMessage.bind(window.parent) : null;
  if (window.parent === window && origPost) {
    window.postMessage = function(msg) {
      try {
        var data = typeof msg === 'string' ? JSON.parse(msg) : msg;
        if (data && data.event === 'onStateChange') emit(mapYtCode(data.info));
        else if (data && data.event === 'infoDelivery' && data.info && typeof data.info.playerState === 'number') {
          emit(mapYtCode(data.info.playerState));
        } else if (data && data.event === 'onError') emit('yt-error');
      } catch (err) {}
      return origPost(msg, '*');
    };
  }
  try {
    window.postMessage(JSON.stringify({ event: 'listening', id: 1, channel: 'widget' }), '*');
  } catch (e) {}
  var style = document.createElement('style');
  style.textContent = 'video{object-fit:cover!important;width:100%!important;height:100%!important;} .html5-endscreen,.ytp-chrome-top,.ytp-chrome-bottom,.ytp-pause-overlay{display:none!important;visibility:hidden!important;}';
  (document.head || document.documentElement).appendChild(style);
  function bindVideo() {
    var err = document.querySelector('.ytp-error');
    if (err && err.offsetParent !== null) {
      emit('yt-error');
      return;
    }
    var vid = document.querySelector('.html5-video-container video') || document.querySelector('video');
    if (vid) {
      if (!vid.__animetvListeners) {
        vid.__animetvListeners = true;
        vid.addEventListener('play', function() { emit('yt-play'); });
        vid.addEventListener('playing', function() { emit('yt-play'); });
        vid.addEventListener('pause', function() { emit(vid.ended ? 'yt-end' : 'yt-pause'); });
        vid.addEventListener('ended', function() { emit('yt-end'); });
        vid.addEventListener('error', function() { emit('yt-error'); });
      }
      if (!vid.paused && !vid.ended && vid.readyState >= 2) emit('yt-play');
      return;
    }
    setTimeout(bindVideo, 200);
  }
  bindVideo();
})();
"""

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
actual fun PlatformTrailer(
    youtubeId: String,
    muted: Boolean,
    modifier: Modifier,
    onPlaybackState: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onPlaybackState)

    val webView = remember(youtubeId, muted) {
        WebView(context).apply {
            isClickable = false
            isLongClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnTouchListener { _, _ -> true }
            setOnGenericMotionListener { _, _ -> true }

            // Start invisible (alpha 0 over transparent) so the poster/banner stays visible until playback begins.
            alpha = 0f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onState(state: String) {
                        post {
                            when (state) {
                                "yt-play" -> {
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    alpha = 1f
                                }
                                "yt-pause", "yt-end", "yt-error" -> {
                                    alpha = 0f
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                }
                            }
                            latestCallback?.invoke(state)
                        }
                    }
                },
                TRAILER_BRIDGE_NAME
            )

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(TRAILER_OBSERVER_JS, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean = false
            }

            val videoId = Uri.encode(youtubeId)
            val muteFlag = if (muted) 1 else 0
            val origin = Uri.encode("https://anilist.co")
            loadUrl(
                "https://www.youtube-nocookie.com/embed/$videoId" +
                    "?autoplay=1&mute=$muteFlag&playsinline=1&controls=0&disablekb=1&fs=0&rel=0" +
                    "&modestbranding=1&showinfo=0&iv_load_policy=3&enablejsapi=1&loop=1&playlist=$videoId&origin=$origin"
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface(TRAILER_BRIDGE_NAME)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}
