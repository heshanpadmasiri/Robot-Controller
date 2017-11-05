/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import java.util.HashMap;
import java.util.Map;

public class SerialCommunication {  
    private Map<Long,LoopbackCommunicationAdapter> idToAdapterMap;
    private Map<LoopbackCommunicationAdapter,Long> adapterToIdMap;

  public SerialCommunication() {
      idToAdapterMap = new HashMap<>();
  }
  
  public void connectToCommunicationAdapter(long id, LoopbackCommunicationAdapter comAdapter){
    assert(!idToAdapterMap.containsKey(id) && !adapterToIdMap.containsKey(comAdapter));
    idToAdapterMap.put(id, comAdapter);
    adapterToIdMap.put(comAdapter,id);
  }
  
  public void sendMessage(LoopbackCommunicationAdapter comAdapter,byte[] message){
    
  }
    
}
