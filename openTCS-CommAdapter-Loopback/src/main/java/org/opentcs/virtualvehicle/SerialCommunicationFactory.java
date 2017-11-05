/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

public class SerialCommunicationFactory {
    private static final SerialCommunication SERIAL_COMMUNICATION = new SerialCommunication();
    private static long vehicleId = 0;
        
    
    public SerialCommunicationFactory(LoopbackCommunicationAdapter communicationAdapter){        
        synchronized(SERIAL_COMMUNICATION){
          SERIAL_COMMUNICATION.connectToCommunicationAdapter(vehicleId, communicationAdapter);
          vehicleId++;
        }
    }

  public  SerialCommunication getSerialCommunication() {
    return SERIAL_COMMUNICATION;
  }    
    
}
