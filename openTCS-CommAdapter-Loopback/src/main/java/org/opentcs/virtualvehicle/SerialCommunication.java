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
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SerialCommunication {      
    private static ConcurrentMap<Byte,LoopbackCommunicationAdapter> idToNameMap;
    private static ConcurrentMap<LoopbackCommunicationAdapter,Byte> NameToIdMap;
    private static ConcurrentMap<Byte,MovementCommandMessage> messageLog;
    private static SerialPort serialPort;
    private static InputStream in;
    private static OutputStream out;
    private static TransportOrderCreator orderCreator;
    static byte[] outMessage;

  public SerialCommunication() {
      idToNameMap = new ConcurrentHashMap<>();
      NameToIdMap = new ConcurrentHashMap<>();
      outMessage = new byte[4];
      messageLog = new ConcurrentHashMap<>();
      orderCreator = TransportOrderCreatorFactory.getTransportOrderCreator();
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
     byte id = NameToIdMap.get(communicationAdapter);
     message[3] = id;     
     outMessage = message;    
     System.out.println("Message updated to:" + Arrays.toString(outMessage));
     messageLog.put(message[4],commandMessage);
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
                serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(57600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
                
                in = serialPort.getInputStream();
                serialPort.notifyOnDataAvailable(true);
                out = serialPort.getOutputStream();
                               
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
    
    public static void disconnectAdapter(LoopbackCommunicationAdapter adapter){
       Byte id = NameToIdMap.get(adapter);
       System.out.println("Disconnecting adapter");
       if(id != null){
         NameToIdMap.remove(adapter);
         idToNameMap.remove(id);
         if(NameToIdMap.isEmpty()){
           serialPort.removeEventListener();
           if(serialPort != null){
             try{
               serialPort.removeEventListener();
               serialPort.close();
               in.close();
               out.close();
             } catch(IOException ex){
               System.out.println("Serial streamclosing failed!");
               ex.printStackTrace();
             }
           }
         }
       }
    }
    
    /**
     * Handles the input coming from the serial port. A new line character
     * is treated as the end of a block in this example. 
     * print the message recieved to the std out
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
                byte[] header = new byte[3];
                in.read(header, 0, 3);
              
              switch (header[0]) {
                case 'A':
                  {
                    System.out.println("AGV Message:");
                    byte[] message = new byte[5];
                    in.read(message, 0, 5);
                    System.out.println(Arrays.toString(message));
                    byte id = message[0];
                    byte orderId = message[1]; // current point
                    byte state = message[4]; 
                    byte isComplete = message[3];
                    
                    idToNameMap.get(id).updateState(state);
                    if(isComplete == 1){
                      
                      if(messageLog.containsKey(orderId)){
                        System.out.println("Order:" + orderId + " completion recieved");
                        MovementCommandMessage cmd = messageLog.get(orderId);
                        messageLog.remove(orderId);
                        idToNameMap.get(id).processMessage(cmd);
                      } else {
                        System.out.println("Invalid order id no such order");
                        idToNameMap.get(id).updateLocation(orderId);
                      }                      
                    } break;
                  }
                case 'S':
                  {
                    System.out.println("Status message");
                    byte[] message = new byte[5];
                    in.read(message, 0, 5);
                    System.out.println(Arrays.toString(message));
                    StatusMessage statusMessage = new StatusMessage(message[1],message[2]);
                    idToNameMap.get(message[0]).processMessage(statusMessage);
                    break;
                  }
                case 'E':
                  {
                    System.out.println("Emergency message");
                    byte[] message = new byte[3];
                    in.read(message, 0, 3);
                    System.out.println(Arrays.toString(message));
                    EmergencyMessage eMessage = new EmergencyMessage();
                    idToNameMap.get(message[0]).processMessage(eMessage);
                    break;
                  }
                case 'L':{
                  //Todo: enter the point name
                  System.out.println("Load message");
                  byte[] message = new byte[3];
                  in.read(message,0,3);
                  System.out.println(Arrays.toString(message));
                  orderCreator.createTransportOrderByPoint("Point-0010");//Change this
                  break;
                }
                default:
                  break;
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
                System.out.println("Message:" + Arrays.toString(outMessage) + " written");
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
