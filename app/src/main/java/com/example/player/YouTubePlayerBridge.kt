package com.example.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class YouTubePlayerBridge(private val context: Context) {
    private val logTag = "YTPlayerBridge"
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var onStateChanged: ((Int) -> Unit)? = null
    var onProgressUpdated: ((Long, Long) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var isPlayerReady = false
    private var pendingVideoId: String? = null
    var currentVideoId: String? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(view: WebView) {
        if (this.webView === view) return
        this.webView = view
        view.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            }

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onPlayerReady() {
                    mainHandler.post {
                        isPlayerReady = true
                        Log.d(logTag, "YouTube IFrame Player ready")
                        pendingVideoId?.let { id ->
                            loadVideo(id)
                            pendingVideoId = null
                        }
                    }
                }

                @JavascriptInterface
                fun onPlayerStateChange(state: Int) {
                    mainHandler.post {
                        Log.d(logTag, "YouTube player state: $state")
                        onStateChanged?.invoke(state)
                    }
                }

                @JavascriptInterface
                fun onProgress(currentSec: Float, durationSec: Float) {
                    mainHandler.post {
                        val currentMs = (currentSec * 1000).toLong()
                        val durationMs = (durationSec * 1000).toLong()
                        onProgressUpdated?.invoke(currentMs, durationMs)
                    }
                }

                @JavascriptInterface
                fun onPlayerError(errorCode: Int) {
                    mainHandler.post {
                        Log.e(logTag, "YouTube Player Error: $errorCode")
                        onError?.invoke("Playback error code $errorCode")
                    }
                }
            }, "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }
            webChromeClient = WebChromeClient()

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                    <style>
                        body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                        #player { width: 100%; height: 100%; }
                    </style>
                </head>
                <body>
                    <div id="player"></div>
                    <script src="https://www.youtube-nocookie.com/iframe_api"></script>
                    <script>
                        var player;
                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                height: '100%',
                                width: '100%',
                                playerVars: {
                                    'autoplay': 1,
                                    'controls': 1,
                                    'playsinline': 1,
                                    'rel': 0,
                                    'modestbranding': 1,
                                    'origin': 'https://www.youtube-nocookie.com'
                                },
                                events: {
                                    'onReady': function(e) {
                                        window.AndroidBridge.onPlayerReady();
                                    },
                                    'onStateChange': function(e) {
                                        window.AndroidBridge.onPlayerStateChange(e.data);
                                    },
                                    'onError': function(e) {
                                        window.AndroidBridge.onPlayerError(e.data);
                                    }
                                }
                            });
                            setInterval(function() {
                                if (player && player.getCurrentTime && player.getDuration) {
                                    window.AndroidBridge.onProgress(player.getCurrentTime(), player.getDuration());
                                }
                            }, 500);
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()

            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
        }
    }

    fun loadVideo(videoId: String) {
        currentVideoId = videoId
        if (!isPlayerReady || webView == null) {
            pendingVideoId = videoId
            return
        }
        val js = """
            if (player && player.loadVideoById) {
                player.loadVideoById('$videoId');
                player.playVideo();
            }
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    fun play() {
        webView?.evaluateJavascript("if (player && player.playVideo) { player.playVideo(); }", null)
    }

    fun pause() {
        webView?.evaluateJavascript("if (player && player.pauseVideo) { player.pauseVideo(); }", null)
    }

    fun seekTo(seconds: Float) {
        webView?.evaluateJavascript("if (player && player.seekTo) { player.seekTo($seconds, true); }", null)
    }

    fun setSpeed(speed: Float) {
        webView?.evaluateJavascript("if (player && player.setPlaybackRate) { player.setPlaybackRate($speed); }", null)
    }

    fun stop() {
        pendingVideoId = null
        currentVideoId = null
        val js = "if (player && player.stopVideo) { player.stopVideo(); } else if (player && player.pauseVideo) { player.pauseVideo(); }"
        webView?.evaluateJavascript(js, null)
    }
}
