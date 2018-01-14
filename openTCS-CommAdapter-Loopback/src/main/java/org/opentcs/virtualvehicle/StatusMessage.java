/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

/**
 *
 * @author Heshan
 */
public class StatusMessage {
  private byte charge;
  private byte state;

  public StatusMessage(byte charge, byte state) {
    this.charge = charge;
    this.state = state;
  }

  public byte getCharge() {
    return charge;
  }

  public byte getState() {
    return state;
  }
  
  
   
  
}
