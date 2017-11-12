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
import java.util.HashMap;
import java.util.Map;

public class SerialCommunication {   
    private Map<Byte,LoopbackCommunicationAdapter> idToNameMap;
    private Map<LoopbackCommunicationAdapter,Byte> NameToIdMap;
    static byte[] outMessage;

  public SerialCommunication() {
      idToNameMap = new HashMap<>();
      NameToIdMap = new HashMap<>();
      outMessage = new byte[4];
      
  }
  
  public void connectToCommunicationAdapter(Byte id, LoopbackCommunicationAdapter communicationAdapter){
    assert(!idToNameMap.containsKey(id) && !NameToIdMap.containsKey(communicationAdapter));
    idToNameMap.put(id, communicationAdapter);
    NameToIdMap.put(communicationAdapter,id);
  }
  
  public void sendMessage(LoopbackCommunicationAdapter communicationAdapter,byte[] message){
     assert(NameToIdMap.containsKey(communicationAdapter));
     message[0] = 65;
     message[1] = 67;
     outMessage = message;
     
  }    
  
  void connect ( String portName ) throws Exception
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
            int data;
          
            try
            {
                int len = 0;
                while ( ( data = in.read()) > -1 )
                {
                    System.out.println(data);
                    if ( data == '\n' ) {
                        break;
                    }
                    buffer[len++] = (byte) data;
                }
                //System.out.print(new String(buffer,0,len));
                buffer = new byte[1024];
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
