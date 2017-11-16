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
  private int voltage;

  public StatusMessage(int voltage) {
    this.voltage = voltage;
  }

  public int getVoltage() {
    return voltage;
  }
  
  
  
}
