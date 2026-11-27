package com.paregi.animetv

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

private const val TRAILER_BRIDGE_NAME = "AnimeTvTrailer"
private const val TRAILER_ORIGIN = "https://anilist.co"
private const val TRAILER_REFERER = "https://anilist.co/"
private const val TRAILER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

private const val TRAILER_CSS =
    "video{object-fit:cover!important;width:100%!important;height:100%!important;left:0!important;top:0!important;}" +
        ".html5-endscreen{visibility:hidden!important;}" +
        "#player .html5-video-player>*{display:none!important;}" +
        "#yterror_msg,#player .html5-video-player .html5-video-container{z-index:999999!important;position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;width:100%!important;height:100%!important;visibility:visible!important;}" +
        "#player .html5-video-player .html5-video-container{display:block!important;}" +
        "#yterror_msg{z-index:1!important;display:none;background:#000!important;}" +
        "#yterror_msg.active{z-index:9999999!important;display:flex;align-items:center;justify-content:center;}" +
        "#yterror_msg div{color:#aaa;font-size:max(13px,3vw);text-align:center;padding:0 16px;}" +
        "#player,#player .html5-video-player,body,html{background:#000!important;}"

private const val TRAILER_OBSERVER_JS = """
(function() {
  function ensureStyle() {
    var target = document.head || document.documentElement;
    if (!target) {
      setTimeout(ensureStyle, 50);
      return;
    }
    if (!document.getElementById('__animetv_yt_style')) {
      var style = document.createElement('style');
      style.id = '__animetv_yt_style';
      style.textContent = "$TRAILER_CSS";
      target.appendChild(style);
    }
  }
  ensureStyle();
  if (window.__animetvTrailerBound) {
    if (window.__animetvCheckTrailer) window.__animetvCheckTrailer();
    return;
  }
  window.__animetvTrailerBound = true;

  var lastState = '';
  var vid = null;
  var errorMsgElm = null;
  var isErrorMsg = false;
  var hasPlayed = false;
  var isEnded = false;
  var findAttempts = 0;

  function emit(state) {
    if (!state || state === lastState) return;
    lastState = state;
    if (state === 'yt-play') {
      hasPlayed = true;
    }
    try {
      if (window.$TRAILER_BRIDGE_NAME) {
        window.$TRAILER_BRIDGE_NAME.onState(state);
      }
    } catch (e) {}
  }

  function showError() {
    var host = document.body || document.documentElement;
    if (!errorMsgElm) {
      if (!host) {
        setTimeout(showError, 100);
        emit('yt-error');
        return;
      }
      errorMsgElm = document.createElement('div');
      errorMsgElm.setAttribute('id', 'yterror_msg');
      errorMsgElm.innerHTML = '<div>Trailer video is unavailable<br>or regionally blocked</div>';
      host.appendChild(errorMsgElm);
    }
    if (!isErrorMsg) {
      errorMsgElm.classList.add('active');
      isErrorMsg = true;
    }
    emit('yt-error');
  }

  function hideError() {
    if (errorMsgElm && isErrorMsg) {
      errorMsgElm.classList.remove('active');
      isErrorMsg = false;
    }
  }

  function hasYtpError() {
    var err = document.querySelector('.ytp-error');
    if (!err) return false;
    if (err.style && err.style.display === 'none') return false;
    return !!(err.textContent && err.textContent.trim().length > 0);
  }

  function checkVid() {
    if (hasYtpError()) {
      showError();
      return;
    }
    if (!vid) return;
    var hasSrc = !!(vid.currentSrc || vid.src || vid.querySelector('source[src]'));
    if (!hasSrc) {
      showError();
    } else {
      hideError();
    }
  }

  function mapYtCode(code) {
    if (code === 1) return 'yt-play';
    if (code === 2) return 'yt-pause';
    if (code === 0) return 'yt-end';
    return null;
  }

  window.__animetvTrailerCmd = function(cmd) {
    var v = vid || document.querySelector('.html5-video-container video') || document.querySelector('video');
    if (!v) return;
    if (cmd === 'toggle') {
      if (v.paused || v.ended) {
        var p = v.play();
        if (p && p.catch) p.catch(function() {});
      } else {
        v.pause();
      }
    } else if (cmd === 'pause') {
      v.pause();
    } else if (cmd === 'play') {
      var p = v.play();
      if (p && p.catch) p.catch(function() {});
    } else if (cmd === 'mute') {
      v.muted = !v.muted;
    } else if (cmd === 'mute-on') {
      v.muted = true;
    } else if (cmd === 'mute-off') {
      v.muted = false;
    }
  };
  window.__animetvShowError = showError;

  function emitMappedState(mapped) {
    if (mapped === 'yt-play') {
      isEnded = false;
      hideError();
      emit('yt-play');
    } else if (mapped === 'yt-end') {
      isEnded = true;
      emit('yt-end');
    } else if (mapped === 'yt-pause' && hasPlayed) {
      emit('yt-pause');
    }
  }

  function handlePlayerMessage(raw) {
    try {
      var data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (!data) return;
      if (data.event === 'onStateChange') {
        emitMappedState(mapYtCode(data.info));
      } else if (data.event === 'infoDelivery' && data.info && typeof data.info.playerState === 'number') {
        emitMappedState(mapYtCode(data.info.playerState));
      } else if (data.event === 'onError') {
        showError();
      } else if (data.vcmd) {
        if (data.vcmd === 'play' || data.vcmd === 'pause' || data.vcmd === 'toggle' || data.vcmd === 'mute') {
          window.__animetvTrailerCmd(data.vcmd);
        }
      }
    } catch (err) {}
  }

  window.addEventListener('message', function(e) {
    handlePlayerMessage(e.data);
  });

  var origPost = window.parent && window.parent.postMessage ? window.parent.postMessage.bind(window.parent) : null;
  if (window.parent === window && origPost) {
    window.postMessage = function(msg) {
      handlePlayerMessage(msg);
      return origPost(msg, '*');
    };
  }
  try {
    window.postMessage(JSON.stringify({ event: 'listening', id: 1, channel: 'widget' }), '*');
  } catch (e) {}

  function initVid() {
    vid.loop = true;
    if (typeof window.__animetvDesiredMuted === 'boolean') {
      vid.muted = window.__animetvDesiredMuted;
    }
    if (!vid.__animetvListeners) {
      vid.__animetvListeners = true;
      vid.addEventListener('play', function() {
        hasPlayed = true;
        isEnded = false;
        checkVid();
        if (!isErrorMsg) emit('yt-play');
      });
      vid.addEventListener('playing', function() {
        hasPlayed = true;
        isEnded = false;
        checkVid();
        if (!isErrorMsg) emit('yt-play');
      });
      vid.addEventListener('pause', function() {
        if (vid.ended) {
          isEnded = true;
          emit('yt-end');
        } else if (hasPlayed) {
          emit('yt-pause');
        }
        checkVid();
      });
      vid.addEventListener('ended', function() {
        isEnded = true;
        emit('yt-end');
        checkVid();
      });
      vid.addEventListener('error', function() {
        showError();
      });
    }
    if (!vid.paused && !vid.ended && vid.readyState >= 2) {
      hasPlayed = true;
      hideError();
      emit('yt-play');
    }
    setTimeout(checkVid, 1500);
    setInterval(function() {
      if (hasYtpError()) {
        showError();
        return;
      }
      if (isEnded) {
        emit('yt-end');
      } else if (vid.paused) {
        if (hasPlayed) emit('yt-pause');
      } else if (vid.currentSrc || vid.src) {
        hasPlayed = true;
        hideError();
        emit('yt-play');
      }
    }, 2000);
  }

  window.__animetvCheckTrailer = function() {
    ensureStyle();
    if (isErrorMsg && !errorMsgElm) {
      showError();
    } else if (hasYtpError()) {
      showError();
    } else if (vid && !vid.paused && !vid.ended && vid.readyState >= 2) {
      hasPlayed = true;
      hideError();
      emit('yt-play');
    }
  };

  function findVid() {
    if (vid) return;
    if (hasYtpError()) {
      showError();
      return;
    }
    vid = document.querySelector('.html5-video-container video') || document.querySelector('video');
    if (vid) {
      initVid();
      return;
    }
    findAttempts++;
    if (findAttempts >= 40) {
      showError();
      return;
    }
    setTimeout(findVid, 200);
  }
  findVid();
})();
"""

private fun createBlockedResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "utf-8",
        400,
        "Bad Request",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
actual fun PlatformTrailer(
    youtubeId: String,
    muted: Boolean,
    modifier: Modifier,
    onPlaybackState: ((String) -> Unit)?,
    controller: PlatformTrailerController?,
    hideUntilPlaying: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onPlaybackState)
    val latestHideUntilPlaying by rememberUpdatedState(hideUntilPlaying)
    val destroyed = remember(youtubeId) { AtomicBoolean(false) }
    val pausedByLifecycle = remember(youtubeId) { AtomicBoolean(false) }
    val initialMuted = remember(youtubeId) { muted }

    val webView = remember(youtubeId) {
        WebView(context).apply {
            isClickable = false
            isLongClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnTouchListener { _, _ -> true }
            setOnGenericMotionListener { _, _ -> true }

            if (latestHideUntilPlaying) {
                alpha = 0f
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                alpha = 1f
                setBackgroundColor(Color.BLACK)
            }

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.useWideViewPort = false
            settings.userAgentString = TRAILER_USER_AGENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                settings.isAlgorithmicDarkeningAllowed = false
            }

            fun applyState(state: String) {
                if (destroyed.get()) return
                if (latestHideUntilPlaying) {
                    when (state) {
                        "yt-play" -> {
                            setBackgroundColor(Color.BLACK)
                            alpha = 1f
                        }
                        "yt-pause", "yt-end", "yt-error" -> {
                            alpha = 0f
                            setBackgroundColor(Color.TRANSPARENT)
                        }
                    }
                } else {
                    setBackgroundColor(Color.BLACK)
                    alpha = 1f
                }
                latestCallback?.invoke(state)
            }

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onState(state: String) {
                        post { applyState(state) }
                    }
                },
                TRAILER_BRIDGE_NAME
            )

            var mainFrameFailed = false
            var injectedUrl: String? = null

            fun handleMainFrameFailure(targetView: WebView?) {
                if (destroyed.get()) return
                mainFrameFailed = true
                val targetUrl = targetView?.url
                val script = if (targetUrl != null && injectedUrl == targetUrl) {
                    "window.__animetvShowError && window.__animetvShowError();"
                } else {
                    injectedUrl = targetUrl
                    "$TRAILER_OBSERVER_JS\nwindow.__animetvShowError && window.__animetvShowError();"
                }
                targetView?.evaluateJavascript(script, null)
                applyState("yt-error")
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString() ?: return true
                    return !(target.startsWith("https://www.youtube-nocookie.com/embed/") ||
                        target.startsWith("https://www.youtube.com/embed/"))
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val target = url ?: return true
                    return !(target.startsWith("https://www.youtube-nocookie.com/embed/") ||
                        target.startsWith("https://www.youtube.com/embed/"))
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
                    val host = uri.host?.lowercase() ?: return super.shouldInterceptRequest(view, request)
                    if (
                        host.contains("youtube.com") ||
                        host.contains("youtube-nocookie.com") ||
                        host.contains("googlevideo.com")
                    ) {
                        val path = uri.path.orEmpty()
                        // Skip blocking image/CSS Accept tokens on top-level/embed HTML navigations (e.g. text/html,...,image/avif).
                        if (!request.isForMainFrame && !path.startsWith("/embed/")) {
                            val accept = request.requestHeaders?.entries
                                ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
                                ?.value
                                ?.lowercase()
                            if (
                                accept != null &&
                                !accept.contains("text/html") &&
                                (accept.contains("text/css") || accept.contains("image/"))
                            ) {
                                return createBlockedResponse()
                            }
                        }
                        val url = uri.toString()
                        if (
                            path.endsWith("/endscreen.js") ||
                            path.endsWith("/captions.js") ||
                            path.endsWith("/embed.js") ||
                            url.contains("/log_event?alt=json") ||
                            url.contains(".com/ptracking") ||
                            url.contains(".com/api/stats/")
                        ) {
                            return createBlockedResponse()
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!destroyed.get() && url != null && url != "about:blank") {
                        injectedUrl = null
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!destroyed.get() && url != null && url != "about:blank") {
                        val script = when {
                            injectedUrl != url -> {
                                injectedUrl = url
                                if (mainFrameFailed) {
                                    "$TRAILER_OBSERVER_JS\nwindow.__animetvShowError && window.__animetvShowError();"
                                } else {
                                    TRAILER_OBSERVER_JS
                                }
                            }
                            mainFrameFailed -> "window.__animetvShowError && window.__animetvShowError();"
                            else -> "window.__animetvCheckTrailer && window.__animetvCheckTrailer();"
                        }
                        view?.evaluateJavascript(script, null)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        handleMainFrameFailure(view)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                        handleMainFrameFailure(view)
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    val currentUrl = view?.url
                    if (
                        newProgress >= 20 &&
                        !destroyed.get() &&
                        currentUrl != null &&
                        currentUrl != "about:blank" &&
                        injectedUrl != currentUrl
                    ) {
                        injectedUrl = currentUrl
                        view.evaluateJavascript(TRAILER_OBSERVER_JS, null)
                    }
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false
            }

            val videoId = Uri.encode(youtubeId)
            val muteFlag = if (initialMuted) 1 else 0
            val origin = Uri.encode(TRAILER_ORIGIN)
            loadUrl(
                "https://www.youtube-nocookie.com/embed/$videoId" +
                    "?autoplay=1&mute=$muteFlag&playsinline=1&controls=0&disablekb=1&fs=0&rel=0" +
                    "&modestbranding=1&showinfo=0&iv_load_policy=3&enablejsapi=1&loop=1&origin=$origin",
                mapOf("Referer" to TRAILER_REFERER)
            )
        }
    }

    var appliedMuted by remember(webView) { mutableStateOf(initialMuted) }
    LaunchedEffect(webView, muted) {
        if (!destroyed.get() && muted != appliedMuted) {
            appliedMuted = muted
            val jsCmd = if (muted) "'mute-on'" else "'mute-off'"
            webView.evaluateJavascript(
                "window.__animetvDesiredMuted=$muted;window.__animetvTrailerCmd&&window.__animetvTrailerCmd($jsCmd);",
                null
            )
        }
    }

    LaunchedEffect(webView, hideUntilPlaying) {
        if (!destroyed.get() && !hideUntilPlaying) {
            webView.setBackgroundColor(Color.BLACK)
            webView.alpha = 1f
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            if (destroyed.get()) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    pausedByLifecycle.set(true)
                    webView.onPause()
                    webView.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (pausedByLifecycle.compareAndSet(true, false)) {
                        webView.onResume()
                        webView.resumeTimers()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(webView, controller) {
        controller?.commandDispatcher = { cmd ->
            if (!destroyed.get()) {
                val quotedCmd = JSONObject.quote(cmd)
                webView.post {
                    if (!destroyed.get()) {
                        webView.evaluateJavascript(
                            "window.__animetvTrailerCmd && window.__animetvTrailerCmd($quotedCmd);",
                            null
                        )
                    }
                }
            }
        }
        onDispose {
            controller?.commandDispatcher = null
        }
    }

    DisposableEffect(webView) {
        onDispose {
            destroyed.set(true)
            if (pausedByLifecycle.compareAndSet(true, false)) {
                webView.resumeTimers()
            }
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeJavascriptInterface(TRAILER_BRIDGE_NAME)
            webView.onPause()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}
