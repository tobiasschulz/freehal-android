/*******************************************************************************
 * Copyright (c) 2006 - 2012 Tobias Schulz and Contributors.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/gpl.html>.
 ******************************************************************************/
package net.freehal.app.gui;

import net.freehal.app.R;
import java.util.HashMap;

import net.freehal.app.util.FreehalAdapters;
import net.freehal.app.util.AndroidUtils;
import net.freehal.app.util.VoiceRecHelper;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;

@SuppressLint("ValidFragment")
public class DetailFragment extends SherlockFragment {

	public static String ARG_ITEM_ID = "item_id";

	private String tab;
	private HistoryAdapter historyAdapter;
	private static Intent recievedIntent;

	private static HashMap<String, DetailFragment> tabs;
	static {
		tabs = new HashMap<String, DetailFragment>();
	}

	/**
	 * Singleton!
	 * 
	 * @param id
	 *        the id of the tab
	 * @param activity
	 *        the activity (OverviewActivity for tablets or DetailActivity for
	 *        phones)
	 * @return the singleton instance
	 */
	public static DetailFragment forTab(String id) {
		Log.e("forTab", "1: id=" + id);
		id = SelectContent.validateId(id);
		Log.e("forTab", "2: id=" + id);
		boolean isCached = tabs.containsKey(id);
		if (!isCached) {
			Log.e("forTab", "not cached.");
			DetailFragment instance = new DetailFragment();
			instance.setTab(id);
			Bundle arguments = new Bundle();
			arguments.putString(DetailFragment.ARG_ITEM_ID, id);
			instance.setArguments(arguments);
			tabs.put(id, instance);
		}
		Log.e("forTab", "3: tabs.get(id).id=" + tabs.get(id).getTab());
		DetailFragment instance = tabs.get(id);
		if (!isCached) {
			tabs.remove(id);
		}
		return instance;
	}

	public String getTab() {
		return tab;
	}

	public void setTab(String tab) {
		this.tab = tab;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(null);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putString("tab", tab);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		if (savedInstanceState != null && savedInstanceState.containsKey("tab"))
			tab = savedInstanceState.getString("tab");
		tab = SelectContent.validateId(tab);

		FreehalAdapters.setCurrent(tab);

		final View rootView;
		if (tab.equals("online") || tab.equals("offline")) {
			rootView = onCreateViewConversation(inflater, container, savedInstanceState, tab);
		} else if (tab.equals("log")) {
			rootView = onCreateViewLog(inflater, container, savedInstanceState, tab);
		} else if (tab.equals("graph")) {
			rootView = onCreateViewGraph(inflater, container, savedInstanceState, tab);
		} else if (tab.equals("about")) {
			rootView = onCreateViewAbout(inflater, container, savedInstanceState, tab);
		} else {
			Log.e("onCreateView", "unknown tab: " + tab);
			// this should never happen
			rootView = null;
		}

		return rootView;
	}

	@Override
	public void onStart() {
		super.onStart();

		if (tab.equals("online") || tab.equals("offline")) {
			onStartConversation();
		}
	}

	public View onCreateViewConversation(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState, final String item) {

		final View rootView = inflater.inflate(R.layout.fragment_conversation_detail, container, false);
		final EditText edit = (EditText) rootView.findViewById(R.id.edit_message);
		final Button sendButton = (Button) rootView.findViewById(R.id.button_send);

		// input listeners
		InputProcess.Listener listener = new InputProcess.Listener(edit, this);
		edit.setOnKeyListener(listener);
		sendButton.setOnClickListener(listener);
		VoiceRecHelper.setResultHook(listener);

		// list view for conversation
		ListView list = (ListView) rootView.findViewById(R.id.listView);
		historyAdapter = new HistoryAdapter(rootView.getContext(), R.layout.row, History.getInstance());
		historyAdapter.setListView(list);
		list.setAdapter(historyAdapter);
		registerForContextMenu(list);

		// if we are called by a "send" intent!
		if (DetailFragment.hasRecievedIntent()) {
			Intent intent = DetailFragment.getRecievedIntent();
			String action = intent.getAction();
			String type = intent.getType();

			if (Intent.ACTION_SEND.equals(action) && type != null) {
				if ("text/plain".equals(type)) {
					InputProcess.recieveInput(intent.getStringExtra(Intent.EXTRA_TEXT),
							new EditTextKeyboardOpener(edit));
				}
			}
		}

		return rootView;
	}

	public void onStartConversation() {
		final EditText edit = (EditText) getView().findViewById(R.id.edit_message);

		new EditTextKeyboardOpener(edit).onShowKeyboard();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = this.getActivity().getMenuInflater();
		inflater.inflate(R.menu.statement, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final History history = History.getInstance();

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		final int position = info.position;
		final String text;
		if (history.getRef(position).length() > 0)
			text = history.getText(Integer.parseInt(history.getRef(position)), position);
		else
			text = history.getText(position);
		EditText edit = (EditText) getView().findViewById(R.id.edit_message);

		switch (item.getItemId()) {
		case R.id.menu_statement_ask_again:
			InputProcess.recieveInput(text, new EditTextKeyboardOpener(edit));
			return true;
		case R.id.menu_statement_edit:
			edit.setText(text);
			return true;
		case R.id.menu_statement_share_conversation:
			share(Html.fromHtml(history.toString()).toString(),
					this.getResources().getString(R.string.subject_share_conversation));
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	private void share(final String text, final String subject) {
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_TEXT, text);
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		sendIntent.setType("text/plain");
		startActivity(sendIntent);
	}

	public View onCreateViewAbout(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState,
			final String item) {

		View rootView = inflater.inflate(R.layout.fragment_about_detail, container, false);

		final String htmlAbout = getResources().getString(R.string.about_text);
		final String appVersionName = "Version "
				+ AndroidUtils.getVersion(this.getActivity().getApplicationContext()).versionName;
		final String onlineVersionName = FreehalAdapters.getInstance("online").getVersionName();
		final String offlineVersionName = FreehalAdapters.getInstance("offline").getVersionName();

		((TextView) rootView.findViewById(R.id.about_detail)).setText(Html.fromHtml(String.format(htmlAbout,
				appVersionName, onlineVersionName, offlineVersionName)));

		return rootView;
	}

	public View onCreateViewLog(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState,
			final String item) {

		View rootView = inflater.inflate(R.layout.fragment_log_detail, container, false);
		// ((TextView)
		// rootView.findViewById(R.id.log_heading)).setText(R.string.tab_log);

		final String log = FreehalAdapters.getInstance().getLog();
		TextView view = (TextView) rootView.findViewById(R.id.log_detail);
		if (log == null || log.length() == 0)
			view.setText(Html.fromHtml(getResources().getString(R.string.no_log)));
		else
			view.setText(log);
		view.setTypeface(Typeface.MONOSPACE);

		return rootView;
	}

	public View onCreateViewGraph(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState,
			final String item) {

		View rootView = inflater.inflate(R.layout.fragment_graph_detail, container, false);

		final String graph = FreehalAdapters.getInstance().getGraph();
		WebView view = (WebView) rootView.findViewById(R.id.graph_detail);
		if (graph == null || graph.length() < 1000)
			view.loadData(getResources().getString(R.string.no_graph), "text/html", null);
		else
			view.loadData(graph, "text/html", null);
		view.getSettings().setSupportZoom(true);
		view.getSettings().setBuiltInZoomControls(true);

		return rootView;
	}

	public static void setRecievedIntent(Intent intent) {
		recievedIntent = intent;
	}

	public static Intent getRecievedIntent() {
		Intent intent = recievedIntent;
		recievedIntent = null;
		return intent;
	}

	public static boolean hasRecievedIntent() {
		return recievedIntent == null ? false : true;
	}
}
