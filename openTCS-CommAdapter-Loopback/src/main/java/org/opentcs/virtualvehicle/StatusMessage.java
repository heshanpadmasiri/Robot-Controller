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
  private int charge;

  public StatusMessage(int charge) {
    this.charge = charge;
  }

  public int getCharge() {
    return charge;
  }
   
  
}
