/*******************************************************************************
 * Copyright (c) 2014, 2023  IBM Corporation, Carnegie Mellon University and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *******************************************************************************/

package hulop.cm.util;

import java.util.regex.Pattern;

import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

public class TextFilter {

	private Pattern mPatRepeat;

	public TextFilter() {
		JSONObject config = CommonUtil.getConfig();
		if (config != null) {
			try {
				JSONArray repeatPhrases = new JSONArray();
				for (Object phrase : config.getJSONArray("repeat_matches")) {
					repeatPhrases.add(phrase);
				}
				for (Object phrase : config.getJSONArray("repeat_starts")) {
					repeatPhrases.add(phrase + ".*");
				}
				for (Object phrase : config.getJSONArray("repeat_contains")) {
					repeatPhrases.add(".*" + phrase + ".*");
				}
				for (Object phrase : config.getJSONArray("repeat_ends")) {
					repeatPhrases.add(".*" + phrase);
				}
				mPatRepeat = Pattern.compile("^(" + repeatPhrases.join("|") + ")$", Pattern.CASE_INSENSITIVE);
				System.out.println(mPatRepeat.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String preprocess(String text) {
		if (text != null) {
			for (Object obj : RemoteConfig.getArray("preprocess")) {
				try {
					JSONArray fromTo = (JSONArray) obj;
					String toText = text.replace(fromTo.getString(0), fromTo.getString(1));
					if (!toText.equals(text)) {
						System.out.println(text + " => " + toText);
						text = toText;
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
		return text;
	}

	public boolean hasRepeatWord(String text) {
		return text != null && mPatRepeat.matcher(text).matches();
	}
}
