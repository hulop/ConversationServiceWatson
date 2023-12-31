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

import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

public class LogHelper {
	private String mEndpoint, mApiKey;

	public LogHelper() {
		try {
			JSONObject config = CommonUtil.getConfig().getJSONObject("logging_config");
			mEndpoint = config.getString("endpoint");
			mApiKey = config.getString("api_key");
			if (mEndpoint.startsWith("!!") || mApiKey.startsWith("!!")) {
				mApiKey = null;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public boolean saveLog(String clientId, JSONObject log) throws Exception {
		if (mApiKey == null) {
			return false;
		}
		JSONObject data = new JSONObject();
		data.put("event", "conversation");
		data.put("client", clientId);
		data.put("timestamp", System.currentTimeMillis());
		data.put("log", log);
		Request request = Request.Post(new URI(mEndpoint)).bodyForm(Form.form().add("action", "insert")
				.add("auditor_api_key", mApiKey).add("data", new JSONArray().put(data).toString()).build());
		HttpResponse response = request.execute().returnResponse();
		return response.getStatusLine().getStatusCode() == 200;
	}

	public JSONArray getLog(String clientId, String start, String end, String skip, String limit) throws Exception {
		if (mApiKey == null) {
			return new JSONArray();
		}
		Form form = Form.form().add("action", "get").add("auditor_api_key", mApiKey).add("event", "conversation");
		if (clientId != null) {
			form.add("clientId", clientId);
		}
		if (start != null) {
			form.add("start", start);
		}
		if (end != null) {
			form.add("end", end);
		}
		if (skip != null) {
			form.add("skip", skip);
		}
		if (limit != null) {
			form.add("limit", limit);
		}
		Request request = Request.Post(new URI(mEndpoint)).bodyForm(form.build());
		return (new JSONArray(request.execute().returnContent().asString()));
	}

}
