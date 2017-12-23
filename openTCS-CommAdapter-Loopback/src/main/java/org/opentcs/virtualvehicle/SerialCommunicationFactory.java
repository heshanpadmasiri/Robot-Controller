/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import org.opentcs.data.model.Vehicle;
import gnu.io.*;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;

public class SerialCommunicationFactory {
    private static final SerialCommunication SERIAL_COMMUNICATION = new SerialCommunication();
    private static Byte vehicleId = 0;
    public volatile static String portId;    
    
    public SerialCommunicationFactory(LoopbackCommunicationAdapter CommunicationAdapter){
        PortSelector dialog = new PortSelector(new javax.swing.JFrame(), true);
        dialog.setVisible(true);
        Thread dialogThread = new Thread(dialog);
        dialogThread.start();
        try{
          dialogThread.join();
        } catch (InterruptedException ex){
          System.out.println("Port selection interrupted");
          ex.printStackTrace();
        }
        synchronized(SERIAL_COMMUNICATION){
          SerialCommunication.connectToCommunicationAdapter(vehicleId, CommunicationAdapter); 
        }
        try{
          //Change the com port when needed
          SERIAL_COMMUNICATION.connect(portId);
        } catch (Exception e){
          System.out.println("Error Unnable to connect on the port " + portId);
          e.printStackTrace();
        }
    }
    
    public static void setPortId(String portId){
      SerialCommunicationFactory.portId = portId;
    }

  public  SerialCommunication getSerialCommunication() {

        return SERIAL_COMMUNICATION;
  }

  private static HashSet<CommPortIdentifier> getAvailableSerialPorts() {
      HashSet<CommPortIdentifier> h = new HashSet<CommPortIdentifier>();
      Enumeration thePorts = CommPortIdentifier.getPortIdentifiers();
      while (thePorts.hasMoreElements()) {
            CommPortIdentifier com = (CommPortIdentifier) thePorts.nextElement();
            switch (com.getPortType()) {
                case CommPortIdentifier.PORT_SERIAL:
                    try {
                        CommPort thePort = com.open("CommUtil", 50);
                        thePort.close();
                        h.add(com);
                    } catch (PortInUseException e) {
                        System.out.println("Port, "  + com.getName() + ", is in use.");
                    } catch (Exception e) {
                        System.err.println("Failed to open port " +  com.getName());
                        e.printStackTrace();
                    }
            }
      }
      return h;
  }

  public static List<String> getAvailableSerialPortNames(){
        HashSet<CommPortIdentifier> portIdentifiers = getAvailableSerialPorts();
        return portIdentifiers.stream().map(CommPortIdentifier::getName).collect(Collectors.toList());
  }
  
  public static DefaultListModel<String> getListModel(){
        DefaultListModel<String> listModel = new DefaultListModel<>();
        List<String> portNames = SerialCommunicationFactory.getAvailableSerialPortNames();
        for(String each:portNames){
          listModel.addElement(each);
        }
        return listModel;
  }
    
}
