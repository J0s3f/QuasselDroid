package com.iskrembilen.quasseldroid;

import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.iskrembilen.quasseldroid.gui.BufferActivity;
import com.iskrembilen.quasseldroid.gui.LoginActivity;

public class QuasseldroidNotificationManager {

	//TODO: lots of duplicate code in this class, clean up

	private Context context;
	private SharedPreferences preferences;
	private List<Integer> highlightedBuffers;
	NotificationManager notifyManager;
	private boolean notified;

	public QuasseldroidNotificationManager(Context context) {
		this.context = context;
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		highlightedBuffers = new ArrayList<Integer>();
		notified = false;
	}

	public void notifyHighlightsRead(int bufferId) {
		highlightedBuffers.remove((Integer)bufferId);
		if(highlightedBuffers.size() == 0) {
			CharSequence text = context.getText(R.string.notification_connected);
			CharSequence ticker = (notified) ? "" : text;
			
			int icon = R.drawable.icon;
			int temp_flags = Notification.FLAG_ONGOING_EVENT;			

			// Set the icon, scrolling text and timestamp
			Notification notification = new Notification(icon, ticker, System.currentTimeMillis());
			notification.flags |= temp_flags;
			notification.number = 0;

			// The PendingIntent to launch our activity if the user selects this notification
			PendingIntent contentIntent;

			Intent launch = new Intent(context, BufferActivity.class);
			launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			contentIntent = PendingIntent.getActivity(context, 0, launch, 0);

			// Set the info for the views that show in the notification panel.
			notification.setLatestEventInfo(context, context.getText(R.string.app_name), text,
					contentIntent);

			// Send the notification.
			notifyManager.notify(R.id.NOTIFICATION, notification);
			notified = true;
		}else{
			notifyHighlight(null);
		}
	}

	public void notifyConnected() {
		CharSequence text = context.getText(R.string.notification_connected);
		CharSequence ticker = (notified) ? "" : text;
		int icon = R.drawable.icon;
		int temp_flags = Notification.FLAG_ONGOING_EVENT;			

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(icon, ticker, System.currentTimeMillis());
		notification.flags |= temp_flags;
		notification.number = highlightedBuffers.size();
		
		if (preferences.getBoolean(context.getString(R.string.preference_notify_connect), false)) {
			if (preferences.getBoolean(context.getString(R.string.preference_notification_vibrate), true)) {
				notification.defaults |= Notification.DEFAULT_VIBRATE;
			}
		
			if (preferences.getBoolean(context.getString(R.string.preference_notification_sound), true)) {
				notification.sound = Uri.parse(preferences.getString(context.getString(R.string.preference_notification_connect_sound_file), ""));
				if (notification.sound.equals(Uri.EMPTY))
					notification.defaults |= Notification.DEFAULT_SOUND;
			}
		}
		
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent;

		Intent launch = new Intent(context, BufferActivity.class);
		launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		contentIntent = PendingIntent.getActivity(context, 0, launch, 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(context, context.getText(R.string.app_name), text,
				contentIntent);
		
		// Send the notification.
		notifyManager.notify(R.id.NOTIFICATION, notification);
		notified = true;
	}
	
	public void notifyConnecting() {
		CharSequence text = context.getText(R.string.notification_connecting);
		CharSequence ticker = (notified) ? "" : text;
		int icon = R.drawable.connecting;
		int temp_flags = Notification.FLAG_ONGOING_EVENT;			

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(icon, ticker, System.currentTimeMillis());
		notification.flags |= temp_flags;
		notification.number = 1;
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent;

		Intent launch = new Intent(context, BufferActivity.class);
		launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		contentIntent = PendingIntent.getActivity(context, 0, launch, 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(context, context.getText(R.string.app_name), text,
				contentIntent);

		// Send the notification.
		notifyManager.notify(R.id.NOTIFICATION, notification);
		notified = true;
	}	

	public void notifyHighlight(Integer bufferId) {
		if(bufferId != null && !highlightedBuffers.contains(bufferId)) {
			highlightedBuffers.add(bufferId);			
		}

		CharSequence text = "You have highlights on " + highlightedBuffers.size() + " buffers";
		CharSequence ticker = (notified) ? "" : text;
		int icon = R.drawable.highlight;
		int temp_flags = Notification.FLAG_ONGOING_EVENT;			

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(icon, ticker, System.currentTimeMillis());
		notification.flags |= temp_flags;
		notification.number = highlightedBuffers.size();
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent;

		Intent launch = new Intent(context, BufferActivity.class);
		launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		contentIntent = PendingIntent.getActivity(context, 0, launch, 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(context, context.getText(R.string.app_name), text,
				contentIntent);
		if(bufferId != null) {
			if(preferences.getBoolean(context.getString(R.string.preference_notification_sound), false)) {
				notification.sound = Uri.parse(preferences.getString(context.getString(R.string.preference_notification_sound_file), ""));
				if (notification.sound.equals(Uri.EMPTY))
					notification.defaults |= Notification.DEFAULT_SOUND;
			}
			if(preferences.getBoolean(context.getString(R.string.preference_notification_light), false))
				notification.defaults |= Notification.DEFAULT_LIGHTS;
			if(preferences.getBoolean(context.getString(R.string.preference_notification_vibrate), false))
				notification.defaults |= Notification.DEFAULT_VIBRATE;	
		}
		// Send the notification.
		notifyManager.notify(R.id.NOTIFICATION, notification);
		notified = true;
	}

	public void notifyDisconnected() {
		CharSequence text = context.getText(R.string.notification_disconnected);
		CharSequence ticker = (notified) ? "" : text;
		int icon = R.drawable.inactive;
		int temp_flags = Notification.FLAG_ONLY_ALERT_ONCE;

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(icon, ticker, System.currentTimeMillis());
		notification.flags |= temp_flags;
		notification.number = 1;
		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent;

		Intent launch = new Intent(context, LoginActivity.class);
		contentIntent = PendingIntent.getActivity(context, 0, launch, 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(context, context.getText(R.string.app_name), text,
				contentIntent);
		// Send the notification.
		notifyManager.notify(R.id.NOTIFICATION, notification);
		notified = true;
	}
}
