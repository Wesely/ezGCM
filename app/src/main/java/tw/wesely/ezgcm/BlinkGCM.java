package tw.wesely.ezgcm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.greenrobot.event.EventBus;
import tw.com.blink.blinklib.network.ApiCall;
import tw.com.blink.blinklib.network.BlinkApi;
import tw.com.blink.blinklib.network.BlinkApiRespondEvent;
import tw.com.blink.blinklib.pref.Pref;

/**
 * Created by Wesely on 2015/4/9.
 */
public class BlinkGCM {
	static EventBus eventBus;
	// for GCM
	String SENDER_ID = "993052024183";
	private static final String PROPERTY_APP_VERSION = "appVersion";
	static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1001;

	static final String TAG = "GCM Demo";

	private static String android_id;
	private static String regid;
	GoogleCloudMessaging gcm;
	AtomicInteger msgId = new AtomicInteger();
	SharedPreferences prefs;
	SharedPreferences.Editor editor;
	static Context context;

	public void prepareGCM(Context context) {
		this.context = context;
		prefs = getGcmPreferences(context);
		if(prefs.getBoolean(Pref.PREF_BW_GCM_REGESTERED, false))
			return;
		editor = prefs.edit();
		eventBus = EventBus.getDefault();
		eventBus.register(this);
		android_id = Settings.Secure.getString(context.getContentResolver(),
				Settings.Secure.ANDROID_ID);
		if (checkPlayServices()) {
			gcm = GoogleCloudMessaging.getInstance(context);
			regid = getRegistrationId(context);

			if (regid.isEmpty()) {
				registerInBackground();
			}
		} else { //
			Log.i(TAG, "No valid Google Play Services APK found.");
			Toast.makeText(context, "需安裝GooglePlay服務才能有完善功能", Toast.LENGTH_SHORT)
					.show();
			Intent browserIntent = new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms&hl=zh_TW"));
			context.startActivity(browserIntent);
		}
		sendUpstreamMsg();
		sendRegistrationIdToBackend();

	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
		int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
		if (status != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(status)) {
				showErrorDialog(status);
			} else {
				Toast.makeText(context, "需安裝GooglePlay服務才能有完善功能",
						Toast.LENGTH_SHORT).show();
			}
			return false;
		}
		return true;
	}

	void showErrorDialog(int code) {
		GooglePlayServicesUtil.getErrorDialog(code, (Activity) context,
				REQUEST_CODE_RECOVER_PLAY_SERVICES).show();
	}

	/**
	 * Stores the registration ID and the app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 * @param context application's context.
	 * @param regId   registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
		int appVersion = getAppVersion(context);
		Log.i(TAG, "Saving regId on app version " + appVersion);
		editor.putString(Pref.PREF_BW_GCM_REG_ID, regId);
		editor.putInt(PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}

	/**
	 * Gets the current registration ID for application on GCM service, if there
	 * is one.
	 * <p/>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 * registration ID.
	 */
	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGcmPreferences(context);
		String registrationId = prefs.getString(Pref.PREF_BW_GCM_REG_ID, "");
		if (registrationId.isEmpty()) {
			Log.i(TAG, "Registration not found.");
			return "";
		}
		// Check if app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version.
		int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION,
				Integer.MIN_VALUE);
		int currentVersion = getAppVersion(context);
		if (registeredVersion != currentVersion) {
			Log.i(TAG, "App version changed.");
			return "";
		}
		return registrationId;
	}

	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p/>
	 * Stores the registration ID and the app versionCode in the application's
	 * shared preferences.
	 */
	private void registerInBackground() {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				try {
					if (gcm == null) {
						gcm = GoogleCloudMessaging.getInstance(context);
					}
					regid = gcm.register(SENDER_ID);
					msg = "Device registered, registration ID=" + regid;

					// You should send the registration ID to your server over
					// HTTP, so it
					// can use GCM/HTTP or CCS to send messages to your app.
					sendRegistrationIdToBackend();

					// For this demo: we don't need to send it because the
					// device will send
					// upstream messages to a server that echo back the message
					// using the
					// 'from' address in the message.

					// Persist the regID - no need to register again.
					storeRegistrationId(context, regid);
					editor.putBoolean(Pref.PREF_BW_GCM_ACTIVE, true);
					editor.commit();
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					// If there is an error, don't just keep trying to register.
					// Require the user to click a button again, or perform
					// exponential back-off.
					editor.putBoolean(Pref.PREF_BW_GCM_ACTIVE, false);
					editor.commit();
				}
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				Log.d("registerInBackground, done", "msg = " + msg);
			}
		}.execute(null, null, null);
	}

	public void sendUpstreamMsg() {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				try {
					Bundle data = new Bundle();
					data.putString("my_message", "Hello World");
					data.putString("my_action", "com.Blink.android.ECHO_NOW");
					String id = Integer.toString(msgId.incrementAndGet());
					gcm.send(SENDER_ID + "@gcm.googleapis.com", id, data);
					msg = "Sent message";
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					Log.e("sendUpstreamMsg - IOException", msg);
				}
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				Log.d("onPostExecute - msg", msg);
				Log.d("onPostExecute - regid", regid);
			}
		}.execute(null, null, null);
		return;
	}

	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	private static int getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getGcmPreferences(Context context) {
		// This sample app persists the registration ID in shared preferences,
		// but how you store the regID in your app is up to you.
		return new Pref(context).getSharedPref();
	}

	private class GCMRegResult {
		JSONObject result;

		public GCMRegResult(JSONObject result) {
			this.result = result;
		}
	}

	/**
	 * Sends the registration ID to your server over HTTP, so it can use
	 * GCM/HTTP or CCS to send messages to your app. Not needed for this demo
	 * since the device sends upstream messages to a server that echoes back the
	 * message using the 'from' address in the message.
	 */
	@SuppressLint("InlinedApi")
	private void sendRegistrationIdToBackend() {

		Pref pref = new Pref(context);
		if (regid == null)
			return;
		if (regid.length() < 1)
			return;
		Log.d("sendRegistrationIdToBackend", android.os.Build.MODEL);
		Map<String, String> postData = new HashMap<String, String>();

		if (pref.isLogin()) {
			postData.put("token", pref.getToken());
		} else {
			postData.put("token", "");
		}
		postData.put("registration_id", regid);
		if (pref.getSharedPref().getString(Pref.PREF_BW_GCM_ACTIVE,
				"1") != null)
			if (pref.getSharedPref().getString(
					Pref.PREF_BW_GCM_ACTIVE, "1") == "1") {
				postData.put("active", "1");
				pref.getSharedPref().edit()
						.putString(Pref.PREF_BW_GCM_ACTIVE, "1").commit();

				Log.d("GCM_setting", "avtive = 1, because state = 1");
			} else if (pref.getSharedPref().getString(
					Pref.PREF_BW_GCM_ACTIVE, "1") == "0") {
				postData.put("active", "0");
				pref.getSharedPref().edit()
						.putString(Pref.PREF_BW_GCM_ACTIVE, "0").commit();
				Log.d("GCM_setting", "avtive = 0, because state = 0");
			} else {
				Log.d("GCM_setting", "pref=null");
				postData.put("active", "1");
				pref.getSharedPref().edit()
						.putString(Pref.PREF_BW_GCM_ACTIVE, "1").commit();
			}
		postData.put("device_id", android_id);
		postData.put("name", "TEMP");
		// c.close();

		Log.e("GCM_POST_DATA", postData.toString());
		new ApiCall(eventBus, BlinkApi.API_GCM_REGISTER).setData(postData).postJSON().execute();

	}

	@SuppressLint("InlinedApi")
	public static void changeNotifyState(String state) {
		Log.d("Main Activity-changeNotifyState", android.os.Build.MODEL);
		Map<String, String> postData = new HashMap<String, String>();
		if (new Pref(context).isLogin()) {
			postData.put("token",
					new Pref(context).getToken());
		} else {
			postData.put("token", "");
		}

		postData.put("registration_id", regid);
		postData.put("active", state);
		postData.put("device_id", android_id);
		postData.put("name", android.os.Build.MODEL);
		// c.close();

		new ApiCall(eventBus, BlinkApi.API_GCM_REGISTER).setData(postData).postJSON().execute();
		// localBus.post(new GCMRegResult(result));

	}

	public void onEvent(BlinkApiRespondEvent event) {
		Log.d("GCM_result", event.result.toString());
		try {
			if(event.getJSONObject().getBoolean("success")){
				editor = prefs.edit();
				editor.putBoolean(Pref.PREF_BW_GCM_REGESTERED, true);
				editor.commit();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
}
