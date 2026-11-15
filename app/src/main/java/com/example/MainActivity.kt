package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme

private val ArcadeNavy = Color(0xFF0A0F2C)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        BowlingStrikeScreen()
      }
    }
  }
}

private class AndroidHapticsBridge(private val context: Context) {
  @JavascriptInterface
  fun vibrate(durationMs: Long) {
    val clamped = durationMs.coerceIn(5L, 120L)
    val vibrator =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }

    if (vibrator?.hasVibrator() == true) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
          VibrationEffect.createOneShot(clamped, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(clamped)
      }
    }
  }
}

private class AndroidAppBridge(private val activity: ComponentActivity?) {
  @JavascriptInterface
  fun exitApp() {
    activity?.runOnUiThread { activity.finish() }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BowlingStrikeScreen() {
  val context = LocalContext.current
  val activity = context as? ComponentActivity
  val lifecycleOwner = LocalLifecycleOwner.current

  val webView = remember {
    WebView(context).apply {
      layoutParams =
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        )
      setBackgroundColor(AndroidColor.parseColor("#0A0F2C"))
      overScrollMode = WebView.OVER_SCROLL_NEVER
      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false

      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        mediaPlaybackRequiresUserGesture = false
        cacheMode = WebSettings.LOAD_NO_CACHE
      }

      webViewClient = WebViewClient()
      addJavascriptInterface(AndroidHapticsBridge(context.applicationContext), "AndroidHaptics")
      addJavascriptInterface(AndroidAppBridge(activity), "AndroidApp")
      loadUrl("file:///android_asset/bowling_strike.html")
    }
  }

  DisposableEffect(lifecycleOwner, webView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> webView.onResume()
        Lifecycle.Event.ON_PAUSE -> webView.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      webView.destroy()
    }
  }

  BackHandler {
    webView.evaluateJavascript("window.handleAndroidBack ? window.handleAndroidBack() : false") {
      result ->
      if (result == "false" || result == "null") {
        activity?.finish()
      }
    }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ArcadeNavy)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .testTag("bowling_webview_container")
  ) {
    AndroidView(
      factory = { webView },
      modifier = Modifier.fillMaxSize().testTag("bowling_webview"),
    )
  }
}
