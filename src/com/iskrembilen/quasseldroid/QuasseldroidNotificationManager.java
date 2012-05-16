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
	
	private Notification prepareNotification(int icon, CharSequence text) {
		return prepareNotification(icon, text, BufferActivity.class);
	}
	
	private Notification prepareNotification(int icon, CharSequence text, Class<?> intentTarget) {
		CharSequence ticker = "";
		int highlightNumber = highlightedBuffers.size();
		
		if (!notified) {
			// The first time we show a notification, we show a ticker text and set the displayed
			// number to 1 (which is required in order to activate that feature - jumping straight
			// from 0 to a number >1 won't show the number overlayed on the icon, unfortunately).
			// Ideally this number should be set back to 0 ASAP, necessitating another notification
			// event, but in practice it's set to 1 by the "connecting..." notification, then reset
			// to 0 once the connection completes, which is usually only a few seconds later.
			ticker = text;
			highlightNumber = 1;
		}

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(icon, ticker, System.currentTimeMillis());
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.number = highlightNumber;

		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent;

		Intent launch = new Intent(context, intentTarget);
		launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		contentIntent = PendingIntent.getActivity(context, 0, launch, 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(context, context.getText(R.string.app_name), text,
				contentIntent);

		return notification;
	}
	
	private void showNotification(Notification notification) {
		notifyManager.notify(R.id.NOTIFICATION, notification);
		notified = true;
	}
	
	/**
	 * Short wrapper function to easily handle the most common notifications
	 * 
	 * @param icon
	 * @param text
	 */
	private void doNotify(int icon, CharSequence text) {
		showNotification(prepareNotification(icon, text));
	}

	public void notifyHighlightsRead(int bufferId) {
		highlightedBuffers.remove((Integer)bufferId);
		if(highlightedBuffers.size() == 0) {
			doNotify(R.drawable.icon, context.getText(R.string.notification_connected));
		}else{
			notifyHighlight(null);
		}
	}

	public void notifyConnected() {
		Notification notification = prepareNotification(R.drawable.icon, context.getText(R.string.notification_connected));
		
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
		
		showNotification(notification);
	}
	
	public void notifyConnecting() {
		doNotify(R.drawable.connecting, context.getText(R.string.notification_connecting));
	}	

	public void notifyHighlight(Integer bufferId) {
		if(bufferId != null && !highlightedBuffers.contains(bufferId)) {
			highlightedBuffers.add(bufferId);			
		}
		
		int hlBufferCount = highlightedBuffers.size();
		Notification notification = prepareNotification(R.drawable.highlight, "You have highlights on " + hlBufferCount +
				" buffer" + (hlBufferCount == 1 ? "" : "s"));
		
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
		
		showNotification(notification);
	}

	public void notifyDisconnected() {
		Notification notification = prepareNotification(R.drawable.inactive, context.getText(R.string.notification_disconnected), LoginActivity.class);
		notification.flags ^= Notification.FLAG_ONGOING_EVENT;
		notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
		showNotification(notification);
	}
}
