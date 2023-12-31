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

package hulop.cm.qa;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

import hulop.cm.util.CommonUtil;
import hulop.cm.util.Extra;

public class WatsonHelper extends QAHelper {
	private final String mLang;
	private JSONObject mLastResultMap = new JSONObject();
	private Map<String, Long> mLastPostMap = new HashMap<String, Long>();

	private String mEndpoint, mUsername, mPassword, mWorkspace;
	private boolean mIgnoreCert;

	private static final Map<String, WatsonHelper> instances = new HashMap<String, WatsonHelper>();
	private static final Extra extra = new Extra();

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
			JSONObject config = CommonUtil.getConfig().getJSONObject("watson_config");
			mEndpoint = System.getenv("CONV_WATSON_ENDPOINT");
			if (mEndpoint == null) {
				mEndpoint = config.getString("endpoint");
			}
			mUsername = config.getString("username");
			mPassword = config.getString("password");
			mWorkspace = config.getString("workspace_" + lang);
			mIgnoreCert = config.getBoolean("ignoreCert");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public JSONObject postMessage(String clientId, String text, JSONObject clientContext) throws Exception {
		JSONObject input = new JSONObject();
		if (text != null) {
			input.put("text", text);
		}
		JSONObject requestBody = new JSONObject();
		boolean hasText = text != null && text.length() > 0;
		if (hasText && !mLastResultMap.has(clientId)) {
			postMessage(clientId, null, null);
		}
		if (mLastResultMap.has(clientId)) {
			JSONObject lastResult = mLastResultMap.getJSONObject(clientId);
			if (hasText && lastResult.has("context")) {
				try {
					JSONObject context = lastResult.getJSONObject("context");
					JSONObject copy = (JSONObject) context.clone();
					// context.remove("dest_info");
					// context.remove("candidates_info");
					copy.remove("output_pron");
					copy.remove("navi");
					requestBody.put("context", copy);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		JSONObject requestContext = requestBody.optJSONObject("context");
		if (requestContext == null) {
			requestBody.put("context", requestContext = new JSONObject());
		}
		long now = System.currentTimeMillis();
		Long lastWelcome = mLastPostMap.put(clientId, now);
		if (lastWelcome != null) {
			requestContext.put("elapsed_time", now - lastWelcome.longValue());
		}
		requestBody.put("alternate_intents", true);
		requestBody.put("input", input);
		addClientContext(clientContext, requestContext);
		String api = String.format(mEndpoint, mWorkspace);
		System.out.println(api);
		System.out.println("---- start of request ----\n" + requestBody.toString(4) + "\n---- end ----");

		extra.putInfoMap(requestContext, mLang);
		if ("$CONTEXT_DEBUG$".equals(text)) {
			return requestBody;
		}
//		Request request = Request.Post(new URI(api)).bodyString(requestBody.toString(), ContentType.APPLICATION_JSON);
		Object info_map = requestContext.remove("info_map"); // issue #1443
		Request request = Request.Post(new URI(api)).bodyString(CommonUtil.jsonString(requestBody), ContentType.APPLICATION_JSON); // issue #1443
		requestContext.put("info_map", info_map); // issue #1443

		JSONObject response = (JSONObject) execute(mIgnoreCert, mUsername, mPassword, request);
		JSONObject responseContext = response.optJSONObject("context");
		if (responseContext != null) {
			try {
				removeClientContext(responseContext);
				if (!"$DEBUG$".equals(text)) {
					extra.removeInfoMap(responseContext);
				}
				System.out.println("---- start of response ----\n" + response.toString(4) + "\n---- end ----");
				if (!responseContext.has("output_pron")) {
					ResponseHandler handler = new ResponseHandler(response, requestContext);
					String dest = responseContext.optString("dest");
					String dest_category = responseContext.optString("dest_category");
					String dest_category_map = responseContext.optString("dest_category_map");
					JSONObject dest_info = null;
					if (dest != null) {
						dest_info = handler.getInfo(dest);
					} else if (dest_category != null && dest_category_map != null) {
						dest_info = handler.getCategoryInfo(dest_category, requestContext.optJSONObject(dest_category_map));
					}
					if (dest_info != null) {
						responseContext.put("dest_info", dest_info);
					}
					JSONArray candidates = responseContext.optJSONArray("candidates");
					if (candidates != null) {
						JSONArray candidates_info = new JSONArray();
						responseContext.put("candidates_info", candidates_info);
						for (Object candidate : candidates) {
							JSONObject info = handler.getInfo((String) candidate);
							if (info != null && info.has("pr_short")) {
								candidates_info.add(info);
							}
						}
					}
					handler.save();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		setLastResult(clientId, response);
		return response;
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

	private class ResponseHandler {
		private final JSONObject response;
		private String text, pron;
		private JSONObject conversion_map, translation_map;
		private boolean converted = false;

		public ResponseHandler(JSONObject response, JSONObject requestContext) throws JSONException {
			JSONArray array = response.getJSONObject("output").getJSONArray("text");
			String join = array.join("\n");
			this.response = response;
			this.conversion_map = requestContext.optJSONObject("info_map");
			this.translation_map = response.getJSONObject("context").optJSONObject("translation_map");
			if (translation_map == null) {
				translation_map = extra.createTranslationMap(mLang);
			}
			System.out.println("translation_map: " + translation_map);
			this.text = convert(join, false).replaceAll("(\\.{3,})", "");
			this.pron = convert(join, true).replaceAll("(\\.{3,})", "ja".equals(mLang) ? "。\n\n" : "\n\n");
		}

		public void save() throws JSONException {
			text = translate(text, false);
			pron = translate(pron, true);
			response.getJSONObject("output").put("text", new JSONArray(text.split("\n")));
			response.getJSONObject("context").put("output_pron", pron);
		}

		public JSONObject getInfo(String name) throws JSONException {
			JSONObject info = conversion_map != null ? conversion_map.optJSONObject(name) : null;
			if (info != null) {
				if (!info.has("name")) {
					info = ((JSONObject)info.clone()).put("name", name);
				}
				if (!converted && info.has("pron") && "ja".equals(mLang)) {
					pron = pron.replace(name, info.getString("pron"));
				}
			}
			return info;
		}

		public JSONObject getCategoryInfo(String category_name, JSONObject category_map) throws JSONException {
			if (conversion_map != null && category_map != null) {
				JSONArray names = category_map.optJSONArray(category_name);
				if (names != null) {
					String nodes = "";
					for (Object name : names) {
						try {
							String node = conversion_map.getJSONObject((String) name).getString("nodes");
							if (nodes.length() > 0) {
								nodes += "|";
							}
							nodes += node;
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					return new JSONObject().put("name", category_name).put("pron", category_name).put("nodes", nodes);
				}
			}
			return null;
		}

		private String convert(String before, boolean pron) {
			if (conversion_map == null) {
				return before;
			}
			StringBuffer sb = new StringBuffer();
			Matcher m = PAT_NAME.matcher(before);
			while (m.find()) {
				String name = m.group(1);
				JSONObject info = conversion_map.optJSONObject(name);
				if (info == null) {
					name = m.group(0);
				} else {
					if (pron) {
						try {
							name = info.getString("pron");
						} catch (Exception e) {
						}
					}
					converted = true;
				}
				m.appendReplacement(sb, name);
			}
			m.appendTail(sb);
			return sb.toString();
		}

		private String translate(String before, boolean pron) {
			if (translation_map == null) {
				return before;
			}
			boolean translated = false;
			StringBuffer sb = new StringBuffer();
			Matcher m = PAT_NAME.matcher(before);
			while (m.find()) {
				String name = m.group(1);
				if (pron) {
					try {
						name = translation_map.getJSONObject(name).getString("pron");
					} catch (Exception e) {
					}
				}
				translated = true;
				m.appendReplacement(sb, name);
			}
			m.appendTail(sb);
			if (translated || !pron) {
				return sb.toString();
			}
			String after = before;
			for (Iterator<String> it = translation_map.keys(); it.hasNext();) {
				String name = it.next();
				try {
					String p = translation_map.getJSONObject(name).optString("pron");
					if (p != null) {
						after = after.replace(name, p);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return after;
		}
	}
	static final Pattern PAT_NAME = Pattern.compile("@@(.*?)##");
}
