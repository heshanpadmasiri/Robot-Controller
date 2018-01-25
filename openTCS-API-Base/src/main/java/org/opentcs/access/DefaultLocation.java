package org.opentcs.access;


import java.io.Serializable;
import java.util.HashMap;

/**
 *
 * @author Heshan
 */
public class DefaultLocation implements Serializable{
    HashMap<String,String> locations;

  private static DefaultLocation instance = new DefaultLocation();
    
  private DefaultLocation() {
    locations  = new HashMap<>();
  }

  public static DefaultLocation getInstance() {
    return instance;
  }
  
  public boolean containsValue(String key){
    return locations.containsKey(key);
  }
  
  public void enterValue(String key, String value){
    locations.put(key, value);
  }
  
  public String getValue(String key){
    return locations.get(key);
  } 
}

