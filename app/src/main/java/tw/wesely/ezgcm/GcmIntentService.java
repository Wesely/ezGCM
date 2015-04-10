/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tw.wesely.ezgcm;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.Blink.android.mainpage.BlinkMainActivity;
import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
	public static final int NOTIFICATION_ID = 1;
	private NotificationManager mNotificationManager;
	NotificationCompat.Builder builder;

	public GcmIntentService() {
		super("GcmIntentService");
	}

	public static final String TAG = "GcmIntentService";

	protected void onMessage(Context context, Intent intent) {
		Log.i(TAG, "Received message");
		Bundle bData = intent.getExtras();
		// String message = bData.getString("message");
		// String campaigndate = bData.getString("campaigndate");
		// String title = bData.getString("title");
		// String description = bData.getString("description");
		generateNotification(context, bData);
	}

	private static void generateNotification(Context context, Bundle data) {
		Log.e("generateNotification", "generateNotification");
		int icon = R.drawable.ic_launcher;
		long when = System.currentTimeMillis();
		NotificationManager nm = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent ni = new Intent(context, BlinkMainActivity.class);
		ni.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent intent = PendingIntent.getActivity(context, 0, ni, 0);
		Notification noti = new NotificationCompat.Builder(context)
				.setContentTitle("Blink 通知").setContentText("點擊查看更多")
				.setContentIntent(intent).setDefaults(Notification.DEFAULT_ALL)
				.setSmallIcon(icon).setWhen(when).build(); // just an init ?
		nm.notify(0, noti);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(TAG, "onHandleIntent");
		Bundle extras = intent.getExtras();
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
		// The getMessageType() intent parameter must be the intent you received
		// in your BroadcastReceiver.
		String messageType = gcm.getMessageType(intent);

		if (!extras.isEmpty()) { // has effect of unparcelling Bundle
			Log.d(TAG, "!extras.isEmpty()");
			Log.d(TAG, "!extras.isEmpty()" + extras.toString());
			/*
			 * Filter messages based on message type. Since it is likely that
			 * GCM will be extended in the future with new message types, just
			 * ignore any message types you're not interested in, or that you
			 * don't recognize.
			 */
			if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR
					.equals(messageType)) {
				Log.e("onHandleIntent", "MESSAGE_TYPE_SEND_ERROR");
				// sendNotification("Send error: " + extras.toString());
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED
					.equals(messageType)) {
				Log.e("onHandleIntent", "MESSAGE_TYPE_DELETED");
				// sendNotification("Deleted messages on server: " +
				// extras.toString());
				// If it's a regular GCM message, do some work.
			} else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE
					.equals(messageType)) {
				Log.i(TAG, "Completed work @ " + SystemClock.elapsedRealtime());
				// Post notification of received message.
				sendNotification(extras);
				Log.i(TAG, "Received: " + extras.toString());
			}
		} else {
			Log.d(TAG, "extras isEmpty()");
		}

		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}

	// Put the message into a notification and post it.
	// This is just one simple example of what you might choose to do with
	// a GCM message.
	private void sendNotification(Bundle extras) {
		// - ev : done, event (活動) 單元；part 2 : 項目 id 是活動序號
		// - et : done, event ticket (活動票券)；part 2 : 項目 id 是票券的活序號
		// - mc : done, merchant card (店家卡)，part 2 : 店家卡 id 是 user 卡片代號
		// - vt : vieshow ticket；電影票區塊的電影 item (暫定)
		// - vs : done, 開啟威秀單元頁；配合 url 開啟電影頁
		// - es : 開啟玉山單元頁；暫定不會有這 type (暫定)
		// - bc : 開啟 Blink Coin webview
		// (https://www.blink.com.tw/account/coin/app/)
		/**
		try {
			Log.d("sendNotification", extras.toString());
			String msg = extras.getString("message");
			String inapp = extras.getString("inapp");
			String url = extras.getString("url");
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
					new Intent(this, MainActivity.class).putExtra(
							"FromNotification", true), 0);
			if (url != null) {
				if (url.length() > 0) {
					Log.d("Notification", "url:" + url);
					contentIntent = PendingIntent.getActivity(this, 0,
							new Intent(this, WebViewWithCookie.class).putExtra(
									"url", url), 0);
				}
			} else if (inapp.contains("ev")) {
				String id = inapp.replace("ev|", "");
				Log.d("Notification", "inapp:" + inapp + " ; id = " + id);
				contentIntent = PendingIntent.getActivity(this, 0, new Intent(
						this, EventActivity.class).putExtra("FromNotification",
						true), 0);

			} else if (inapp.contains("et")) {
				String id = inapp.replace("et|", "");
				contentIntent = PendingIntent.getActivity(this, 0, new Intent(
						this, WalletActivity.class).putExtra(
						"FromNotification", true), 0);

			} else if (inapp.contains("mc")) {
				String id = inapp.replace("mc|", "");
				contentIntent = PendingIntent.getActivity(this, 0, new Intent(
						this, WalletActivity.class).putExtra(
						"FromNotification", true), 0);

			} else if (inapp.contains("vs")) {
				String id = inapp.replace("vs|", "");
				contentIntent = PendingIntent.getActivity(this, 0, new Intent(
						this, VieshowActivity.class).putExtra(
						"FromNotification", true), 0);

			} else if (inapp.contains("bc")) {
				String id = inapp.replace("bc|", "");
				contentIntent = PendingIntent.getActivity(this, 0, new Intent(
						this, WalletBlinkCoinWebViewActivity.class).putExtra(
						"FromNotification", true), 0);

			}
			mNotificationManager = (NotificationManager) this
					.getSystemService(Context.NOTIFICATION_SERVICE);

			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
					this)
					.setSmallIcon(R.drawable.ic_launcher)
					.setContentTitle("BlinkWallet通知")
					.setStyle(
							new NotificationCompat.BigTextStyle().bigText(msg))
					.setContentText(msg);

			mBuilder.setContentIntent(contentIntent);
			mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());

		} catch (Exception e) {
			e.printStackTrace();
		}
		 **/
	}
}
