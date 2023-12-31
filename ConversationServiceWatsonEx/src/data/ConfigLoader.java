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
package data;

import java.io.InputStreamReader;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.wink.json4j.JSON;
import org.apache.wink.json4j.JSONObject;

public class ConfigLoader {

	private static String CONFIG_URL = System.getenv("CONVERSATION_EX_DATA");
	private static JSONObject mConfig = null;

	public static JSONObject load(String path) throws Exception {
		JSONObject obj = loadRemote(path);
		return obj != null ? new JSONObject(obj.toString()) : new JSONObject(new InputStreamReader(ConfigLoader.class.getResourceAsStream(path), "UTF-8"));
	}

	private static synchronized JSONObject loadRemote(String path) {
		Matcher m = Pattern.compile("^/data/(.*)$").matcher(path);
		if (m.matches()) {
			path = m.group(1);
		}
		if (CONFIG_URL != null && mConfig == null) {
			mConfig = new JSONObject();
			try {
				Request request = Request.Get(new URI(CONFIG_URL));
				HttpResponse response = Executor.newInstance().execute(request).returnResponse();
				if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
					try (ZipInputStream zis = new ZipInputStream(response.getEntity().getContent())) {
						System.out.println(CONFIG_URL);
						for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
							if (!entry.isDirectory()) {
								mConfig.put(entry.getName(), JSON.parse(zis));
							}
						}
					}
				}
			} catch (Exception e) {
				System.err.println(CONFIG_URL + " - " + e.getMessage());
			}
		}
		return mConfig != null ? mConfig.optJSONObject(path) : null;
	}

}
