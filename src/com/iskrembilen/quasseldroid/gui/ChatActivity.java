 /*
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

package com.iskrembilen.quasseldroid.gui;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.*;

import com.iskrembilen.quasseldroid.Buffer;
import com.iskrembilen.quasseldroid.BufferInfo;
import com.iskrembilen.quasseldroid.IrcMessage;
import com.iskrembilen.quasseldroid.IrcUser;
import com.iskrembilen.quasseldroid.UserCollection;
import com.iskrembilen.quasseldroid.IrcMessage.Type;
import com.iskrembilen.quasseldroid.R;
import com.iskrembilen.quasseldroid.service.CoreConnService;

public class ChatActivity extends Activity{


	public static final int MESSAGE_RECEIVED = 0;

	public static final String IMGUR_DEVELOPER_KEY = "3b809e186ddf9c91f4c0905fd5891ba8";
	
	private BacklogAdapter adapter;
	private ListView backlogList;


	private int dynamicBacklogAmout;

	SharedPreferences preferences;

	private ResultReceiver statusReceiver;
	
	private int sentMessageHistoryViewIndex = 0;
	private String sentMessageHistoryLastRequestedMessage = "";
	
	private static String storedInputText = "";

	private static final String TAG = ChatActivity.class.getSimpleName();



	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.chat_layout);

		preferences = PreferenceManager.getDefaultSharedPreferences(this);

		adapter = new BacklogAdapter(this, null);
		backlogList = ((ListView)findViewById(R.id.chatBacklogList));
		backlogList.setCacheColorHint(0xffffff);
		backlogList.setAdapter(adapter);
		backlogList.setOnScrollListener(new BacklogScrollListener(5));
		backlogList.setDividerHeight(0);
		backlogList.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
		//View v = backlogList.getChildAt(backlogList.getChildCount());
		backlogList.setSelection(backlogList.getChildCount());

		findViewById(R.id.ChatInputView).setOnKeyListener(inputfieldKeyListener);
		backlogList.setOnItemLongClickListener(itemLongClickListener);
		((ListView) findViewById(R.id.chatBacklogList)).setCacheColorHint(0xffffff);

        backlogList.setOnTouchListener(gestureListener);
        
        Intent intent = getIntent();
        CharSequence sharedString = intent.getCharSequenceExtra(BufferActivity.BUFFER_SHARE_EXTRA_TEXT);
        Uri sharedUri = (Uri) intent.getParcelableExtra(BufferActivity.BUFFER_SHARE_EXTRA_IMAGE);
        if (sharedString != null && sharedString.length() > 0) {
        	((EditText) findViewById(R.id.ChatInputView)).setText(sharedString);
        } else if (sharedUri != null && sharedUri.toString().length() > 0) {
        	try {
    		ContentResolver cr = getContentResolver();
    		final InputStream is = cr.openInputStream(sharedUri);
    		
        	new AlertDialog.Builder(this)
	            .setIcon(android.R.drawable.ic_dialog_alert)
	            .setTitle("Proceed with Image Upload?")
	            .setMessage("Your shared image will first be uploaded to Imgur. Do you wish to proceed?")
	            .setPositiveButton("Yes; Upload Image", new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialog, int which) {
	                	
	                	AsyncHttpClient client = new AsyncHttpClient();
	                	
	                	RequestParams params = new RequestParams();
	                	params.put("image", is);
	                	
		                client.post(ChatActivity.this, "http://api.imgur.com/2/upload.json?key=" + IMGUR_DEVELOPER_KEY, params, new JsonHttpResponseHandler() {
	                		ProgressDialog waitDialog;
	                		
	                	    @Override
	                	    public void onSuccess(JSONObject json) {
	                	    	waitDialog.hide();
	                	    	
	    	                	try {
			                	    Log.d(TAG, "JSON Response: " + json.toString());
			                	    String imgurUrl = json.getJSONObject("upload").getJSONObject("links").getString("imgur_page");
			                	    
			                	    EditText inputView = (EditText) findViewById(R.id.ChatInputView);
			                	    if (inputView.getText().length() > 0) {
			                	    	if (inputView.getText().charAt(inputView.getText().length() - 1) != ' ') {
			                	    		inputView.append(" ");
			                	    	}
			                	    }
		                	    	inputView.append(imgurUrl);
	    	                	} catch (JSONException ex) {
	    	                		Log.e(TAG, "Shared Media JSONException: " + ex.getMessage());
	    						}
	                	    }
	                	    
	                	    private void runInBackground() {
	                	    	waitDialog.hide();
	                	    	Toast backgroundInfo = Toast
	                	    			.makeText(ChatActivity.this, "Resulting Imgur URL will be inserted into the input area when the upload completes.", Toast.LENGTH_LONG);
	                	    	backgroundInfo.setGravity(Gravity.TOP, 0, 0);
	                	    	backgroundInfo.show();
	                	    }
	                	    
	                	    @Override
	                	    public void onStart() {
	                	    	Log.d(TAG, "AysncHttpClient Start");
	                	    	
	                	    	waitDialog = new ProgressDialog(ChatActivity.this);
	                	    	waitDialog.setIndeterminate(true);
	                	    	waitDialog.setMessage("Image uploading; please wait...");
	                	    	waitDialog.setCancelable(true);
	                	    	waitDialog.setCanceledOnTouchOutside(true);
	                	    	waitDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
									@Override
									public void onCancel(DialogInterface arg0) {
										runInBackground();
									}
								});
	                	    	waitDialog.setButton("Run in Background", new DialogInterface.OnClickListener() {
	                	    		@Override
	                	    		public void onClick(DialogInterface dialog, int which) {
	                	    			runInBackground();
	                	    		}
	                	    	});
	                	    	waitDialog.show();
	                	    }
	                	    
	                	    @Override
	                	    public void onFailure(Throwable e) {
	                	    	Log.d(TAG, "AysncHttpClient Failed: " + e.getMessage());
	                	    	waitDialog.hide();
	                	    	
	                	    	new AlertDialog.Builder(ChatActivity.this)
	                	    		.setIcon(android.R.drawable.ic_dialog_alert)
	                	    		.setTitle("Upload Failed!")
	                	    		.setMessage("Uploading your image failed with this error: " + e.getMessage())
	                	    		.show();
	                	    }
	                	    
	                	    @Override
	                	    public void onFinish() {
	                	    	Log.d(TAG, "AysncHttpClient Finished");
	                	    	waitDialog.hide();
	                	    }
	                	});
	                }
	            })
	            .setNegativeButton("Cancel", null)
	            .show();
        	} catch (FileNotFoundException ex) {
        		Log.e(TAG, "Shared Media FileNotFoundException: " + ex.getMessage());
        	}
        }

		statusReceiver = new ResultReceiver(null) {

			@Override
			protected void onReceiveResult(int resultCode, Bundle resultData) {
				if (resultCode==CoreConnService.CONNECTION_DISCONNECTED) finish();
				super.onReceiveResult(resultCode, resultData);
			}

		};
	}

	public static void readBytesFromInputStreamIntoBAOS(InputStream is, ByteArrayOutputStream byteBuffer) throws IOException {
		byte[] buffer = new byte[1024];

		int len = 0;
		while ((len = is.read(buffer)) != -1) {
		    byteBuffer.write(buffer, 0, len);
		}
	}

	OnItemLongClickListener itemLongClickListener = new OnItemLongClickListener() {

		private void openUrl(String url) {
			try {
				if (url.indexOf("://") == -1) {
					// We match URLs without a scheme, assuming them to be HTTP, but Android requires a scheme to be present
					url = "http://" + url;
				}
				
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(browserIntent);
			} catch (ActivityNotFoundException ex) {
				Toast.makeText(ChatActivity.this, "No handler found for that URL.", Toast.LENGTH_SHORT).show();
			}
		}
		
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			IrcMessage message = adapter.getItem(position);
			if (message.hasURLs()) {
				final ArrayList<String> urls = (ArrayList<String>) message.getURLs();

				if (urls.size() == 1 ){ //Open the URL
					openUrl(urls.get(0));
				} else if (urls.size() > 1 ){
					//Show list of URLs, and make it possible to choose one
					AlertDialog.Builder urlListBuilder = new AlertDialog.Builder(ChatActivity.this);
					final CharSequence[] urlArray = urls.toArray(new CharSequence[urls.size()]);
					urlListBuilder.setItems(urlArray, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int selectedUrl) {
							openUrl(urls.get(selectedUrl));
						}
					});
					AlertDialog urlList = urlListBuilder.create();
					urlList.show();
				}
			}
			return false;
		}
	};

	private OnKeyListener inputfieldKeyListener =  new View.OnKeyListener() {
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			int bufferId = adapter.buffer.getInfo().id;
			
			if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_DOWN ) { //On key down as well
				EditText inputfield = (EditText)findViewById(R.id.ChatInputView);
				String inputText = inputfield.getText().toString();

				if ( ! "".equals(inputText) ) {
					boundConnService.sendMessage(bufferId, inputText);
					sentMessageHistoryViewIndex = 0;
					inputfield.setText("");
				}

				return true;
			} else if (keyCode == KeyEvent.KEYCODE_TAB && event.getAction() == KeyEvent.ACTION_DOWN) {
				onSearchRequested(); // lawl
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
				String oldMessage = boundConnService.getSentMessage(bufferId, --sentMessageHistoryViewIndex);
				if (oldMessage == null) {
					++sentMessageHistoryViewIndex;
					sentMessageHistoryLastRequestedMessage = "";
				} else {
					EditText inputfield = (EditText) findViewById(R.id.ChatInputView);
					if (inputfield.getText().length() > 0 && sentMessageHistoryViewIndex == -1) {
						// This is our first move into the history, so store the existing message in the list;
						boundConnService.addMessageToSentHistory(bufferId, inputfield.getText().toString());
					} else if (sentMessageHistoryViewIndex < -1 && inputfield.getText().length() > 0 &&
							!sentMessageHistoryLastRequestedMessage.equals(inputfield.getText().toString())) {
						// The message in the text box has been modified, so we'd better save that, too
						boundConnService.changeMessageInSentHistory(bufferId, sentMessageHistoryViewIndex + 1, inputfield.getText().toString());
					}
					inputfield.setText(oldMessage);
					sentMessageHistoryLastRequestedMessage = oldMessage;
				}
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
				EditText inputfield = (EditText) findViewById(R.id.ChatInputView);
				if (inputfield.getText().length() > 0 && sentMessageHistoryViewIndex == 0) {
					// Pressing down when not already viewing the history causes the current message
					//  to be sent to the history and the input area to be blanked
					boundConnService.addMessageToSentHistory(bufferId, inputfield.getText().toString());
					inputfield.setText("");
				} else {
					if (!sentMessageHistoryLastRequestedMessage.equals(inputfield.getText().toString())) {
						// The message in the text box has been modified, so we'd better save that
						boundConnService.changeMessageInSentHistory(bufferId, sentMessageHistoryViewIndex, inputfield.getText().toString());
					}
					
					String oldMessage = boundConnService.getSentMessage(bufferId, ++sentMessageHistoryViewIndex);
					if (oldMessage == null) {
						// Reached the "bottom" (most recent) of the list
						inputfield.setText("");
						sentMessageHistoryViewIndex = 0;
						sentMessageHistoryLastRequestedMessage = "";
					} else {
						inputfield.setText(oldMessage);
					}
				}
				return true;
			}
			return false;
		}
	};
	
	private void switchToBuffer(Buffer buffer) {
		if (buffer == null)
			return;
		
		Intent i = new Intent(ChatActivity.this, ChatActivity.class);
		i.putExtra(BufferActivity.BUFFER_ID_EXTRA, buffer.getInfo().id);
		i.putExtra(BufferActivity.BUFFER_NAME_EXTRA, buffer.getInfo().name);
		
		startActivity(i);
	}
	
	class FlingXListener extends SimpleOnGestureListener {
		private static final double SWIPE_MIN_DISTANCE = 0.05; // as a fraction of window width
		private static final double SWIPE_THRESHOLD_VELOCITY = 1.0; // as a fraction of window width
		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			View chatBacklogListView = findViewById(R.id.chatBacklogList);
			if (chatBacklogListView == null) {
				return false;
			}
			
			int width = chatBacklogListView.getWidth();
			double deltaX = (e2.getX() - e1.getX()) / width;
			double deltaY = (e2.getY() - e1.getY()) / chatBacklogListView.getHeight();
			double velocityFraction = Math.abs(velocityX) / width;
			
			if (Math.abs(deltaY) > Math.abs(deltaX)) {
            	// Moved more vertically than horizontally...
                return false;
            }
			
            if (velocityFraction >= SWIPE_THRESHOLD_VELOCITY) {
				if (deltaX < 0 && -deltaX > SWIPE_MIN_DISTANCE) {
					// Swiped from right to left
					Buffer nextBuffer = boundConnService.getNetworkList(null).getNextBufferFromId(adapter.getBufferId(), false);
					switchToBuffer(nextBuffer);
				} else if (deltaX > 0 && deltaX > SWIPE_MIN_DISTANCE) {
					// Swiped from left to right
					Buffer prevBuffer = boundConnService.getNetworkList(null).getPreviousBufferFromId(adapter.getBufferId(), false);
					switchToBuffer(prevBuffer);
				}
            }
			return false;
		}
	}
	private GestureDetector flingDetector = new GestureDetector(new FlingXListener());
    
	View.OnTouchListener gestureListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (flingDetector.onTouchEvent(event)) {
                return true;
            }
            return false;
        }
    };

	//TODO: fix this again after changing from string to ircusers
	//Nick autocomplete when pressing the search-button
	@Override
	public boolean onSearchRequested() {
		EditText inputfield = (EditText)findViewById(R.id.ChatInputView);
		String inputString = inputfield.getText().toString();
		String[] inputWords = inputString.split(" ");
		String inputNick = inputWords[inputWords.length-1];
		int inputLength = inputString.lastIndexOf(" ") == -1 ? 0: inputString.substring(0, inputString.lastIndexOf(" ")).length();
		UserCollection userColl = adapter.buffer.getUsers();
		
		if ( "".equals(inputNick) ) {
			if ( userColl.getOperators().size() > 0 ) {
				inputfield.setText(userColl.getOperators().get(0).nick+ ": ");
				inputfield.setSelection(userColl.getOperators().get(0).nick.length() + 2);
			}
		} else {
			if (matchAndSetNick(inputNick, inputWords, inputString, inputLength, inputfield, userColl.getOperators())){}
			else if (matchAndSetNick(inputNick, inputWords, inputString, inputLength, inputfield, userColl.getVoiced())) {}
			else if (matchAndSetNick(inputNick, inputWords, inputString, inputLength, inputfield, userColl.getUsers())) {}
		}
		return false;  // don't go ahead and show the search box
	}

	private boolean matchAndSetNick(String inputNick, String[] inputWords, String inputString, int inputLength, EditText inputfield, List<IrcUser> userList) {
		for (IrcUser user : userList) {
			if ( user.nick.matches("(?i)"+inputNick+".*")  ) { //Matches the start of the string
				String additional = inputWords.length > 1 ? " ": ": ";
				inputfield.setText(inputString.substring(0, inputLength) + (inputLength >0 ? " ":"") + user.nick+  additional);
				inputfield.setSelection(inputLength + (inputLength >0 ? 1:0) + user.nick.length() + additional.length());
				return true;
			}
		}
		return false;
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.chat_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_preferences:
			Intent i = new Intent(ChatActivity.this, PreferenceView.class);
			startActivity(i);
			break;
		case R.id.menu_disconnect:
			this.boundConnService.disconnectFromCore();
			break;
		case R.id.menu_hide_events:
			showDialog(R.id.DIALOG_HIDE_EVENTS);
			break;
		case R.id.menu_users_list:
			openNickList(adapter.buffer);
			break;
		}
		return super.onOptionsItemSelected(item);
	}



	@Override
	protected void onStart() {
		super.onStart();
		dynamicBacklogAmout = Integer.parseInt(preferences.getString(getString(R.string.preference_dynamic_backlog), "10"));
		doBindService();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		EditText inputArea = (EditText) findViewById(R.id.ChatInputView);
		if (storedInputText.length() > 0 && inputArea.getText().toString().length() == 0) {
			inputArea.setText(storedInputText);
		}
		
		if (boundConnService != null) {
			boundConnService.keepScreenOnIfEnabled(getWindow());
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		storedInputText = ((EditText) findViewById(R.id.ChatInputView)).getText().toString();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (adapter.buffer == null) return;
		adapter.buffer.setDisplayed(false);
		
		//Dont save position if list is at bottom
		if (backlogList.getLastVisiblePosition()==adapter.getCount()-1) {
			adapter.buffer.setTopMessageShown(0);
		}else{
			adapter.buffer.setTopMessageShown(adapter.getListTopMessageId());
		}
		if (adapter.buffer.getUnfilteredSize()!= 0){
			boundConnService.setLastSeen(adapter.getBufferId(), adapter.buffer.getUnfilteredBacklogEntry(adapter.buffer.getUnfilteredSize()-1).messageId);
			boundConnService.markBufferAsRead(adapter.getBufferId());
			boundConnService.setMarkerLine(adapter.getBufferId(), adapter.buffer.getUnfilteredBacklogEntry(adapter.buffer.getUnfilteredSize()-1).messageId);
		}
		doUnbindService();
	}



	@Override
	protected Dialog onCreateDialog(int id) {
		//TODO: wtf rewrite this dialog in code creator shit, if it is possible, mabye it is an alert builder for a reason
		Dialog dialog;
		switch (id) {
		case R.id.DIALOG_HIDE_EVENTS:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Hide Events");
			String[] filterList = IrcMessage.Type.getFilterList();
			boolean[] checked = new boolean[filterList.length];
			ArrayList<IrcMessage.Type> filters = adapter.buffer.getFilters();
			for (int i=0;i<checked.length;i++) {
				if(filters.contains(IrcMessage.Type.valueOf(filterList[i]))) {
					checked[i]=true;
				}else{
					checked[i]=false;
				}
			}
			builder.setMultiChoiceItems(filterList, checked, new OnMultiChoiceClickListener() {

				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					IrcMessage.Type type = IrcMessage.Type.valueOf(IrcMessage.Type.getFilterList()[which]);
					if(isChecked)
						adapter.addFilter(type);
					else
						adapter.removeFilter(type);
				}
			});
			dialog = builder.create();
			break;
		
		default:
			dialog = null;
			break;
		}
		return dialog;
	}
	
	private void openNickList(Buffer buffer) {
		Intent i = new Intent(ChatActivity.this, NicksActivity.class);
		i.putExtra(BufferActivity.BUFFER_ID_EXTRA, buffer.getInfo().id);
		i.putExtra(BufferActivity.BUFFER_NAME_EXTRA, buffer.getInfo().name);
		startActivity(i);
	}

	public class BacklogAdapter extends BaseAdapter implements Observer {

		//private ArrayList<IrcMessage> backlog;
		private LayoutInflater inflater;
		private Buffer buffer;
		private ListView list = (ListView)findViewById(R.id.chatBacklogList);


		public BacklogAdapter(Context context, ArrayList<IrcMessage> backlog) {
			inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		}

		public void setBuffer(Buffer buffer) {
			this.buffer = buffer;
			if ( buffer.getInfo().type == BufferInfo.Type.QueryBuffer ){
				((TextView)findViewById(R.id.chatNameView)).setText(buffer.getInfo().name);
			} else if ( buffer.getInfo().type == BufferInfo.Type.StatusBuffer ){
				((TextView)findViewById(R.id.chatNameView)).setText(buffer.getInfo().name); //TODO: Add which server we are connected to
			} else{
				((TextView)findViewById(R.id.chatNameView)).setText(buffer.getInfo().name + ": " + buffer.getTopic());
			}
			notifyDataSetChanged();
			list.scrollTo(list.getScrollX(), list.getScrollY());
		}


		public int getCount() {
			if (this.buffer==null) return 0;
			return buffer.getSize();
		}

		public IrcMessage getItem(int position) {
			//TODO: QriorityQueue is fucked, we dont want to convert to array here, so change later
			return buffer.getBacklogEntry(position);
		}

		public long getItemId(int position) {
			return buffer.getBacklogEntry(position).messageId;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder = null;

			if (convertView==null) {
				convertView = inflater.inflate(R.layout.backlog_item, null);
				holder = new ViewHolder();
				holder.timeView = (TextView)convertView.findViewById(R.id.backlog_time_view);
				holder.nickView = (TextView)convertView.findViewById(R.id.backlog_nick_view);
				holder.msgView = (TextView)convertView.findViewById(R.id.backlog_msg_view);
				holder.separatorView = (TextView)convertView.findViewById(R.id.backlog_list_separator);
				holder.item_layout = (LinearLayout)convertView.findViewById(R.id.backlog_item_linearlayout);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder)convertView.getTag();
			}

			//Set separator line here
			if (position != (getCount()-1) && (buffer.getMarkerLineMessage() == getItem(position).messageId || (buffer.isMarkerLineFiltered() && getItem(position).messageId<buffer.getMarkerLineMessage() && getItem(position+1).messageId>buffer.getMarkerLineMessage()))) { 
				holder.separatorView.getLayoutParams().height = 1;
			} else {
				holder.separatorView.getLayoutParams().height = 0;
			}

			IrcMessage entry = this.getItem(position);
			holder.messageID = entry.messageId;
			holder.timeView.setText(entry.getTime());


			switch (entry.type) {
			case Action:
				holder.nickView.setText("-*-");
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_actionmessage_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_actionmessage_color));
				holder.msgView.setText(entry.getNick()+" "+entry.content);
				break;
			case Server:
				holder.nickView.setText("*");
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_servermessage_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_servermessage_color));
				holder.msgView.setText(entry.content);
				break;
			case Join:
				holder.nickView.setText("-->");
				holder.msgView.setText(entry.getNick() + " has joined " + entry.content);
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				break;
			case Part:
				holder.nickView.setText("<--");
				holder.msgView.setText(entry.getNick() + " has left (" + entry.content + ")");
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				break;
			case Quit:				
				holder.nickView.setText("<--");
				holder.msgView.setText(entry.getNick() + " has quit (" + entry.content + ")");
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				break;
				//TODO: implement the rest
			case Kick:
				holder.nickView.setText("<-*");
				int nickEnd = entry.content.toString().indexOf(" ");
				String nick = entry.content.toString().substring(0, nickEnd);
				String reason = entry.content.toString().substring(nickEnd+1);
				holder.msgView.setText(entry.getNick() + " has kicked " + nick + " from " + entry.bufferInfo.name + " (" + reason + ")");
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				break;

			case Mode:
				holder.nickView.setText("***");
				holder.msgView.setText("Mode " + entry.content.toString() + " by " + entry.getNick());
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				break;
			case Nick:
				holder.nickView.setText("<->");
				holder.msgView.setText(entry.getNick()+" is now known as " + entry.content.toString());
				holder.msgView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				holder.nickView.setTextColor(getResources().getColor(R.color.ircmessage_commandmessages_color));
				break;
			case Plain:
			default:
				if(entry.isSelf()) {
					holder.nickView.setTextColor(Color.BLACK); //TODO: probably move to color file, or somewhere else it needs to be, so user can select color them self
				}else{
					int hashcode = entry.getNick().hashCode();
					holder.nickView.setTextColor(Color.rgb(hashcode & 0xFF0000, hashcode & 0xFF00, hashcode & 0xFF));
				}
				holder.msgView.setTextColor(0xff000000);
				holder.msgView.setTypeface(Typeface.DEFAULT);

				holder.nickView.setText("<" + entry.getNick() + ">");
				holder.msgView.setText(entry.content);
				break;
			}
			if (entry.isHighlighted()) {
				holder.item_layout.setBackgroundResource(R.color.ircmessage_highlight_color);
			}else {
				holder.item_layout.setBackgroundResource(R.color.ircmessage_normal_color);
			}
			//Log.i(TAG, "CONTENT:" + entry.content);
			return convertView;
		}

		public void update(Observable observable, Object data) {
			if (data==null) {
				notifyDataSetChanged();
				return;
			}
			switch ((Integer)data) {
			case R.id.BUFFERUPDATE_NEWMESSAGE:
				notifyDataSetChanged();				
				break;
			case R.id.BUFFERUPDATE_BACKLOG:
				int topId = getListTopMessageId();
				notifyDataSetChanged();
				setListTopMessage(topId);
				break;
			}

		}

		/*
		 * Returns the messageid for the ircmessage that is currently at the top of the screen
		 */
		public int getListTopMessageId() {
			int topId;
			if (list.getChildCount()==0) {
				topId = 0;
			}else {
				topId = ((ViewHolder)list.getChildAt(0).getTag()).messageID;
			}
			return topId;
		}

		/*
		 * Sets what message from the adapter will be at the top of the visible screen
		 */
		public void setListTopMessage(int messageid) {
			for(int i=0;i<adapter.getCount();i++){
				if (adapter.getItemId(i)==messageid){
					list.setSelectionFromTop(i,5);
					break;
				}
			}
		}

		public void stopObserving() {
			buffer.deleteObserver(this);

		}

		public void clearBuffer() {
			buffer = null;

		}

		public int getBufferId() {
			return buffer.getInfo().id;
		}

		public void getMoreBacklog() {
			adapter.buffer.setBacklogPending(ChatActivity.this.dynamicBacklogAmout);
			boundConnService.getMoreBacklog(adapter.getBufferId(),ChatActivity.this.dynamicBacklogAmout);
		}

		public void removeFilter(Type type) {
			buffer.removeFilterType(type);
			
		}

		public void addFilter(Type type) {
			buffer.addFilterType(type);
			
		}
	}	


	public static class ViewHolder {
		public TextView timeView;
		public TextView nickView;
		public TextView msgView;
		public TextView separatorView;
		public LinearLayout item_layout;

		public int messageID;
	}




	private class BacklogScrollListener implements OnScrollListener {

		private int visibleThreshold;
		private boolean loading = false;

		public BacklogScrollListener(int visibleThreshold) {
			this.visibleThreshold = visibleThreshold;
		}

		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			if (loading) {
				if (!adapter.buffer.hasPendingBacklog()) {
					loading = false;
				}
			}
//			Log.d(TAG, "loading: "+ Boolean.toString(loading) +"totalItemCount: "+totalItemCount+ "visibleItemCount: " +visibleItemCount+"firstVisibleItem: "+firstVisibleItem+ "visibleThreshold: "+visibleThreshold);
			if (!loading && (firstVisibleItem <= visibleThreshold)) {
				if (adapter.buffer!=null) {
					loading = true;
					ChatActivity.this.adapter.getMoreBacklog();
				}else {
					Log.w(TAG, "Can't get backlog on null buffer");
				}

			}	

		}

		public void onScrollStateChanged(AbsListView view, int scrollState) {
			// Not interesting for us to use

		}

	}

	/**
	 * Code for service binding:
	 */
	private CoreConnService boundConnService;
	private Boolean isBound;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			Log.i(TAG, "BINDING ON SERVICE DONE");
			boundConnService = ((CoreConnService.LocalBinder)service).getService();

			Intent intent = getIntent();
			//Testing to see if i can add item to adapter in service
			Buffer buffer = boundConnService.getBuffer(intent.getIntExtra(BufferActivity.BUFFER_ID_EXTRA, 0), adapter);
			adapter.setBuffer(buffer);
			buffer.setDisplayed(true);
			
			boundConnService.onHighlightsRead(buffer.getInfo().id);

			//Move list to correect position
			if (adapter.buffer.getTopMessageShown() == 0) {
				backlogList.setSelection(adapter.getCount()-1);
			}else{
				adapter.setListTopMessage(adapter.buffer.getTopMessageShown());
			}

			boundConnService.registerStatusReceiver(statusReceiver);
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			boundConnService = null;

		}
	};

	void doBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		bindService(new Intent(ChatActivity.this, CoreConnService.class), mConnection, Context.BIND_AUTO_CREATE);
		isBound = true;
		Log.i(TAG, "BINDING");
	}

	void doUnbindService() {
		if (isBound) {
			Log.i(TAG, "Unbinding service");
			// Detach our existing connection.
			adapter.stopObserving();
			boundConnService.unregisterStatusReceiver(statusReceiver);
			unbindService(mConnection);
			isBound = false;

		}
	}
}
