/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.util;



import org.brunel.action.Action;
import org.brunel.build.d3.D3Builder;
import org.brunel.build.util.BuilderOptions;
import org.brunel.data.Dataset;
import org.brunel.data.io.CSV;
import org.brunel.model.VisItem;

import com.google.gson.Gson;

/**
 * Brunel integration methods provided for services and other languages.  Only primitives are used for language integration methods
 * 
 * Note, these methods currently assume a single dataset.
 *
 */
public class D3Integration {
	
	private static final Gson gson = new Gson();

	/**
	 * Create and return the Brunel results as a String containing the Brunel JSON.
	 * @param data the data as a CSV String
	 * @param brunelSrc the brunel syntax
	 * @param width the desired width for the visualization
	 * @param height the desired height for the visualization
	 * @param visId an identifier used in the SVG tag that will contain the visualization
	 * @return a String that is JSON containing the Brunel JS, CSS and interactive control metadata.
	 */
	
	//Note:   This method is called from other languages.
	//Do not modify this method signature without checking all language integrations.
    public static String createBrunelJSON(String data, String brunelSrc, int width,  int height, String visId) {
				BrunelD3Result result = createBrunelResult(data, brunelSrc, width, height, visId);
				return gson.toJson(result) ;
    }
    
	/**
	 * Create and return the Brunel results as a String containing the Brunel JSON.
	 * @param data the data as a CSV String
	 * @param brunelSrc the brunel syntax
	 * @param width the desired width for the visualization
	 * @param height the desired height for the visualization
	 * @param visId an identifier used in the SVG tag that will contain the visualization
	 * @return a Gson serializable object containing the Brunel JS, CSS and interactive control metadata.
	 */
    
    public static BrunelD3Result createBrunelResult(String data, String brunelSrc, int width,  int height, String visId) {
    			Dataset dataset = makeBrunelData(data);
				D3Builder builder = makeD3(dataset, brunelSrc, width, height, visId);
				BrunelD3Result result = new BrunelD3Result();
				result.css = builder.getStyleOverrides();
				result.js = builder.getVisualization().toString();
				result.controls = builder.getControls();
				return result;
    }
    
	
	//Creates a D3Builder to produce the d3 output
    public static D3Builder makeD3(Dataset data, String actionText, int width, int height, String visId) {
    	try {
            BuilderOptions options = new BuilderOptions();
            options.visIdentifier = visId;
            D3Builder builder = D3Builder.make(options);
            VisItem item = makeVisItem(data, actionText);
            builder.build(item, width, height);
            return builder;
    	} catch (Exception ex) {
        	ex.printStackTrace();
            throw new IllegalArgumentException("Could not execute Brunel: " + actionText, ex);
        } 
    }

    //Create a Dataset instance given CSV
    private static Dataset makeBrunelData(String data) {
    	if (data.isEmpty()) return null;
    	try {
            return  Dataset.make(CSV.read(data));
    	 } catch (Exception e) {
             throw new IllegalArgumentException("Could not create data as CSV from content", e);
         }    	
       
    }

 
    //Create the VisItem instance for the given Brunel
    private static VisItem makeVisItem(Dataset brunel, String actionText) {
        Action action = Action.parse(actionText);
        if (brunel == null) return action.apply();
        return action.apply(brunel);
    }



}
