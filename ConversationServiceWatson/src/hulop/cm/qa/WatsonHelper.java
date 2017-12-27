/*******************************************************************************
 * Copyright (c) 2014, 2017  IBM Corporation, Carnegie Mellon University and others
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

package hulop.cm.qa;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

public class WatsonHelper extends QAHelper {
	private final String mLang;
	private JSONObject mLastResultMap = new JSONObject();
	private Map<String, Long> mLastPostMap = new HashMap<String, Long>();

	private String mEndpoint, mUsername, mPassword, mWorkspace, mVersion;
	private boolean mIgnoreCert;

	private static Map<String, WatsonHelper> instances = new HashMap<String, WatsonHelper>();

	public static WatsonHelper getInstance(String lang) {
		WatsonHelper instance = instances.get(lang);
		if (instance == null) {
			instances.put(lang, instance = new WatsonHelper(lang));
		}
		return instance;
	}

	protected WatsonHelper(String lang) {
		super();
		mLang = lang;
		try {
			JSONObject config = mConfig.getJSONObject("watson_config");
			mEndpoint = config.getString("endpoint");
			mUsername = config.getString("username");
			mPassword = config.getString("password");
			mWorkspace = config.getString("workspace_" + lang);
			mVersion = config.getString("version");
			mIgnoreCert = config.getBoolean("ignoreCert");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public JSONObject postMessage(String clientId, String text) throws Exception {
		JSONObject input = new JSONObject();
		if (text != null) {
			input.put("text", text);
		}
		JSONObject bodyObj = new JSONObject();
		boolean hasText = text != null && text.length() > 0;
		if (hasText && !mLastResultMap.has(clientId)) {
			postMessage(clientId, null);
		}
		if (mLastResultMap.has(clientId)) {
			JSONObject lastResult = mLastResultMap.getJSONObject(clientId);
			if (lastResult.has("context")) {
				try {
					JSONObject context = lastResult.getJSONObject("context");
					JSONObject copy = null;
					if (hasText) {
						copy = (JSONObject) context.clone();
						// context.remove("dest_info");
						// context.remove("candidates_info");
						copy.remove("output_pron");
						copy.remove("navi");
					} else if (context.has("no_welcome")) {
						copy = new JSONObject().put("no_welcome", context.get("no_welcome"));
					}
					if (copy != null) {
						bodyObj.put("context", copy);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		long now = System.currentTimeMillis();
		Long lastWelcome = mLastPostMap.put(clientId, now);
		if (lastWelcome != null) {
			JSONObject context;
			if (bodyObj.has("context")) {
				context = bodyObj.getJSONObject("context");
			} else {
				bodyObj.put("context", context = new JSONObject());
			}
			context.put("elapsed_time", now - lastWelcome.longValue());
		}
		bodyObj.put("alternate_intents", true);
		bodyObj.put("input", input);
		String api = String.format(mEndpoint, mWorkspace, mVersion);
		System.out.println(api);
		System.out.println(bodyObj.toString(4));

		Request request = Request.Post(new URI(api)).bodyString(bodyObj.toString(), ContentType.APPLICATION_JSON);

		JSONObject result = (JSONObject) execute(mIgnoreCert, mUsername, mPassword, request);
		if (result.has("context")) {
			try {
				JSONObject context = result.getJSONObject("context");
				if (!context.has("output_pron")) {
					JSONArray array = result.getJSONObject("output").getJSONArray("text");
					String output_pron = array.join("\n");
					String output_text = output_pron.replaceAll("(。{3,}|\\.{3,})", "");
					if (!output_text.equals(output_pron)) {
						System.out.println(output_pron + " ==> " + output_text);
					}
					result.getJSONObject("output").put("text", new JSONArray(output_text.split("\n")));
					String[] prons = output_pron.split("(\n|。{3,}|\\.{3,})");
					if (prons.length > 1) {
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < prons.length; i++) {
							if (i > 0) {
								sb.append("ja".equals(mLang) ? "。 。\n\n" : "\n\n");
							}
							sb.append(prons[i]);
						}
						output_pron = sb.toString();
					}
					context.put("output_pron", output_pron);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		setLastResult(clientId, result);
		return result;
	}

	public JSONObject getLastResult(String clientId) {
		if (mLastResultMap.has(clientId)) {
			try {
				return mLastResultMap.getJSONObject(clientId);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public void setLastResult(String clientId, JSONObject result) {
		try {
			mLastResultMap.put(clientId, result);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

}