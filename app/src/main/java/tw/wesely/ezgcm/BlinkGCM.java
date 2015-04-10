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

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by Wesely on 2015/4/9.
 */
public class BlinkGCM {
	// TODO: your api ID here
	String SENDER_ID = "123456789123456789";
	private static final String PROPERTY_APP_VERSION = "appVersion";
	private static final String PREF_KEY = "just.a.pref.key";
	private static final String GCM_REGISTERED = "gcm.registered";
	private static final String GCM_ID = "gcm.id";
	private static final String GCM_ACTIVE = "gcm.active";
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
		if(prefs.getBoolean(GCM_REGISTERED, false))
			return;
		editor = prefs.edit();

		android_id = Settings.Secure.getString(context.getContentResolver(),
				Settings.Secure.ANDROID_ID);
		if (checkPlayServices()) {
			gcm = GoogleCloudMessaging.getInstance(context);
			regid = getRegistrationId(context);

			if (regid.isEmpty()) {
				registerInBackground();
			}
		} else {
			// TODO: can handle it by yourself
			Log.i(TAG, "No valid Google Play Services APK found.");
			Toast.makeText(context, "you need to install GooglePlay Service", Toast.LENGTH_SHORT)
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
				Toast.makeText(context, "you need to install GooglePlay Service",
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
		editor.putString(GCM_ID, regId);
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
		String registrationId = prefs.getString(GCM_ID, "");
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
					editor.putBoolean(GCM_ACTIVE, true);
					editor.commit();
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					// If there is an error, don't just keep trying to register.
					// Require the user to click a button again, or perform
					// exponential back-off.
					editor.putBoolean(GCM_ACTIVE, false);
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
		return context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE);
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

		if (regid == null)
			return;
		if (regid.length() < 1)
			return;
		Log.d("sendRegistrationIdToBackend", android.os.Build.MODEL);
		Map<String, String> postData = new HashMap<String, String>();
		postData.put("registration_id", regid);
		postData.put("something", "else");
		Log.e("GCM_POST_DATA", postData.toString());
		//TODO: send postData to your server via your own API

	}

}
