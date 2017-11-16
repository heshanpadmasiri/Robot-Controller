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
public class EnergyStateMessage {
  private final int currentEnergyState;

  public EnergyStateMessage(int currentEnergyState) {
    this.currentEnergyState = currentEnergyState;
  }

  public int getCurrentEnergyState() {
    return currentEnergyState;
  }  
}
