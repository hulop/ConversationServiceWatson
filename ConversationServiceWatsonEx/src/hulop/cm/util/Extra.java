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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.wink.json4j.JSON;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

public class Extra {

	private JSONObject directory_config, directory_data, infoCache;
	private final JSONObject nameCache = new JSONObject();
	private double lat, lng;
	private long lastChecked;

	public Extra() {
		try {
			directory_config = CommonUtil.getConfig().getJSONObject("directory_config");
			JSONObject location;
			try {
				location = new JSONObject(System.getenv("HULOP_INITIAL_LOCATION"));
			} catch (Exception e) {
				location = directory_config.getJSONObject("default_location");
			}
			lat = location.getDouble("lat");
			lng = location.getDouble("lng");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void putInfoMap(JSONObject context, String lang) throws JSONException {
		sync();
		if (directory_data != null) {
			JSONObject infoMap = infoCache.optJSONObject(lang);
			if (infoMap == null) {
				try {
					infoCache.put(lang, infoMap = createInfoMap(lang));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (infoMap != null) {
				context.putAll(infoMap);
				putDynamicMap(context, lang);
				if (context.has("maj_category_map_available")) {
					context.put("whole_maj_map", context.get("maj_category_map_available"));
				}
				if (context.has("sub_category_map_available")) {
					context.put("whole_map", context.get("sub_category_map_available"));
				}
				if (context.has("tags_map_available")) {
					context.put("whole_tags_map", context.get("tags_map_available"));
				}
				new MapSorter(context).run();
			}
		}
	}

	public void removeInfoMap(JSONObject context) {
		context.remove("info_map");
		context.remove("maj_category_map");
		context.remove("sub_category_map");
//		context.remove("min_category_map");
		context.remove("tags_map");
		context.remove("disabled_nodes");
		context.remove("maj_category_map_available");
		context.remove("sub_category_map_available");
//		context.remove("min_category_map_available");
		context.remove("tags_map_available");
		context.remove("alias_map");
		context.remove("building_floor_map");
		context.remove("building_floor_map_available");
		context.remove("building_group_map");
		context.remove("whole_maj_map");
		context.remove("whole_map");
		context.remove("whole_tags_map");
	}

	private void sync() {
		long now = System.currentTimeMillis();
		if (directory_data == null || now > lastChecked + 60 * 1000) {
			lastChecked = now;
			try {
				if (directory_data != null) {
					JSONObject last_updated = (JSONObject)exec(directory_config.getString("endpoint_last_updated"));
					if (directory_data.getJSONObject("last_updated").equals(last_updated)) {
						return;
					}
				}
				infoCache = new JSONObject();
				directory_data = (JSONObject)exec(String.format(directory_config.getString("endpoint_directory"), lat, lng));
				lastDisableChecked = 0;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private JSONObject createInfoMap(String lang) throws JSONException {
		JSONObject root = directory_data.getJSONObject(lang);
		JSONObject info_map = new JSONObject(), alias_map = new JSONObject();
		JSONObject infoMap = new JSONObject().put("info_map", info_map).put("alias_map", alias_map);
		scanData(root, info_map);
		JSONArray aliases = RemoteConfig.getObject("aliases").optJSONArray(lang);
		if (aliases != null) {
			for (Object o : aliases) {
				JSONObject obj = (JSONObject) o;
				String conv_name = obj.optString("name");
				String conv_pron = obj.optString("pron");
				String dest_name = obj.optString("dest_name");
				if (conv_name != null && dest_name != null) {
					JSONObject info = info_map.optJSONObject(dest_name);
					if (info != null) {
						alias_map.append(dest_name, conv_name);
						JSONObject lastInfo = info_map.optJSONObject(conv_name);
						info_map.put(conv_name, info = (JSONObject) info.clone());
						info.put("name", conv_name);
						info.put("pron", conv_pron != null ? conv_pron : conv_name);
						if (lastInfo != null) {
							info.put("nodes", info.getString("nodes") + "|" + lastInfo.getString("nodes"));
						}
					}
				}
			}
		}
		JSONObject name_map = nameCache.optJSONObject(lang);
		if (name_map == null) {
			try {
				name_map = CommonUtil.load("/data/categories/" + lang + ".json");
			} catch (Exception e) {
				name_map = new JSONObject();
			}
			nameCache.put(lang, name_map);
		}
		infoMap.put("maj_category_map", createTagsMap(root.optJSONObject("major_categories"), alias_map, name_map, "CAT_"));
		infoMap.put("sub_category_map", createTagsMap(root.optJSONObject("sub_categories"), alias_map, name_map, "CAT_"));
		System.out.println("---- start of infoMap ----\n" + infoMap.toString(4) + "\n---- end ----");
		return infoMap;
	}

	private void putDynamicMap(JSONObject context, String lang) throws JSONException {
		JSONObject root = directory_data.getJSONObject(lang);
		JSONObject alias_map = context.optJSONObject("alias_map");
		JSONObject tag_name_map = RemoteConfig.getObject("tags").optJSONObject(lang);
//		context.put("min_category_map", createTagsMap(root.optJSONObject("minor_categories"), alias_map, tag_name_map, ""));
		context.put("tags_map", createTagsMap(root.optJSONObject("tags"), alias_map, tag_name_map, ""));

		try {
			JSONArray disabled_nodes = getDisabledNodes(context);
			context.put("disabled_nodes", disabled_nodes);
			JSONArray hidden_names = RemoteConfig.getObject("hidden_names").optJSONArray(lang, new JSONArray());
			addInaccessibleNodes(context, hidden_names = new JSONArray(hidden_names.toString()));
			context.put("hidden_names", hidden_names);
			filterNames(context, "maj_category_map", disabled_nodes, hidden_names);
			filterNames(context, "sub_category_map", disabled_nodes, hidden_names);
//			filterNames(context, "min_category_map", disabled_nodes, hidden_names);
			filterNames(context, "tags_map", disabled_nodes, hidden_names);
			addBuildingMap(context, "building_floor_map", root.optJSONObject("building_floors"), hidden_names);

			// Add building_group_map
			JSONObject group = root.optJSONObject("building_group");
			if (group != null && RemoteConfig.getObject("building_group").length() > 0) {
				JSONObject name_map = nameCache.optJSONObject(lang);
				JSONObject building_map = new JSONObject();
				for (String building : JSONObject.getNames(group)) {
					JSONObject from = group.getJSONObject(building);
					JSONObject to = new JSONObject();
					building_map.put(building, to);
					to.put("maj_category_map", createTagsMap(from.optJSONObject("major_categories"), alias_map, name_map, "CAT_"));
					to.put("sub_category_map", createTagsMap(from.optJSONObject("sub_categories"), alias_map, name_map, "CAT_"));
//					to.put("min_category_map", createTagsMap(from.optJSONObject("minor_categories"), alias_map, tag_name_map, ""));
					to.put("tags_map", createTagsMap(from.optJSONObject("tags"), alias_map, tag_name_map, ""));
					filterNames(to, "maj_category_map", disabled_nodes, hidden_names);
					filterNames(to, "sub_category_map", disabled_nodes, hidden_names);
//					filterNames(to, "min_category_map", disabled_nodes, hidden_names);
					filterNames(to, "tags_map", disabled_nodes, hidden_names);
				}
				context.put("building_group_map", building_map);
				mergeGroup(building_map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void mergeGroup(JSONObject building_map) throws JSONException {
		JSONObject building_group = (JSONObject)RemoteConfig.getObject("building_group").clone();
		JSONArray removeBuildings =  (JSONArray)building_group.remove("disable");
		for (String group_name : JSONObject.getNames(building_group)) {
			JSONObject to = new JSONObject();
			building_map.put(group_name, to);
			for (Object building : building_group.getJSONArray(group_name)) {
				JSONObject from = building_map.optJSONObject((String) building);
				if (from != null) {
					for (String map_name : JSONObject.getNames(from)) {
						JSONObject map_to = to.optJSONObject(map_name);
						if (map_to == null) {
							to.put(map_name, map_to = new JSONObject());
						}
						JSONObject map_from = from.getJSONObject(map_name);
						for (String key : JSONObject.getNames(map_from)) {
							JSONArray titles = map_to.optJSONArray(key);
							if (titles == null) {
								map_to.put(key, titles = new JSONArray());
							}
							for (Object title : map_from.getJSONArray(key)) {
								if (!titles.contains(title)) {
									titles.add(title);
								}
							}
						}
					}
				}
			}
		}
		if (removeBuildings != null) {
			for(Object building: removeBuildings) {
				building_map.remove(building);
			}
		}
	}

	public JSONObject createTranslationMap(String lang) throws JSONException {
		JSONArray translations = RemoteConfig.getObject("translations").optJSONArray(lang);
		if (translations == null) {
			return null;
		}
		JSONObject translation_map = new JSONObject();
		for (Object o : translations) {
			JSONObject obj = (JSONObject) o;
			String conv_name = obj.optString("name");
			String conv_pron = obj.optString("pron");
			if (conv_name != null && conv_pron != null) {
				translation_map.put(conv_name, new JSONObject().put("pron", conv_pron));
			}
		}
		return translation_map;
	}

	private void addCategoryMap(JSONObject map, String key, List<String> names, JSONObject aliases) throws JSONException {
		JSONArray array = map.optJSONArray(key);
		if (array == null) {
			map.put(key, array = new JSONArray());
		}
		for (String name : names) {
			if (!array.contains(name)) {
				array.add(name);
			}
			if (aliases != null && aliases.has(name)) {
				addCategoryMap(map, key, (List<String>) aliases.get(name), null);
			}
		}
	}

	private JSONObject createTagsMap(JSONObject sourceMap, JSONObject aliases, JSONObject dict, String prefix) throws JSONException {
		JSONObject destMap = new JSONObject();
		if (sourceMap != null) {
			for (Iterator<String> it = sourceMap.keys(); it.hasNext();) {
				String key = it.next();
				addCategoryMap(destMap, dict != null && dict.has(prefix + key) ? dict.getString(prefix + key) : key, (List<String>) sourceMap.get(key), aliases);
			}
		}
		return destMap;
	}

	private JSONObject scanData(JSONObject root, JSONObject target) {
		try {
			for (Object _section : root.getJSONArray("sections")) {
				for (Object _item : ((JSONObject) _section).getJSONArray("items")) {
					JSONObject item = (JSONObject) _item;
					if (item.has("content")) {
						scanData(item.getJSONObject("content"), target);
					} else {
						String name = item.getString("title");
						JSONArray nodes = new JSONArray(item.getString("nodeID").split("\\|"));
						JSONObject info = target.optJSONObject(name);
						if (info != null) {
							for (String node : info.getString("nodes").split("\\|")) {
								if (!nodes.contains(node)) {
									nodes.put(node);
								}
							}
						} else {
							target.put(name, info = new JSONObject().put("name", name));
							if (item.has("titlePron")) {
								info.put("pron", item.getString("titlePron"));
							}
							if (item.has("short_description")) {
								info.put("pr_short", item.getString("short_description"));
							}
						}
						for (String key : new String[] { "user_stroller", "user_wheelchair" }) {
							if (item.has(key)) {
								info.put(key, item.get(key));
							}
						}
						info.put("nodes", nodes.join("|"));
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return target;
	}

	private Object exec(String uri) throws Exception {
		System.out.println(uri);
		Request request = Request.Get(new URI(uri));
		Response response = Executor.newInstance().execute(request);
		Content content = response.returnContent();
		return JSON.parse(content.asStream());
	}

	private JSONArray disabledNodes;
	private long lastDisableChecked;

	private JSONArray getDisabledNodes(JSONObject context) throws Exception  {
		long now = System.currentTimeMillis();
		if (now > lastDisableChecked + 10 * 60 * 1000) {
			lastDisableChecked = now;
			disabledNodes = (JSONArray) exec(
					String.format(directory_config.getString("endpoint_disabled_nodes"), lat, lng));
		}
		JSONArray disabled_nodes = new JSONArray();
		if (disabledNodes != null) {
			JSONObject info_map = context.getJSONObject("info_map");
			for (Iterator<String> it = info_map.keys(); it.hasNext();) {
				String name = it.next();
				if (!disabled_nodes.contains(name)) {
					boolean available = false;
					for (String node : info_map.getJSONObject(name).getString("nodes").split("\\|")) {
						if (!disabledNodes.contains(node)) {
							available = true;
							break;
						}
					}
					if (!available) {
						disabled_nodes.put(name);
					}
				}
			}
		}
		return disabled_nodes;
	}

	private void addInaccessibleNodes(JSONObject context, JSONArray nodes) throws Exception  {
		String user_mode = context.optString("user_mode", "user_general");
		JSONObject info_map = context.getJSONObject("info_map");
		for (Iterator<String> it = info_map.keys(); it.hasNext();) {
			String name = it.next();
			if (!nodes.contains(name) && !info_map.getJSONObject(name).optBoolean(user_mode, true)) {
				nodes.put(name);
			}
		}
	}

	private void filterNames(JSONObject context, String mapName, JSONArray excludes, JSONArray hidden_names) throws JSONException {
		JSONObject allObj = context.optJSONObject(mapName);
		if (allObj != null) {
			context.put(mapName, allObj = new JSONObject(allObj.toString())); // deep clone
			JSONObject availableObj = new JSONObject();
			context.put(mapName + "_available", availableObj);
			for (Iterator<String> it = allObj.keys(); it.hasNext();) {
				String key = it.next();
				JSONArray names = allObj.getJSONArray(key);
				for (Object hide : hidden_names) {
					names.remove(hide);
				}
				names = (JSONArray)names.clone();
				for (Object ex : excludes) {
					names.remove(ex);
				}
				if (names.length() > 0) {
					availableObj.put(key, names);
				}
			}
		}
	}

	private void addBuildingMap(JSONObject context, String mapName, JSONObject building_floor_map, JSONArray hidden_names) throws JSONException {
		if (building_floor_map != null) {
			JSONObject map_any = new JSONObject(), map_available = new JSONObject();
			for (Iterator<String> bit = building_floor_map.keys(); bit.hasNext();) {
				String building = bit.next();
				JSONObject floor_map = building_floor_map.getJSONObject(building);
				for (Iterator<String> fit = floor_map.keys(); fit.hasNext();) {
					String floor = fit.next();
					for (Object obj : floor_map.getJSONArray(floor)) {
						JSONObject facil = (JSONObject) obj;
						String title = facil.getString("title");
						if (hidden_names.contains(title)) {
							continue;
						}
						put(map_any, building, floor, title);
						if (disabledNodes != null && !disabledNodes.contains(facil.getString("node"))) {
							put(map_available, building, floor, title);
						}
					}
				}
			}
			context.put(mapName, map_any);
			context.put(mapName + "_available", map_available);
		}
	}

	private static void put(JSONObject map, String objectKey, String arrayKey, Object value) throws JSONException {
		JSONObject object = map.optJSONObject(objectKey);
		if (object == null) {
			map.put(objectKey, object = new JSONObject());
		}
		JSONArray array = object.optJSONArray(arrayKey);
		if (array == null) {
			object.put(arrayKey, array = new JSONArray());
		}
		if (!array.contains(value)) {
			array.add(value);
		}
	}
}

class MapSorter {
	private static final String[] MAP_NAMES = { "maj_category_map", "maj_category_map_available", "sub_category_map",
			"sub_category_map_available", "tags_map", "tags_map_available", "building_floor_map",
			"building_floor_map_available", "whole_map", "whole_maj_map", "whole_tags_map" };

	private final JSONObject context, infoMap;

	public MapSorter(JSONObject context) {
		this.context = context;
		this.infoMap = context.optJSONObject("info_map");
	}

	public void run() {
		for (String name : MAP_NAMES) {
			sortMaps(context.opt(name));
		}
	}

	private Comparator<Object> comparator = new Comparator<Object>() {
		@Override
		public int compare(Object o1, Object o2) {
			if (o1 instanceof String && o2 instanceof String) {
				String title1 = (String)o1, title2 = (String)o2;
				if (infoMap == null) {
					return 0;//title1.compareToIgnoreCase(title2);
				}
				JSONObject info1 = infoMap.optJSONObject(title1), info2 = infoMap.optJSONObject(title2);
				if (info1 != null && info2 != null) {
					String pron1 = info1.optString("pron"), pron2 = info2.optString("pron");
					if (pron1 != null && pron2 != null) {
						return pron1.compareToIgnoreCase(pron2);
					}
				}
			}
			return 0;
		}
	};

	private void sortMaps(Object parent) {
		if (parent instanceof JSONObject) {
			for (Object child : ((JSONObject) parent).values()) {
				sortMaps(child);
			}
		} else if (parent instanceof JSONArray) {
			((JSONArray) parent).sort(comparator);
		}
	}
}
