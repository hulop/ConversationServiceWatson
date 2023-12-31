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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wink.json4j.JSON;
import org.apache.wink.json4j.JSONObject;

import data.ConfigLoader;

public class CommonUtil {
	private static JSONObject gConfig = loadConfig();

	public static void sendJSON(Object obj, HttpServletResponse response) throws IOException {
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		OutputStream os = null;
		try {
			String s = obj.toString();
			(os = response.getOutputStream()).write(s.getBytes("UTF-8"));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (os != null) {
				os.close();
			}
		}
	}

	private static JSONObject loadConfig() {
		JSONObject config = null;
		try {
			String str = readResource("/data/config.json");
			if (str != null) {
				Matcher m = Pattern.compile("\\$\\(([^\\)]+)\\)").matcher(str);
				StringBuffer sb = new StringBuffer();
				while (m.find()) {
					String key = m.group(1);
					String value = System.getenv(key);
					if (value == null) {
						System.err.println("Env variable " + key + " not found");
						value = "!!" + key + "!!";
					}
					m.appendReplacement(sb, value);
					System.out.println(key + "=" + value);
				}
				m.appendTail(sb);
				System.out.println(sb.toString());
				config = (JSONObject) JSON.parse(sb.toString());
				System.out.println("---- start of config ----\n" + config.toString(4) + "\n---- end ----");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return config;
	}

	public static JSONObject getConfig() {
		return gConfig;
	}

	public static Object getJSON(HttpServletRequest request) {
		InputStream is = null;
		try {
			is = request.getInputStream();
			return JSON.parse(is);
		} catch (Exception e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	public static JSONObject load(String path) throws Exception {
//		return new JSONObject(new InputStreamReader(CommonUtil.class.getResourceAsStream(path), "UTF-8"));
		return ConfigLoader.load(path);
	}

	private static String readResource(String name) throws IOException {
		InputStream is = CommonUtil.class.getResourceAsStream(name);
		if (is != null) {
			Reader reader = null;
			try {
				reader = new InputStreamReader(is);
				int length;
				char cbuf[] = new char[4096];
				StringBuilder sb = new StringBuilder();
				while ((length = reader.read(cbuf, 0, cbuf.length)) != -1) {
					sb.append(cbuf, 0, length);
				}
				return sb.toString();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (reader != null) {
					reader.close();
				}
			}
		}
		return null;
	}

	private static final Pattern patTW = Pattern.compile("^zh-(Hant|HK|TW|MO)", Pattern.CASE_INSENSITIVE);
	public static String langFilter(String lang) {
		if (lang != null) {
			if (patTW.matcher(lang).find()) {
				lang = "zh-TW";
			} else if ("zh".equals(lang = lang.substring(0, 2))) {
				lang = "zh-CN";
			}
		}
		return lang;
	}

	public static String jsonString(Object input) {
		return Json.createReader(new StringReader(input.toString())).read().toString();
	}
}
