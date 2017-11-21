/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;


import gnu.io.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SerialCommunication {      
    private static ConcurrentMap<Byte,LoopbackCommunicationAdapter> idToNameMap;
    private static ConcurrentMap<LoopbackCommunicationAdapter,Byte> NameToIdMap;
    private static ConcurrentMap<Byte,MovementCommandMessage> messageLog;
    static byte[] outMessage;

  public SerialCommunication() {
      idToNameMap = new ConcurrentHashMap<>();
      NameToIdMap = new ConcurrentHashMap<>();
      outMessage = new byte[4];
      messageLog = new ConcurrentHashMap<>();
  }
  
  public static synchronized void clearCommunications() {
    outMessage = new byte[4];
  }
  
  public static synchronized void connectToCommunicationAdapter(Byte id, LoopbackCommunicationAdapter communicationAdapter){
    assert(!idToNameMap.containsKey(id) && !NameToIdMap.containsKey(communicationAdapter));
    idToNameMap.put(id, communicationAdapter);
    NameToIdMap.put(communicationAdapter,id);
  }
  
  public static synchronized void sendMessage(LoopbackCommunicationAdapter communicationAdapter,MovementCommandMessage commandMessage){
     assert(NameToIdMap.containsKey(communicationAdapter));
     byte[] message = commandMessage.getSerialMessage();
     byte commandId = (byte)(messageLog.size()+1);
     byte id = NameToIdMap.get(communicationAdapter);
     message[3] = id;
     message[4] = commandId;     
     outMessage = message;    
     messageLog.put(commandId, commandMessage);
  }    
  
    synchronized void  connect ( String portName ) throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if ( portIdentifier.isCurrentlyOwned() )
        {
            System.out.println("Error: Port is currently in use");
        }
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);
            
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(57600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
                
                InputStream in = serialPort.getInputStream();
                serialPort.notifyOnDataAvailable(true);
                OutputStream out = serialPort.getOutputStream();
                               
                (new Thread(new SerialWriter(out))).start();
                
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);

            }
            else
            {
                System.out.println("Error: Only serial ports are handled by this example.");
            }
        }     
    }
    
    /**
     * Handles the input coming from the serial port. A new line character
     * is treated as the end of a block in this example. 
     */
    public static class SerialReader implements SerialPortEventListener 
    {
        private InputStream in;
        private byte[] buffer = new byte[1024];
        
        public SerialReader ( InputStream in )
        {
            this.in = in;
        }
        
        public void serialEvent(SerialPortEvent arg0) { 
            try
            {
                int[] header = new int[3];
                int data;
                for(int i = 0;i < 3;i++){
                    data = in.read();
                    header[i] = data;
                }
                if (header[0] == 'A'){
                  byte[] message = new byte[5];
                  in.read(message, 0, 5);
                  byte id = message[0];
                  byte orderId = message[1];
                  byte state = message[2];
                  byte isComplete = message[3];
                  byte isError = message[4];
                  if(isComplete == 1){
                    MovementCommandMessage cmd = messageLog.get(orderId);
                    idToNameMap.get(id).processMessage(cmd);
                  }
                }
                else if (header[0] == 'S'){
                  byte[] message = new byte[5];
                  in.read(message, 0, 5);
                  StatusMessage statusMessage = new StatusMessage(message[1]);
                  idToNameMap.get(message[0]).processMessage(statusMessage);
                }
                else if(header[0] == 'E'){
                  byte[] message = new byte[3];
                  in.read(message, 0, 3);
                  EmergencyMessage eMessage = new EmergencyMessage();
                  idToNameMap.get(message[0]).processMessage(eMessage);
                }
            }
            catch ( IOException e )
            {
                e.printStackTrace();
                System.exit(-1);
            }             
        }
    }

    /** */
    public static class SerialWriter implements Runnable 
    {
        OutputStream out;
        
        public SerialWriter ( OutputStream out )
        {
            this.out = out;
        }
        
        public void run ()
        {
          while(true)
            writeMessage();
        }
        
        private void writeMessage(){
          try
            {        
                Thread.sleep(100);
                this.out.write(outMessage);
            }
            catch ( IOException e )
            {
                e.printStackTrace();
                System.exit(-1);
            }  
            catch (InterruptedException ex){
              ex.printStackTrace();
              System.exit(-1);
            }
        }
    }
}
