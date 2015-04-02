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
package com.sciamlab.ckan4j.exception;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 
 * @author SciamLab
 *
 */

public class CKANException extends Exception {

	private static final long serialVersionUID = -2213429135494785501L;
	
	private JSONObject error;

	public CKANException(JSONObject error){
		super((error.opt("__type")!=null)?error.getString("__type"):error.toString());
		this.error = error;
	}
	
	public CKANException(JSONException e){
		super(e);
		this.error = new JSONObject();
		this.error.put("__type", "JSONException");
		this.error.put("message", e.getMessage());
	}
	
	public CKANException(JSONException e, String text){
		super(e);
		this.error = new JSONObject();
		this.error.put("__type", "JSONException");
		this.error.put("message", e.getMessage());
		this.error.put("text", text);
	}

	public JSONObject getError() {
		return error;
	}
	
}