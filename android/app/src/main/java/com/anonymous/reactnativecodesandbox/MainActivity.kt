package com.anonymous.reactnativecodesandbox
import expo.modules.splashscreen.SplashScreenManager

import android.content.Intent
import android.os.Build
import android.os.Bundle

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

import expo.modules.ReactActivityDelegateWrapper
import android.util.Log

import com.facebook.react.ReactApplication

class MainActivity : ReactActivity(), MainActivityCallback, EsimManager.EsimInstallCallback {

  private lateinit var esimManager: EsimManager
  private lateinit var deleter: HandlerForEsimDelete
  private lateinit var toggler: EsimToggleHandler

  override fun onCreate(savedInstanceState: Bundle?) {
    // Set the theme to AppTheme BEFORE onCreate to support
    // coloring the background, status bar, and navigation bar.
    // This is required for expo-splash-screen.
    // setTheme(R.style.AppTheme);
    // @generated begin expo-splashscreen - expo prebuild (DO NOT MODIFY) sync-f3ff59a738c56c9a6119210cb55f0b613eb8b6af
    SplashScreenManager.registerOnActivity(this)
    // @generated end expo-splashscreen
    super.onCreate(null)
    this.esimManager = EsimManager(this, this)
    this.deleter = HandlerForEsimDelete(this, this)
    this.toggler = EsimToggleHandler(this, this)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    // Handle eSIM provisioning result from native UI
    esimManager.handleActivityResult(requestCode, resultCode, data)
  }

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "main"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate {
    return ReactActivityDelegateWrapper(
          this,
          BuildConfig.IS_NEW_ARCHITECTURE_ENABLED,
          object : DefaultReactActivityDelegate(
              this,
              mainComponentName,
              fabricEnabled
          ){})
  }

  /**
    * Align the back button behavior with Android S
    * where moving root activities to background instead of finishing activities.
    * @see <a href="https://developer.android.com/reference/android/app/Activity#onBackPressed()">onBackPressed</a>
    */
  override fun invokeDefaultOnBackPressed() {
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
          if (!moveTaskToBack(false)) {
              // For non-root activities, use the default implementation to finish them.
              super.invokeDefaultOnBackPressed()
          }
          return
      }

      // Use the default back button implementation on Android S
      // because it's doing more than [Activity.moveTaskToBack] in fact.
      super.invokeDefaultOnBackPressed()
  }

  override fun executeInstallFunction(address: String)
  {
    Log.i("MainActivity", "Executing eSIM install for: $address")
    esimManager.install(address)
  }

  override fun onInstallComplete(success: Boolean, message: String) {
    Log.i("MainActivity", "eSIM install complete - Success: $success, Message: $message")
    val reactContext = (applicationContext as ReactApplication).reactNativeHost.reactInstanceManager.currentReactContext
    val module = reactContext?.getNativeModule(EsimModule::class.java)
    if (module != null) {
      module.onEsimDownloadComplete(success, message)
    } else {
      // Fallback: try static instance if react context wasn't available at callback time
      EsimModule.instance?.onEsimDownloadComplete(success, message)
    }
  }

  override fun executeDeleteFunction(id: Int)
  {
    deleter.deleteEsim(id) { success, message ->
      val module = (applicationContext as ReactApplication).reactNativeHost.reactInstanceManager
        .currentReactContext?.getNativeModule(EsimModule::class.java)
        module?.onEsimDeleteComplete(success, message)
    }
  }

  override fun executeEsimToggle(enable: Boolean, subscriptionId: Int, portIndex: Int) {
    toggler.toggleEsim(subscriptionId, portIndex, enable) { success, message ->
      val module =
              (applicationContext as ReactApplication).reactNativeHost.reactInstanceManager
                      .currentReactContext?.getNativeModule(EsimModule::class.java)
      module?.onEsimToggleComplete(success, message)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    esimManager.cleanup()
  }

}

interface MainActivityCallback {
    fun executeInstallFunction(data: String)
    fun executeDeleteFunction(id: Int)
    fun executeEsimToggle(enable: Boolean, subscriptionId: Int, portIndex: Int)
}
