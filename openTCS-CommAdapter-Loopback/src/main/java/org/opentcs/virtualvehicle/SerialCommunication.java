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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialCommunication {      
    private static ConcurrentMap<Byte,LoopbackCommunicationAdapter> idToNameMap;
    private static ConcurrentMap<LoopbackCommunicationAdapter,Byte> NameToIdMap;
    private static ConcurrentMap<Integer,MovementCommandMessage> messageLog;
    private static SerialPort serialPort;
    private static InputStream in;
    private static OutputStream out;
    private static TransportOrderCreator orderCreator;
    static byte[] outMessage;
    private static final Logger LOG = LoggerFactory.getLogger(SerialCommunication.class);

  public SerialCommunication() {
      idToNameMap = new ConcurrentHashMap<>();
      NameToIdMap = new ConcurrentHashMap<>();
      outMessage = new byte[4];
      messageLog = new ConcurrentHashMap<>();
      orderCreator = TransportOrderCreatorFactory.getTransportOrderCreator();
  }
  
  public static int decodeByte(byte head, byte tail){
      int iHead = head;
      int iTail = tail;
      return (iHead << 7) + iTail;
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
     int orderId = decodeByte(message[4],message[5]);
     messageLog.put(orderId,commandMessage);
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
            LOG.info("Connection Success");
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
                LOG.debug(Arrays.toString(header));
                if(header[0] == 'A' && header[1] == 'G' && header[2] == 'V'){                
                    System.out.println("AGV Message:");
                    byte[] message = new byte[5];
                    in.read(message, 0, 5);
                    System.out.println(Arrays.toString(message));
                    byte id = message[0];
                    int orderId =decodeByte(message[1], message[2]); // current point
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
                    } 
                  }
            
                if(header[0] == 'S' && header[1] == 'T' && header[2] == 'S')
                  {
                    System.out.println("Status message");
                    byte[] message = new byte[5];
                    in.read(message, 0, 5);
                    System.out.println(Arrays.toString(message));
                    StatusMessage statusMessage = new StatusMessage(message[1],message[2]);
                    idToNameMap.get(message[0]).processMessage(statusMessage);
                   
                  }
                if(header[0] == 'E' && header[1] == 'M' && header[2] == 'G')
                  {
                    System.out.println("Emergency message");
                    byte[] message = new byte[3];
                    in.read(message, 0, 3);
                    System.out.println(Arrays.toString(message));
                    EmergencyMessage eMessage = new EmergencyMessage();
                    idToNameMap.get(message[0]).processMessage(eMessage);                    
                  }
                if(header[0] == 'L' && header[1] == 'D' && header[2] == 'A'){
                  //Todo: enter the point name
                  System.out.println("Load message");
                  byte[] message = new byte[3];
                  in.read(message,0,3);
                  System.out.println(Arrays.toString(message));
                  orderCreator.createTransportOrderByPoint("Point-0010");//Change this
                  
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
