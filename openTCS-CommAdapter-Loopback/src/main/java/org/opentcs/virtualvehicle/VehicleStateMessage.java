/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import org.opentcs.data.model.Vehicle;

/**
 *
 * @author Heshan
 */
public class VehicleStateMessage {
  Vehicle.State currentState;

  public VehicleStateMessage(Vehicle.State currentState) {
    this.currentState = currentState;
  }

  public Vehicle.State getCurrentState() {
    return currentState;
  }  
  
}
