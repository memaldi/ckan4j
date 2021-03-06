/**
 * Copyright 2014 Sciamlab s.r.l.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sciamlab.ckan4j;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.InternalServerErrorException;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.sciamlab.ckan4j.CKANApiClient.CKANApiClientBuilder;
import com.sciamlab.ckan4j.exception.CKANException;
import com.sciamlab.common.dao.SciamlabDAO;
import com.sciamlab.common.util.SciamlabCollectionUtils;

public class CKANRating {
	
	private static final Logger logger = Logger.getLogger(CKANRating.class);
	
	private SciamlabDAO dao;
	private CKANApiClient ckan;
	private String rating_table;
	
	private CKANRating(CKANRatingBuilder builder){
		this.dao = builder.dao;
		this.rating_table = builder.rating_table;
		try {
			this.ckan = CKANApiClientBuilder.init(builder.ckan_api_endpoint.toString()).apiKey(builder.ckan_api_key).build();
		} catch (MalformedURLException e) {
			//should never be thrown
			e.printStackTrace();
		}
	}
	
	/**
	 * Retrieves a rating for the given dataset
	 * 
	 * @param dataset_id
	 * @throws CKANException 
	 */
	public JSONObject getRate(String dataset_id) throws CKANException {
		//getting dataset info
		JSONObject dataset = this.ckan.packageShow(dataset_id);
		Integer rating_count = 0, rating_average_int = 0;
		Double rating_average = 0.0;
		for(Object tmp : SciamlabCollectionUtils.asList(dataset.getJSONArray("extras"))){
			JSONObject extra = (JSONObject)tmp;
			if("rating_count".equals(extra.getString("key")))
					rating_count = extra.getInt("value");
			else if("rating_average_int".equals(extra.getString("key")))
				rating_average_int = extra.getInt("value");
			else if("rating_average".equals(extra.getString("key")) && !"None".equals(extra.getInt("value")))
					rating_average = extra.getDouble("value");
		}
		logger.debug("current count: "+rating_count+" average: "+rating_average_int);
		
    	JSONObject json = new JSONObject();
    	json.put("dataset", dataset_id);
		json.put("count", rating_count);
		json.put("rating", rating_average_int);
		return json;
	}
	
	/**
	 * Inserts a rating for the given dataset
	 * 
	 * @param dataset_id
	 * @param user_id
	 * @param rating
	 * @throws Exception 
	 */
	public JSONObject postRate(final String dataset_id, final String user_id, final Integer rating) throws Exception {
		if(rating==null || rating > 5 || rating < 1)
			throw new Exception("Rating must be in [1,2,3,4,5]");
		if(user_id==null || "".equals(user_id))
			throw new Exception("User is mandatory");
		
		//getting dataset info
		JSONObject dataset = this.ckan.packageShow(dataset_id);
		for(Object tmp : SciamlabCollectionUtils.asList(dataset.getJSONArray("extras"))){
			JSONObject extra = (JSONObject)tmp;
			if("spatial".equals(extra.getString("key"))){
				//converting spatial into string to avoid parsing issue during update
				JSONObject spatial = new JSONObject(extra.getString("value"));
				if(spatial.opt("type")!=null && spatial.opt("coordinates")!=null)
					extra.put("value", "{ \"type\": \""+spatial.getString("type")+"\", \"coordinates\": "+ spatial.getJSONArray("coordinates") +" }");
				break;
			}
		}
		
		//once checked, then register the rating
		final Date now = new Date(new java.util.Date().getTime());
		//update rating
		int sql_result = dao.execUpdate("UPDATE " + this.rating_table
				+ " SET rating = ?, modified = ?"
				+ " WHERE user_id = ? AND package_id = ?;", new ArrayList<Object>(){{add(rating); add(now); add(user_id); add(dataset_id);}});
		if(sql_result==0){
			logger.debug("No existing rating found for user '"+user_id+"' on dataset '"+dataset_id+"'. Need to create a new one");
			//insert new rating
			sql_result = dao.execUpdate("INSERT INTO " + this.rating_table + " (user_id, package_id, rating, created, modified)"
					+ " VALUES (?, ?, ?, ?, ?);", 
					new ArrayList<Object>(){{add(user_id); add(dataset_id); add(rating); add(now); add(now);}});
		}
		
		//calculating the new average
		Map<String, Properties> map = dao.execQuery(
			"SELECT package_id, count(*) as count, avg(rating) as rating FROM " + this.rating_table
			+" WHERE package_id = ? GROUP BY package_id", new ArrayList<Object>(){{ add(dataset_id); }}, "package_id", 		
			new ArrayList<String>(){{ add("rating"); add("count"); }} );
    	Properties p = map.get(dataset_id);
    	Double rating_average = ((BigDecimal) p.get("rating")).doubleValue();
    	Integer rating_count = ((Long) p.get("count")).intValue();
    	
		int rating_average_int = this.roundAverageToInteger(rating_average);
    	boolean rating_count_found = false, rating_average_int_found = false, rating_average_found = false;
    	for(Object tmp : SciamlabCollectionUtils.asList(dataset.getJSONArray("extras"))){
			JSONObject extra = (JSONObject)tmp;
			if("rating_count".equals(extra.getString("key"))){
				extra.put("value", rating_count);
				rating_count_found = true;
			}else if("rating_average_int".equals(extra.getString("key"))){
				extra.put("value", rating_average_int);
				rating_average_int_found = true;
			}else if("rating_average".equals(extra.getString("key"))){
				extra.put("value", rating_average);
				rating_average_found = true;
			}else if("spatial".equals(extra.getString("key"))){
				//converting spatial into string to avoid parsing issue during update
				JSONObject spatial = new JSONObject(extra.getString("value"));
				if(spatial.opt("type")!=null && spatial.opt("coordinates")!=null)
					dataset.put("spatial", "{ \"type\": \""+spatial.getString("type")+"\", \"coordinates\": "+ spatial.getJSONArray("coordinates") +" }");
			}
		}
    	if(!rating_count_found)
    		dataset.getJSONArray("extras").put(new JSONObject().put("key", "rating_count").put("value", rating_count));
    	if(!rating_average_int_found)
    		dataset.getJSONArray("extras").put(new JSONObject().put("key", "rating_average_int").put("value", rating_average_int));
    	if(!rating_average_found)
    		dataset.getJSONArray("extras").put(new JSONObject().put("key", "rating_average").put("value", rating_average));
    	
    	//updating rating on CKANApiClient
    	dataset = this.ckan.packageUpdate(dataset);
    	logger.debug(dataset);
		logger.info("Rating updated on CKANApiClient: avg "+rating_average_int+" count "+rating_count+" ["+dataset_id+"]");
    	
    	JSONObject json = new JSONObject();
    	json.put("dataset", dataset_id);
		json.put("count", rating_count);
		json.put("rating", rating_average_int);
		return json;
	}

	private int roundAverageToInteger(Double rating_average) {
		if(rating_average == 0.0) return  0;
    	else if(rating_average < 1.6) return  1;
    	else if(rating_average < 2.6) return  2;
    	else if(rating_average < 3.6) return  3;
    	else if(rating_average < 4.6) return  4;
    	else if(rating_average < 5.6) return  5;
		throw new InternalServerErrorException("rating "+rating_average+" is out of range 0-5");
	}
	
	public static class CKANRatingBuilder{
		
		private final SciamlabDAO dao;
		private final URL ckan_api_endpoint;
		private final String rating_table;
		private final String ckan_api_key;
		
		public static CKANRatingBuilder getInstance(SciamlabDAO dao, String ckan_api_endpoint, String api_key, String rating_table) throws MalformedURLException{
			return new CKANRatingBuilder(dao, ckan_api_endpoint, api_key, rating_table);
		}
		
		private CKANRatingBuilder(SciamlabDAO dao, String ckan_api_endpoint, String api_key, String rating_table) throws MalformedURLException {
			super();
			this.ckan_api_endpoint = new URL(ckan_api_endpoint);
			this.dao = dao;
			this.rating_table = rating_table;
			this.ckan_api_key = api_key;
		}

		public CKANRating build() {
			return new CKANRating(this);
		}
	}
}
