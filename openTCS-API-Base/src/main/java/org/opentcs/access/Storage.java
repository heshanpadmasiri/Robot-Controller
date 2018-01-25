/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.access;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Heshan
 */
public final class Storage {
    private static Storage instance = new Storage();
    
    private Storage(){
      
    }

  public static Storage getInstance() {
    return instance;
  }
  
  public DefaultLocation readStorage() {
    try{
      FileInputStream fis = new FileInputStream("defaultLocations.ser");
      ObjectInputStream ois = new ObjectInputStream(fis);
      DefaultLocation defaultLocation = (DefaultLocation) ois.readObject();
      ois.close();
      return defaultLocation;
    } catch (FileNotFoundException ex){
      saveStorage();
      return readStorage();
    }
    catch (IOException ex) {
      Logger.getLogger(Storage.class.getName()).log(Level.SEVERE, null, ex);
    }
      catch (ClassNotFoundException ex) {
        Logger.getLogger(Storage.class.getName()).log(Level.SEVERE, null, ex);
      }
    return null;
  }
  
  public void saveStorage(){
    try{
      FileOutputStream fos = new FileOutputStream("defaultLocations.ser");
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      DefaultLocation data = DefaultLocation.getInstance();
      oos.writeObject(data);
      oos.close();
    }
    catch (FileNotFoundException ex) {
      Logger.getLogger(Storage.class.getName()).log(Level.SEVERE, null, ex);
    }
      catch (IOException ex) {
        Logger.getLogger(Storage.class.getName()).log(Level.SEVERE, null, ex);
      }
  }
  
  public void saveStorage(DefaultLocation defaultLocaion){
    try{
      FileOutputStream fos = new FileOutputStream("defaultLocations.ser");
      ObjectOutputStream oos = new ObjectOutputStream(fos);      
      oos.writeObject(defaultLocaion);
      oos.close();
    }
    catch (FileNotFoundException ex) {
      Logger.getLogger(Storage.class.getName()).log(Level.SEVERE, null, ex);
    }
      catch (IOException ex) {
        Logger.getLogger(Storage.class.getName()).log(Level.SEVERE, null, ex);
      }
  }
    
}
