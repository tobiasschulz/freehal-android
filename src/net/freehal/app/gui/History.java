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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.freehal.app.R;
import net.freehal.app.util.FreehalAdapters;
import net.freehal.app.util.FreehalUser;
import net.freehal.app.util.AndroidUtils;
import android.annotation.SuppressLint;
import android.text.Html;
import android.widget.ScrollView;
import android.widget.TextView;

public class History {
	private ArrayList<String> name;
	private ArrayList<String> text;
	private ArrayList<String> reference;
	private String item_id;
	private int alternateText;
	private static HistoryHook hook;
	static HashMap<String, History> singletons;

	static {
		singletons = new HashMap<String, History>();
	}

	public static History getInstance(String item) {
		if (!singletons.containsKey(item)) {
			History hist = new History(item);
			if (item.equals("online")) {
				hist.setAlternateText(R.string.comment_online);
			} else if (item.equals("offline")) {
				hist.setAlternateText(R.string.comment_offline);
			}
			singletons.put(item, hist);
		}
		return singletons.get(item);
	}

	public static History getInstance() {
		return getInstance(FreehalAdapters.getCurrent());
	}

	private History(String item_id) {
		this.item_id = item_id;
		this.alternateText = 0;

		name = new ArrayList<String>();
		text = new ArrayList<String>();
		reference = new ArrayList<String>();

		restore();
	}

	public int addInput(String input, String ref) {
		restore();
		final String user = FreehalUser.get().getUserName(AndroidUtils.getString(R.string.person_user));
		name.add(tag(user, "b", ""));
		text.add(input);
		reference.add(ref);
		save();
		return text.size() - 1;
	}

	public int addOutput(String output, String ref) {
		restore();
		final String user = FreehalUser.get().getFreehalName();
		name.add(tag(user, "b", ""));
		text.add(output);
		reference.add(ref);
		save();
		return text.size() - 1;
	}

	private File getStorageFile(final String column) {
		return new File(AndroidUtils.getActivity().getCacheDir(), "history_" + item_id + "_" + column);
	}

	private void save() {
		if (!name.isEmpty())
			save(name, "name");
		if (!text.isEmpty())
			save(text, "text");
		if (!reference.isEmpty())
			save(reference, "reference");

		if (History.hook != null)
			History.hook.onHistoryChanged();
	}

	private void restore() {
		restore(name, "name");
		restore(text, "text");
		restore(reference, "reference");
	}

	private void save(final ArrayList<String> list, final String column) {
		try {
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(
					getStorageFile(column))));
			for (String line : list) {
				dos.writeUTF(line);
			}
			dos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void restore(ArrayList<String> list, final String column) {
		try {
			DataInputStream din = new DataInputStream(new BufferedInputStream(new FileInputStream(
					getStorageFile(column))));
			list.clear();
			try {
				for (;;) {
					list.add(din.readUTF());
				}
			} catch (EOFException e) {
				// EOF reached
			}
			din.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressLint("ParserError")
	private String tag(String s, String tag, String params) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("<");
		stringBuilder.append(tag);
		if (params.length() > 0)
			stringBuilder.append(params);
		stringBuilder.append(">");
		stringBuilder.append(s);
		stringBuilder.append("</");
		stringBuilder.append(tag);
		stringBuilder.append(">");
		return stringBuilder.toString();
	}

	@Override
	public String toString() {
		String str = new String();
		for (int i = 0; i < name.size() && i < text.size(); ++i) {
			str += name.get(i) + ": " + text.get(i) + "<br/>";
		}
		return str;
	}

	public boolean writeTo(final TextView text, final ScrollView mScrollView) {

		if (size() > 0) {
			text.setText(Html.fromHtml(this.toString()));
			mScrollView.post(new Runnable() {
				public void run() {
					save();
					mScrollView.smoothScrollTo(0, text.getBottom());
				}
			});
			return true;
		} else {
			if (alternateText != 0)
				text.setText(alternateText);
			return false;
		}
	}

	public void setAlternateText(int alternateText) {
		this.alternateText = alternateText;
	}

	public List<String> getText() {
		return text;
	}

	public List<String> getName() {
		return name;
	}

	public String getText(int i) {
		return i < 0 ? null : i < text.size() ? text.get(i) : null;
	}

	public String getText(int i, int fallback) {
		final String value = getText(i);
		return value != null ? value : getText(fallback);
	}

	public String getName(int i) {
		return i < 0 ? null : i < name.size() ? name.get(i) : null;
	}

	public String getName(int i, int fallback) {
		final String value = getName(i);
		return value != null ? value : getName(fallback);
	}

	public String getRef(int i) {
		return i < 0 ? null : i < reference.size() ? reference.get(i) : null;
	}

	public String getRef(int i, int fallback) {
		final String value = getRef(i);
		return value != null ? value : getRef(fallback);
	}

	public int size() {
		return AndroidUtils.min(text.size(), name.size(), reference.size());
	}

	public void setHook(HistoryHook hook) {
		History.hook = hook;
	}

	public void refresh() {
		History.hook.onHistoryChanged();
	}
}
