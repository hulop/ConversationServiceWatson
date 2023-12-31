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

import java.io.ByteArrayInputStream;
import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.apache.wink.json4j.JSON;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONArtifact;
import org.apache.wink.json4j.JSONObject;

public class RemoteConfig {

	private String url, last_modified;
	private long next_check;
	private JSONArtifact last_artifact;
	
	private static RemoteConfig instance;
	private static final JSONObject DEFAULT_OBJECT = new JSONObject();
	private static final JSONArray DEFAULT_ARRAY = new JSONArray();
	static {
		try {
			instance = new RemoteConfig(CommonUtil.getConfig().getString("conversation_config"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static JSONObject getObject(String key) {
		try {
			return ((JSONObject)instance.get()).getJSONObject(key);
		} catch(Exception e) {
			System.err.println("no " + key);;
			return DEFAULT_OBJECT;
		}
	}

	public static JSONArray getArray(String key) {
		try {
			return ((JSONObject)instance.get()).getJSONArray(key);
		} catch(Exception e) {
			System.err.println("no " + key);;
			return DEFAULT_ARRAY;
		}
	}

	public RemoteConfig(String url) {
		this.url = url;
	}

	public JSONArtifact get() {
		long now = System.currentTimeMillis();
		if (now > next_check) {
			next_check = now + 60 * 1000;
			try {
				Request request = Request.Get(new URI(url));
				if (last_modified != null) {
					request.setHeader("If-Modified-Since", last_modified);
				}
				HttpResponse response = Executor.newInstance().execute(request).returnResponse();
				int sc = response.getStatusLine().getStatusCode();
				switch (sc) {
				case HttpStatus.SC_OK:
					Header header = response.getFirstHeader("Last-Modified");
					last_modified = header != null ? header.getValue() : null;
					last_artifact = JSON.parse(new ByteArrayInputStream(EntityUtils.toByteArray(response.getEntity())));
					last_artifact.write(System.out, 4);
					break;
				case HttpStatus.SC_NOT_MODIFIED:
					break;
				default:
					throw new Exception("HttpStatus " + sc);
				}

			} catch (Exception e) {
				last_artifact = null;
				System.err.println(url + " - " + e.getMessage());
			}
		}
		return last_artifact;
	}

}
