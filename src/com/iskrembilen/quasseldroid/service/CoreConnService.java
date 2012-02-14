/**
    QuasselDroid - Quassel client for Android
 	Copyright (C) 2011 Ken Børge Viktil
 	Copyright (C) 2011 Magnus Fjell
 	Copyright (C) 2011 Martin Sandsmark <martin.sandsmark@kde.org>

    This program is free software: you can redistribute it and/or modify it
    under the terms of the GNU General Public License as published by the Free
    Software Foundation, either version 3 of the License, or (at your option)
    any later version, or under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either version 2.1 of
    the License, or (at your option) any later version.

 	This program is distributed in the hope that it will be useful,
 	but WITHOUT ANY WARRANTY; without even the implied warranty of
 	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 	GNU General Public License for more details.

    You should have received a copy of the GNU General Public License and the
    GNU Lesser General Public License along with this program.  If not, see
    <http://www.gnu.org/licenses/>.
 */

package com.iskrembilen.quasseldroid.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Observer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import com.iskrembilen.quasseldroid.Buffer;
import com.iskrembilen.quasseldroid.BufferInfo;
import com.iskrembilen.quasseldroid.IrcMessage;
import com.iskrembilen.quasseldroid.IrcUser;
import com.iskrembilen.quasseldroid.Network;
import com.iskrembilen.quasseldroid.NetworkCollection;
import com.iskrembilen.quasseldroid.QuasseldroidNotificationManager;
import com.iskrembilen.quasseldroid.R;
import com.iskrembilen.quasseldroid.IrcMessage.Flag;
import com.iskrembilen.quasseldroid.io.CoreConnection;

/**
 * This Service holds the connection to the core from the phone, it handles all
 * the communication with the core. It talks to CoreConnection
 */

public class CoreConnService extends Service {

	private static final String TAG = CoreConnService.class.getSimpleName();

	/**
	 * Id for result code in the resultReciver that is going to notify the
	 * activity currently on screen about the change
	 */
	public static final int CONNECTION_DISCONNECTED = 0;
	public static final int CONNECTION_CONNECTED = 1;
	public static final int NEW_CERTIFICATE = 2;
	public static final int UNSUPPORTED_PROTOCOL = 3;
	public static final int INIT_PROGRESS = 4;
	public static final int INIT_DONE = 5;

	public static final String STATUS_KEY = "status";
	public static final String CERT_KEY = "certificate";
	public static final String PROGRESS_KEY = "networkname";



	// The original QuasselDroid URL matching pattern
	private Pattern nonWebURLPattern = Pattern.compile("((mailto\\:|(news|ftp(s?))\\://){1}\\S+)", Pattern.CASE_INSENSITIVE);
	
	// This regular expression string has been copied wholesale from the android.util.Patterns class' WEB_URL constant,
	// but that class is only available in Android 2.2, but we currently build against 2.1 and I'd really rather not
	// bump our minimum required version just for ONE CONSTANT... If, at some point in the future, we make 2.2 or
	// higher the minimum version, we can replace this massive string with the aforementioned WEB_URL constant
	private Pattern WebURLPattern = Pattern.compile("((?:(http|https|Http|Https|rtsp|Rtsp):\\/\\/(?:(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
	        + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
	        + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@)?)?"
	        + "((?:(?:[" + "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF" + "][" + "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF" + "\\-]{0,64}\\.)+"   // named host
	        + "(?:"
	                + "(?:aero|arpa|asia|a[cdefgilmnoqrstuwxz])"
	                + "|(?:biz|b[abdefghijmnorstvwyz])"
	                + "|(?:cat|com|coop|c[acdfghiklmnoruvxyz])"
	                + "|d[ejkmoz]"
	                + "|(?:edu|e[cegrstu])"
	                + "|f[ijkmor]"
	                + "|(?:gov|g[abdefghilmnpqrstuwy])"
	                + "|h[kmnrtu]"
	                + "|(?:info|int|i[delmnoqrst])"
	                + "|(?:jobs|j[emop])"
	                + "|k[eghimnprwyz]"
	                + "|l[abcikrstuvy]"
	                + "|(?:mil|mobi|museum|m[acdeghklmnopqrstuvwxyz])"
	                + "|(?:name|net|n[acefgilopruz])"
	                + "|(?:org|om)"
	                + "|(?:pro|p[aefghklmnrstwy])"
	                + "|qa"
	                + "|r[eosuw]"
	                + "|s[abcdeghijklmnortuvyz]"
	                + "|(?:tel|travel|t[cdfghjklmnoprtvwz])"
	                + "|u[agksyz]"
	                + "|v[aceginu]"
	                + "|w[fs]"
	                + "|(?:xn\\-\\-0zwm56d|xn\\-\\-11b5bs3a9aj6g|xn\\-\\-80akhbyknj4f|xn\\-\\-9t4b11yi5a|xn\\-\\-deba0ad|xn\\-\\-g6w251d|xn\\-\\-hgbk6aj7f53bba|xn\\-\\-hlcj6aya9esc7a|xn\\-\\-jxalpdlp|xn\\-\\-kgbechtv|xn\\-\\-zckzah)"
	                + "|y[etu]"
	                + "|z[amw]))"
	        + "|(?:(?:25[0-5]|2[0-4]" // or ip address
	        + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(?:25[0-5]|2[0-4][0-9]"
	        + "|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(?:25[0-5]|2[0-4][0-9]|[0-1]"
	        + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
	        + "|[1-9][0-9]|[0-9])))"
	        + "(?:\\:\\d{1,5})?)" // plus option port number
	        + "(\\/(?:(?:[" + "a-zA-Z0-9\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF" + "\\;\\/\\?\\:\\@\\&\\=\\#\\~"  // plus option query params
	        + "\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])|(?:\\%[a-fA-F0-9]{2}))*)?"
	        + "(?:\\b|$)", Pattern.CASE_INSENSITIVE);
		
	private CoreConnection coreConn;
	private final IBinder binder = new LocalBinder();

	Handler incomingHandler;
	ArrayList<ResultReceiver> statusReceivers;

	SharedPreferences preferences;

	private NetworkCollection networks;

	private QuasseldroidNotificationManager notificationManager;

	private boolean preferenceParseColors;

	private OnSharedPreferenceChangeListener preferenceListener;

	private boolean preferenceUseWakeLock;

	private WakeLock wakeLock;

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public CoreConnService getService() {
			return CoreConnService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	public void onHighlightsRead(int bufferId) {
		notificationManager.notifyHighlightsRead(bufferId);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		incomingHandler = new IncomingHandler();
		notificationManager = new QuasseldroidNotificationManager(this);
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		preferenceParseColors = preferences.getBoolean(getString(R.string.preference_colored_text), false);
		preferenceUseWakeLock = preferences.getBoolean(getString(R.string.preference_wake_lock), true);
		preferenceListener = new OnSharedPreferenceChangeListener() {
			
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				if(key.equals(getString(R.string.preference_colored_text))) {
					preferenceParseColors = preferences.getBoolean(getString(R.string.preference_colored_text), false);
				} else if(key.equals(getString(R.string.preference_wake_lock))) {
					preferenceUseWakeLock = preferences.getBoolean(getString(R.string.preference_wake_lock), true);
					if(!preferenceUseWakeLock) releaseWakeLockIfExists();
					else if(preferenceUseWakeLock && isConnected()) acquireWakeLockIfEnabled();
				}
			}
		};
		preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
		statusReceivers = new ArrayList<ResultReceiver>();
	}

	@Override
	public void onDestroy() {
		this.disconnectFromCore();

	}

	public Handler getHandler() {
		return incomingHandler;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			handleIntent(intent);
		}
		return START_STICKY;

	}

	/**
	 * Handle the data in the intent, and use it to connect with CoreConnect
	 * 
	 * @param intent
	 */
	private void handleIntent(Intent intent) {
		if (coreConn != null) {
			this.disconnectFromCore();
		}
		Bundle connectData = intent.getExtras();
		String address = connectData.getString("address");
		int port = connectData.getInt("port");
		String username = connectData.getString("username");
		String password = connectData.getString("password");
		Boolean ssl = connectData.getBoolean("ssl");
		Log.i(TAG, "Connecting to core: " + address + ":" + port
				+ " with username " + username);
		networks = new NetworkCollection();
		
		acquireWakeLockIfEnabled();
		
		coreConn = new CoreConnection(address, port, username, password, ssl,
				this);
	}

	private void acquireWakeLockIfEnabled() {
		if (preferenceUseWakeLock) {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Heute ist mein tag");
			wakeLock.acquire();
			Log.i(TAG, "WakeLock acquired");
		}
	}
	private void releaseWakeLockIfExists() {
		if(wakeLock != null) {
			wakeLock.release();
			Log.i(TAG, "WakeLock released");
		}
		wakeLock = null;
	}

	public void sendMessage(int bufferId, String message) {
		coreConn.sendMessage(bufferId, message);
	}

	public void markBufferAsRead(int bufferId) {
		coreConn.requestMarkBufferAsRead(bufferId);
	}

	public void setLastSeen(int bufferId, int msgId) {
		coreConn.requestSetLastMsgRead(bufferId, msgId);
		networks.getBufferById(bufferId).setLastSeenMessage(msgId);
	}

	public void setMarkerLine(int bufferId, int msgId) {
		coreConn.requestSetMarkerLine(bufferId, msgId);
		networks.getBufferById(bufferId).setMarkerLineMessage(msgId);
	}
	
	public void unhideTempHiddenBuffer(int bufferId) {
		coreConn.requestUnhideTempHiddenBuffer(bufferId);
		networks.getBufferById(bufferId).setTemporarilyHidden(false);
	}

	public Buffer getBuffer(int bufferId, Observer obs) {
		Buffer buffer = networks.getBufferById(bufferId);
		if(obs != null)
			buffer.addObserver(obs);
		return buffer;
	}

	public void getMoreBacklog(int bufferId, int amount){
		Log.d(TAG, "Fetching more backlog");
		coreConn.requestMoreBacklog(bufferId, amount);
	}

	public NetworkCollection getNetworkList(Observer obs) {
		return networks;
	}

	/**
	 * Checks if there is a highlight in the message and then sets the flag of
	 * that message to highlight
	 * 
	 * @param buffer
	 *            the buffer the message belongs to
	 * @param message
	 *            the message to check
	 */
	public void checkMessageForHighlight(Buffer buffer, IrcMessage message) {
		if (message.type == IrcMessage.Type.Plain) {
			String nick = networks.getNetworkById(buffer.getInfo().networkId).getNick();
			if(nick == null) {
				Log.e(TAG, "Nick is null in check message for highlight");
				return;
			} else if(nick.equals("")) return;
			Pattern regexHighlight = Pattern.compile(".*(?<!(\\w|\\d))" + Pattern.quote(networks.getNetworkById(buffer.getInfo().networkId).getNick()) + "(?!(\\w|\\d)).*", Pattern.CASE_INSENSITIVE);
			Matcher matcher = regexHighlight.matcher(message.content);
			if (matcher.find()) {
				message.setFlag(IrcMessage.Flag.Highlight);
				// FIXME: move to somewhere proper
			}
		}
	}

	/**
	 * Check if a message contains a URL that we need to support to open in a
	 * browser Set the url fields in the message so we can get it later
	 * 
	 * @param message
	 *            ircmessage to check
	 */
	public void checkForURL(IrcMessage message) {
		// First look for "Web" URLs
		Matcher webMatcher = WebURLPattern.matcher(message.content);
		while (webMatcher.find()) {
			message.addURL(this, webMatcher.group());
		}

		// Now check for things like FTP, News, Mailto, IRC, etc.
		Matcher miscMatcher = nonWebURLPattern.matcher(message.content);
		while (miscMatcher.find()) {
			message.addURL(this, miscMatcher.group());
		}
	}

	/**
	 * Parse mIRC style codes in IrcMessage
	 */
	public void parseStyleCodes(IrcMessage message) {
		if(!preferenceParseColors) return;
		
		final char boldIndicator = 2;
		final char normalIndicator = 15;
		final char italicIndicator = 29;
		final char underlineIndicator = 31;

		String content = message.content.toString();

		if (content.indexOf(boldIndicator) == -1 
				&& content.indexOf(italicIndicator) == -1
				&& content.indexOf(underlineIndicator) == -1)
			return;

		SpannableStringBuilder newString = new SpannableStringBuilder(message.content);

		while (true) {
			content = newString.toString();

			int start = content.indexOf(boldIndicator);
			int end = content.indexOf(boldIndicator, start+1);
			int style = Typeface.BOLD;

			if (start == -1) {
				start = content.indexOf(italicIndicator);
				end = content.indexOf(italicIndicator, start+1);
				style = Typeface.ITALIC;
			}

			if (start == -1) {
				start = content.indexOf(underlineIndicator);
				end = content.indexOf(underlineIndicator, start+1);
				style = -1;
			}

			if (start == -1)
				break;

			if (end == -1)
				end = content.indexOf(normalIndicator, start);

			if (end == -1)
				end = content.length()-1;

			if(start==end) {
				newString.delete(start, start+1);
				break;
			}

			if (style == -1) {
				newString.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			} else {
				newString.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			}

			if (content.charAt(end) == boldIndicator
					|| content.charAt(end) == italicIndicator
					|| content.charAt(end) == normalIndicator
					|| content.charAt(end) == underlineIndicator)
				newString.delete(end, end+1);

			newString.delete(start, start+1);
		}
		message.content = newString;
	}

	/**
	 * Parse mIRC color codes in IrcMessage
	 */
	public void parseColorCodes(IrcMessage message) {
		if(!preferenceParseColors) return;
		
		final char formattingIndicator = 3;

		/*
		 * if (message.content.toString().indexOf(formattingIndicator) == -1)
		 * return;
		 */
		String content = message.content.toString();
		if (content.indexOf(formattingIndicator) == -1)
			return;

		SpannableStringBuilder newString = new SpannableStringBuilder(message.content);



		while (true) {
			content = newString.toString();
			// ^C5,12colored text and background^C
			int start = content.indexOf(formattingIndicator);

			if (start == -1) {
				break;
			}

			int end = start + 1;
			int fg = -1;
			int bg = -1;
			if (end < content.length()) {
				if (Character.isDigit(content.charAt(end))) {
					if (Character.isDigit(content.charAt(end + 1))) {
						fg = Integer.parseInt(content.substring(end, end + 2));
						end += 2;
					} else {
						fg = Integer.parseInt(content.substring(end, end+1));
						end += 1;
					}
				}

				if (content.charAt(end) == ',') {
					end++;

					if (Character.isDigit(content.charAt(end))) {
						if (Character.isDigit(content.charAt(end + 1))) {
							bg = Integer.parseInt(content.substring(end, end + 2));
							end += 2;
						} else {
							bg = Integer.parseInt(content.substring(end, end + 1));
							end += 1;
						}
					}
				}
			}
			int length = end - start;
			int endOfSpan = content.indexOf(formattingIndicator, end) - length;

			if (endOfSpan <= 0) // check for malformed messages
				endOfSpan = content.length() - length;

			newString.delete(start, end);
			if (fg != -1) {
				newString.setSpan(new ForegroundColorSpan(getResources()
						.getColor(mircCodeToColor(fg))), start, endOfSpan,
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			}
			if (bg != -1) {
				newString.setSpan(new BackgroundColorSpan(getResources()
						.getColor(mircCodeToColor(bg))), start, endOfSpan,
						Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			}
		}
		message.content = newString; // BURN IN HELL JAVA
	}

	private int mircCodeToColor(int code) {
		int color;
		switch (code) {
		case 0: // white
			color = R.color.ircmessage_white;
			break;
		case 1: // black
			color = R.color.ircmessage_black;
			break;
		case 2: // blue (navy)
			color = R.color.ircmessage_blue;
			break;
		case 3: // green
			color = R.color.ircmessage_green;
			break;
		case 4: // red
			color = R.color.ircmessage_red;
			break;
		case 5: // brown (maroon)
			color = R.color.ircmessage_brown;
			break;
		case 6: // purple
			color = R.color.ircmessage_purple;
			break;
		case 7: // orange (olive)
			color = R.color.ircmessage_orange;
			break;
		case 8: // yellow
			color = R.color.ircmessage_yellow;
			break;
		case 9: // light green (lime)
			color = R.color.ircmessage_light_green;
			break;
		case 10: // teal (a green/blue cyan)
			color = R.color.ircmessage_teal;
			break;
		case 11: // light cyan (cyan) (aqua)
			color = R.color.ircmessage_light_cyan;
			break;
		case 12: // light blue (royal)
			color = R.color.ircmessage_light_blue;
			break;
		case 13: // pink (light purple) (fuchsia)
			color = R.color.ircmessage_pink;
			break;
		case 14: // grey
			color = R.color.ircmessage_gray;
			break;
		default:
			color = R.color.ircmessage_normal_color;
		}
		return color;
	}

	public void disconnectFromCore() {
		if (coreConn != null)
			coreConn.disconnect();
		releaseWakeLockIfExists();
	}

	public boolean isConnected() {
		return coreConn.isConnected();
	}

	/**
	 * Register a resultReceiver with this server, that will get notification
	 * when a change happens with the connection to the core Like when the
	 * connection is lost.
	 * 
	 * @param resultReceiver
	 *            - Receiver that will get the status updates
	 */
	public void registerStatusReceiver(ResultReceiver resultReceiver) {
		statusReceivers.add(resultReceiver);
		if (coreConn != null && coreConn.isConnected()) {
			resultReceiver.send(CoreConnService.CONNECTION_CONNECTED, null);
		} else {
			resultReceiver.send(CoreConnService.CONNECTION_DISCONNECTED, null);
		}

	}

	/**
	 * Unregister a receiver when you don't want any more updates.
	 * 
	 * @param resultReceiver
	 */
	public void unregisterStatusReceiver(ResultReceiver resultReceiver) {
		statusReceivers.remove(resultReceiver);
	}

	private void sendStatusMessage(int messageId, Bundle bundle) {
		for (ResultReceiver statusReceiver : statusReceivers) {
			statusReceiver.send(messageId, bundle);
		}
	}

	/**
	 * Handler of incoming messages from CoreConnection, since it's in another
	 * read thread.
	 */
	class IncomingHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			if (msg == null) {
				Log.e(TAG, "Msg was null?");
				return;
			}

			Buffer buffer;
			IrcMessage message;
			Bundle bundle;
			IrcUser user;
			String bufferName;
			switch (msg.what) {
			case R.id.NEW_BACKLOGITEM_TO_SERVICE:
				/**
				 * New message on one buffer so update that buffer with the new
				 * message
				 */
				message = (IrcMessage) msg.obj;
				buffer = networks.getBufferById(message.bufferInfo.id);
				
				if (buffer == null) {
					Log.e(TAG, "A messages buffer is null:" + message);
					return;
				}

				if (!buffer.hasMessage(message)) {
					/**
					 * Check if we are highlighted in the message, TODO: Add
					 * support for custom highlight masks
					 */
					checkMessageForHighlight(buffer, message);
					checkForURL(message);
					parseColorCodes(message);
					parseStyleCodes(message);
					buffer.addBacklogMessage(message);
				} else {
					Log.e(TAG, "Getting message buffer already have " + buffer.getInfo().name);
				}
				break;
			case R.id.NEW_MESSAGE_TO_SERVICE:
				/**
				 * New message on one buffer so update that buffer with the new
				 * message
				 */
				message = (IrcMessage) msg.obj;
				buffer = networks.getBufferById(message.bufferInfo.id);
				if (buffer == null) {
					Log.e(TAG, "A messages buffer is null: " + message);
					return;
				}

				if (!buffer.hasMessage(message)) {
					/**
					 * Check if we are highlighted in the message, TODO: Add
					 * support for custom highlight masks
					 */
					checkMessageForHighlight(buffer, message);
					parseColorCodes(message);
					parseStyleCodes(message);
					if ((message.isHighlighted() && !buffer.isDisplayed()) || (buffer.getInfo().type == BufferInfo.Type.QueryBuffer && ((message.flags & Flag.Self.getValue()) == 0))) {
						notificationManager.notifyHighlight(buffer.getInfo().id);
					}
					

					checkForURL(message);
					buffer.addMessage(message);
					
					if (buffer.isTemporarilyHidden()) {
						unhideTempHiddenBuffer(buffer.getInfo().id);
					}
				} else {
					Log.e(TAG, "Getting message buffer already have " + buffer.toString());
				}
				break;

			case R.id.NEW_BUFFER_TO_SERVICE:
				/**
				 * New buffer received, so update out channel holder with the
				 * new buffer
				 */
				buffer = (Buffer) msg.obj;
				networks.addBuffer(buffer);

				break;
			case R.id.ADD_MULTIPLE_BUFFERS:
				/**
				 * Complete list of buffers received
				 */
				for (Buffer tmp : (Collection<Buffer>) msg.obj) {
					networks.addBuffer(tmp);
				}
				break;
			case R.id.ADD_NETWORK:
				networks.addNetwork((Network)msg.obj);
				break;
			case R.id.SET_LAST_SEEN_TO_SERVICE:
				/**
				 * Setting last seen message id in a buffer
				 */
				buffer = networks.getBufferById(msg.arg1);
				if (buffer != null) {
					Boolean hasHighlights = buffer.hasUnseenHighlight();
					buffer.setLastSeenMessage(msg.arg2);
					if(hasHighlights)
						notificationManager.notifyHighlightsRead(buffer.getInfo().id);
				} else {
					Log.e(TAG, "Getting set last seen message on unknown buffer: " + msg.arg1);
				}
				break;
			case R.id.SET_MARKERLINE_TO_SERVICE:
				/**
				 * Setting marker line message id in a buffer
				 */
				buffer = networks.getBufferById(msg.arg1);
				if (buffer != null) {
					buffer.setMarkerLineMessage(msg.arg2);
				} else {
					Log.e(TAG, "Getting set marker line message on unknown buffer: " + msg.arg1);
				}
				break;

			case R.id.CONNECTED:
				/**
				 * CoreConn has connected to a core
				 */
				notificationManager.notifyConnected();
				sendStatusMessage(CoreConnService.CONNECTION_CONNECTED, null);
				break;

			case R.id.LOST_CONNECTION:
				/**
				 * Lost connection with core, update notification
				 */
				for (ResultReceiver statusReceiver : statusReceivers) {
					if (msg.obj != null) { // Have description of what is wrong,
						// used only for login atm
						bundle = new Bundle();
						bundle.putString(CoreConnService.STATUS_KEY, (String) msg.obj);
						statusReceiver.send(CoreConnService.CONNECTION_DISCONNECTED, bundle);
					} else {
						statusReceiver.send(CoreConnService.CONNECTION_DISCONNECTED, null);
					}
				}
				notificationManager.notifyDisconnected();
				releaseWakeLockIfExists();
				break;
			case R.id.NEW_USER_ADDED:
				/**
				 * New IrcUser added
				 */
				user = (IrcUser) msg.obj;
				networks.getNetworkById(msg.arg1).onUserJoined(user);
				break;

			case R.id.SET_BUFFER_ORDER:
				/**
				 * Buffer order changed so set the new one
				 */
				ArrayList<Integer> a = (((Bundle)msg.obj).getIntegerArrayList("keys"));
				ArrayList<Integer> b = ((Bundle)msg.obj).getIntegerArrayList("buffers");
				ArrayList<Integer> c = new ArrayList<Integer>();
				for(Network net : networks.getNetworkList()) {
					c.add(net.getStatusBuffer().getInfo().id);
					for(Buffer buf : net.getBuffers().getRawBufferList()) {
						if(buf.getInfo().id == 4) System.out.println(buf.getInfo().name);
						c.add(buf.getInfo().id);
					}
				}
				Collections.sort(a);
				Collections.sort(b);
				Collections.sort(c);
				
				if(networks == null) throw new RuntimeException("Networks are null when setting buffer order");
				if(networks.getBufferById(msg.arg1) == null) throw new RuntimeException("Buffer is null when setting buffer order, bufferid " + msg.arg1 + " order " + msg.arg2 + " for this buffers keys: " + a.toString() + " corecon buffers: " + b.toString() + " service buffers: " + c.toString());
				networks.getBufferById(msg.arg1).setOrder(msg.arg2);
				break;

			case R.id.SET_BUFFER_TEMP_HIDDEN:
				/**
				 * Buffer has been marked as temporary hidden, update buffer
				 */
				networks.getBufferById(msg.arg1).setTemporarilyHidden((Boolean) msg.obj);
				break;

			case R.id.SET_BUFFER_PERM_HIDDEN:
				/**
				 * Buffer has been marked as permanently hidden, update buffer
				 */
				networks.getBufferById(msg.arg1).setPermanentlyHidden((Boolean) msg.obj);
				break;

			case R.id.INVALID_CERTIFICATE:
				/**
				 * Received a mismatching certificate
				 */
			case R.id.NEW_CERTIFICATE:
				/**
				 * Received a new, unseen certificate
				 */
				bundle = new Bundle();
				bundle.putString(CERT_KEY, (String) msg.obj);
				sendStatusMessage(CoreConnService.NEW_CERTIFICATE, bundle);
				break;
			case R.id.SET_BUFFER_ACTIVE:
				/**
				 * Set buffer as active or parted
				 */
				networks.getBufferById(msg.arg1).setActive((Boolean)msg.obj);
				break;
			case R.id.UNSUPPORTED_PROTOCOL:
				/**
				 * The protocol version of the core is not supported so tell user it is to old
				 */
				sendStatusMessage(CoreConnService.UNSUPPORTED_PROTOCOL, null);
				break;
			case R.id.INIT_PROGRESS:
				bundle = new Bundle();
				bundle.putString(PROGRESS_KEY, (String)msg.obj);
				sendStatusMessage(CoreConnService.INIT_PROGRESS, bundle);
				break;
			case R.id.INIT_DONE:
				sendStatusMessage(CoreConnService.INIT_DONE, null);
				break;
			case R.id.USER_PARTED:
				bundle = (Bundle) msg.obj;
				networks.getNetworkById(msg.arg1).onUserParted(bundle.getString("nick"), bundle.getString("buffer"));
				break;
			case R.id.USER_QUIT:
				networks.getNetworkById(msg.arg1).onUserQuit((String)msg.obj);
				break;
			case R.id.USER_JOINED:
				bundle = (Bundle) msg.obj;
				user = networks.getNetworkById(msg.arg1).getUserByNick(bundle.getString("nick"));
				String modes = (String)bundle.get("mode");
				networks.getNetworkById(msg.arg1).getBuffers().getBuffer(msg.arg2).getUsers().addUser(user, modes);
				break;
			case R.id.USER_CHANGEDNICK:
				bundle = (Bundle) msg.obj;
				user = networks.getNetworkById(msg.arg1).getUserByNick(bundle.getString("oldNick"));
				user.changeNick(bundle.getString("newNick"));
				break;
			case R.id.USER_ADD_MODE:
				bundle = (Bundle) msg.obj;
				bufferName = bundle.getString("channel");
				user = networks.getNetworkById(msg.arg1).getUserByNick(bundle.getString("nick"));
				for(Buffer buf : networks.getNetworkById(msg.arg1).getBuffers().getRawBufferList()) {
					if(buf.getInfo().name.equals(bufferName)) {
						buf.getUsers().addUserMode(user, bundle.getString("mode"));
						break;
					}
				}
				break;
			case R.id.USER_REMOVE_MODE:
				bundle = (Bundle) msg.obj;
				bufferName = bundle.getString("channel");
				Network network = networks.getNetworkById(msg.arg1); 
				if (network != null) {
					user = network.getUserByNick(bundle.getString("nick"));
					for(Buffer buf : networks.getNetworkById(msg.arg1).getBuffers().getRawBufferList()) {
							if(buf.getInfo().name.equals(bufferName)) {
								buf.getUsers().removeUserMode(user, bundle.getString("mode"));
								break;
							}
					}
				}
				break;
			}			
		}
	}

	public boolean isInitComplete() {
		if(coreConn == null) return false;
		return coreConn.isInitComplete();
	}

}
