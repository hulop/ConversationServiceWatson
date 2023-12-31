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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.wink.json4j.JSON;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

public abstract class QAHelper {
	private static final int TIMEOUT = 15 * 1000;
	private static final String[] CLIENT_CONTEXT_KEYS = new String[] { "no_welcome", "latitude", "longitude", "floor", "building", "user_mode" };

	public abstract JSONObject postMessage(String clientId, String text, JSONObject context) throws Exception;

	public QAHelper() {
		super();
	}

	protected Object execute(boolean ignoreCert, String username, String password, Request request) throws Exception {
		HttpClient httpClient = null;
		if (ignoreCert) {
			httpClient = HttpClients.custom().setHostnameVerifier(new AllowAllHostnameVerifier())
					.setSslcontext(new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
						public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
							return true;
						}
					}).build()).build();
		}
		Executor executor = Executor.newInstance(httpClient);
		if (username != null) {
			executor.auth(username, password);
		}
		Response response = executor.execute(request.connectTimeout(TIMEOUT).socketTimeout(TIMEOUT));
		Content content = response.returnContent();
		String strJSON = content.getType().getCharset() == null ? new String(content.asBytes(), "UTF-8")
				: content.asString();
		return JSON.parse(strJSON.replaceAll("\\\\u0000", ""));
	}

	protected void addClientContext(JSONObject clientContext, JSONObject requestContext) {
		if (clientContext != null) {
			for (String key : CLIENT_CONTEXT_KEYS) {
				if (clientContext.has(key)) {
					try {
						requestContext.put(key, clientContext.get(key));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					requestContext.remove(key);
				}
			}
		}
	}

	protected void removeClientContext(JSONObject responseContext) {
		for (String key : CLIENT_CONTEXT_KEYS) {
			responseContext.remove(key);
		}
	}
}