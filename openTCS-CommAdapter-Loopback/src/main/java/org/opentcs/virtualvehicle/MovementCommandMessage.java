/*
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import org.opentcs.drivers.vehicle.MovementCommand;

/**
 *
 * @author Heshan
 */
public class MovementCommandMessage {
  private MovementCommand command;
  private boolean iscompleted;
  byte[] serialMessage;

  public MovementCommandMessage(MovementCommand command, boolean iscompleted, byte[] serialMessage) {
    this.command = command;
    this.iscompleted = iscompleted;
    this.serialMessage = serialMessage;
  }

  public MovementCommand getCommand() {
    return command;
  }

  public boolean isIscompleted() {
    return iscompleted;
  }

  public byte[] getSerialMessage() {
    return serialMessage;
  }

  public void setIscompleted(boolean iscompleted) {
    this.iscompleted = iscompleted;
  }
  
  
}
