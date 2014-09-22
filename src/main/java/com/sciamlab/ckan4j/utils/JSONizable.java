package com.sciamlab.ckan4j.utils;

import net.sf.json.JSON;

/**
 * 
 * @author SciamLab
 *
 */

public interface JSONizable {

	public JSON toJSON();
	public JSON toJSON(boolean goDeep);
}
