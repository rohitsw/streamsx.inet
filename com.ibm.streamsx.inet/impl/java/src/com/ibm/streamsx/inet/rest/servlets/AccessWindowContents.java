/*
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2011, 2012 
*/
package com.ibm.streamsx.inet.rest.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.json.java.OrderedJSONObject;
import com.ibm.streams.operator.Attribute;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.encoding.EncodingFactory;
import com.ibm.streams.operator.encoding.JSONEncoding;
import com.ibm.streams.operator.types.RString;
import com.ibm.streamsx.inet.window.WindowContentsAtTrigger;

public class AccessWindowContents extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -5897438813664075070L;
	
	private final WindowContentsAtTrigger<Tuple> windowContents;
	private final StreamSchema schema;
	private final JSONEncoding<JSONObject,JSONArray> jsonEncoding = EncodingFactory.getJSONEncoding();

	public AccessWindowContents(WindowContentsAtTrigger<Tuple> windowContents) {
		this.windowContents = windowContents;
		schema = windowContents.getInput().getStreamSchema();
	}
	
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        action(request, response);
    }

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

        action(request, response);
	}

	private void action(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
	    
	    Object partition = getPartitionObject(request);
	    
	    Iterable<Attribute> attributes;
	    
	    String[] selectAttributesA = request.getParameterValues("attribute");
	    if (selectAttributesA == null) {
	        attributes = schema;
	    } else {
	        
            List<Attribute> la = new ArrayList<Attribute>(selectAttributesA.length);           
            
	        for (String name : selectAttributesA) {
	            if (schema.getAttributeIndex(name) != -1)
	                la.add(schema.getAttribute(name));  
	        }
	        attributes = la;
	    }
	    
	    String[] suppressA = request.getParameterValues("suppress");
	       
        if (suppressA != null) {
            Set<String> suppress = new HashSet<String>();
            Collections.addAll(suppress, suppressA);
            List<Attribute> la = new ArrayList<Attribute>(
                    schema.getAttributeCount());

            for (Attribute attr : attributes) {
                if (!suppress.contains(attr.getName()))
                    la.add(attr);
            }
            attributes = la;
        }
	    		
		final List<Tuple> tuples = windowContents.getWindowContents(partition);

		response.setCharacterEncoding("UTF-8");
		PrintWriter out = response.getWriter();
		response.setHeader("Cache-Control",
				"no-cache, no-store, must-revalidate");
		response.setStatus(HttpServletResponse.SC_OK);
		
		response.setContentType("application/json");
		formatJSON(out, tuples, attributes); 

		out.flush();
		out.close();
	}
	
	private Object getPartitionObject(HttpServletRequest request) throws ServletException {
   
	   final List<Attribute> pa = windowContents.getPartitionAttributes();
	   // Not partitioned.
	   if (pa.size() == 0)
	       return 0;
	   
       String[] partitionValues = request.getParameterValues("partition");
       if (partitionValues == null)
           return null; // partitioned but no partition given, return all the data.
	   
	   if (pa.size() == 1)
	       return getAttributeObject(pa.get(0), partitionValues[0]);
	   
	   List<Object> partitionList = new ArrayList<Object>(pa.size());
	   
	   for (int i = 0; i < pa.size(); i++){
	       partitionList.add(getAttributeObject(pa.get(i), partitionValues[i]));
	   }
	   return partitionList;
	}
	
	private static Object getAttributeObject(Attribute attribute, String value) throws ServletException {
	    switch (attribute.getType().getMetaType()) {
	    case USTRING:
	        return value;
	    case BSTRING:
	    case RSTRING:
	        return new RString(value);
	    case BOOLEAN:
	        return new RString(value);
	    case INT8:
	        return Byte.valueOf(value);
	    case INT16:
	        return Short.valueOf(value);
	    case INT32:
	        return Integer.valueOf(value);
	    case INT64:
	        return Long.valueOf(value);
	    case UINT8:
	        return Short.valueOf(value).byteValue();
        case UINT16:
            return Integer.valueOf(value).shortValue();
        case UINT32:
            return Long.valueOf(value).intValue();
        case UINT64:
            return new BigInteger(value).longValue();
	    default:
	        throw new ServletException("Unsupported partition type for attribute: " + attribute.getName());
	    }
	}


    protected void formatJSON(PrintWriter out, List<Tuple> tuples, Iterable<Attribute> attributes) throws IOException {
        JSONArray jsonTuples = new JSONArray();
        for (Tuple tuple : tuples) {
            JSONObject jsonTuple = tuple2JSON(jsonEncoding, attributes, tuple);
            jsonTuples.add(jsonTuple);
        }
        
        out.println(jsonTuples.serialize());
    }
    
    public static JSONObject tuple2JSON(JSONEncoding<JSONObject,JSONArray>  encoding, Tuple tuple) {
        
        return tuple2JSON(encoding, tuple.getStreamSchema(), tuple);
    }

    public static JSONObject tuple2JSON(JSONEncoding<JSONObject,JSONArray>  encoding, Iterable<Attribute> attributes, Tuple tuple) {
        JSONObject jsonTuple = new OrderedJSONObject();
        for (Attribute attr : attributes) {
            Object o = encoding.getAttributeObject(tuple, attr);
            jsonTuple.put(attr.getName(), o);
        }
        return jsonTuple;
    }
}
