/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import com.google.inject.assistedinject.Assisted;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Queue;
import javax.inject.Inject;
import org.opentcs.data.ObjectPropConstants;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.model.Vehicle.Orientation;
import org.opentcs.data.order.Route.Step;
import org.opentcs.drivers.vehicle.BasicVehicleCommAdapter;
import org.opentcs.drivers.vehicle.LoadHandlingDevice;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.opentcs.drivers.vehicle.SimVehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapterPanel;
import org.opentcs.drivers.vehicle.messages.SetSpeedMultiplier;
import org.opentcs.util.CyclicTask;
import org.opentcs.util.ExplainedBoolean;
import org.opentcs.virtualvehicle.VelocityController.WayEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link VehicleCommAdapter} that does not really communicate with a physical vehicle but roughly
 * simulates one.
 *
 * @author Stefan Walter (Fraunhofer IML)
 */
public class LoopbackCommunicationAdapter
    extends BasicVehicleCommAdapter
    implements SimVehicleCommAdapter {

  /**
   * This class's Logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(LoopbackCommunicationAdapter.class);
  /**
   * The time by which to advance the velocity controller per step (in ms).
   */
  private static final int ADVANCE_TIME = 100;
  /**
   * This instance's configuration.
   */
  private final VirtualVehicleConfiguration configuration;
  /**
   * The adapter components factory.
   */
  private final LoopbackAdapterComponentsFactory componentsFactory;
  /**
   * The energy storage of this vehicle.
   */
  private EnergyStorage energyStorage;
  /**
   * The task simulating the virtual vehicle's behaviour.
   */
  private CyclicTask vehicleSimulationTask;
  /**
   * The boolean flag to check if execution of the next command is allowed.
   */
  private boolean singleStepExecutionAllowed;
  /**
   * The vehicle to this comm adapter instance.
   */
  private Vehicle vehicle;
  /**
   * Whether the loopback adapter is initialized or not.
   */
  private boolean initialized;
  
  
  private SerialCommunication serialCommunication;
  private TransportOrderCreator orderCreator;
  
  

  /**
   * Creates a new instance.
   *
   * @param componentsFactory The factory providing additional components for this adapter.
   * @param configuration This class's configuration.
   * @param vehicle The vehicle this adapter is associated with.
   * @param energyStorage The energy storage of this vehicle.
   */
  @Inject
  public LoopbackCommunicationAdapter(LoopbackAdapterComponentsFactory componentsFactory,
                                      VirtualVehicleConfiguration configuration,
                                      @Assisted Vehicle vehicle,
                                      @Assisted EnergyStorage energyStorage) {
    super(new LoopbackVehicleModel(vehicle),
          configuration.commandQueueCapacity(),
          1,
          configuration.rechargeOperation());
    this.vehicle = requireNonNull(vehicle, "vehicle");
    this.configuration = requireNonNull(configuration, "configuration");
    this.componentsFactory = requireNonNull(componentsFactory, "componentsFactory");
    this.energyStorage = requireNonNull(energyStorage, "energyStorage");    
  }

  @Override
  public void initialize() {
    if (isInitialized()) {
      LOG.debug("Already initialized.");
      return;
    }
    super.initialize();

    String initialPos = vehicle.getProperties().get(ObjectPropConstants.VEHICLE_INITIAL_POSITION);
    if (initialPos != null) {
      initVehiclePosition(initialPos);
    }

    getProcessModel().setVehicleState(Vehicle.State.IDLE);
    
    initialized = true;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void terminate() {
    if (!isInitialized()) {
      LOG.debug("Not initialized.");
      return;
    }
    SerialCommunication.disconnectAdapter(this);
    super.terminate();
    initialized = false;
  }

  @Override
  public synchronized void enable() {
    if (!isEnabled()) {
      getProcessModel().getVelocityController().addVelocityListener(getProcessModel());
      // Create task for vehicle simulation.
      //vehicleSimulationTask = new VehicleSimulationTask();
      //Thread simThread = new Thread(vehicleSimulationTask, getName() + "-simulationTask");
      //simThread.start();
      LOG.debug("vehicle communications ennabled");
    }
    super.enable();
  }

  @Override
  public synchronized void disable() {
    if (isEnabled()) {
      // Disable vehicle simulation.
      //vehicleSimulationTask.terminate();
      //vehicleSimulationTask = null;
      LOG.debug("vehicle communications dissabled");
      getProcessModel().getVelocityController().removeVelocityListener(getProcessModel());
    }
    super.disable();
  }

  @Override
  public LoopbackVehicleModel getProcessModel() {
    return (LoopbackVehicleModel) super.getProcessModel();
  }

  public Vehicle getVehicle() {
    return vehicle;
  }  
  

  @Override
  protected List<VehicleCommAdapterPanel> createAdapterPanels() {
    return Arrays.asList(componentsFactory.createPanel(this));
  }
  
  private byte getOrderId(MovementCommand cmd){
    Map<String,String> data = cmd.getStep().getDestinationPoint().getProperties();
    assert data.containsKey("id");
    return Byte.decode(data.get("id"));
  }
  
  
  private byte[] getByteMessage(MovementCommand cmd,byte[] header){
    // Todo insert command id
      byte[] message = new byte[9];
      for(int i = 0; i < header.length;i++){
        message[i] = header[i];
      }
      byte state = 0;
      int speed = cmd.getStep().getPath().getMaxVelocity();
    switch (speed) {
      case 0:
        message[5] = 0;
        break;
      case 200:
        message[5] = 1;
        break;
      case 500:
        message[5] = 2;
        break;
      case 1000:
        message[5] = 3;
        break;
      default:
        break;
    }
      Map<String,String> properties = cmd.getStep().getPath().getProperties();
      if(properties.containsKey("side")){
        if(properties.get("side").equals("right")){
          state |= 1;
        }
      }
      if(properties.containsKey("Area")){
        message[6] = Byte.valueOf(properties.get("Area"));
      }
      String operation = cmd.getOperation();
      message[7] = state;
      if(cmd.isFinalMovement()){
        message[8] = 1;
      }
      message[4] = getOrderId(cmd);
      return message;
  }
  
  @Override
  public synchronized void sendCommand(MovementCommand cmd) {    
    assert cmd != null;
    //{vehicleId,rightTurn,Operation,Speed,FinalOperation}
    
    byte[] header = {'A','G','V'};
    byte[] message = getByteMessage(cmd,header);
    
    MovementCommandMessage commandMessage= new MovementCommandMessage(cmd,false,message); 
    try{
     System.out.println(message[2] + "--" + message[3]);     
      
     SerialCommunication.sendMessage(this,commandMessage);
    } catch(Exception ex){
      LOG.debug("Unnable to send serial message!");
    }
    
    // Reset the execution flag for single-step mode.
    singleStepExecutionAllowed = false;
    // Don't do anything else - the command will be put into the sentQueue
    // automatically, where it will be picked up by the simulation task.
  }

  @Override
  public void processMessage(Object message) {
    // Process LimitSpeeed message which might pause the vehicle.
    if (message instanceof SetSpeedMultiplier) {
      SetSpeedMultiplier lsMessage = (SetSpeedMultiplier) message;
      int multiplier = lsMessage.getMultiplier();
      getProcessModel().setVehiclePaused(multiplier == 0);
    }else if(message instanceof EnergyStateMessage){
      EnergyStateMessage eSMessage = (EnergyStateMessage) message;
      getProcessModel().setVehicleEnergyLevel(eSMessage.getCurrentEnergyState());
    }else if(message instanceof VehicleStateMessage){
      VehicleStateMessage vSMessage = (VehicleStateMessage) message;
      getProcessModel().setVehicleState(vSMessage.getCurrentState());
    }else if(message instanceof MovementCommandMessage){      
      MovementCommandMessage mCMessage = (MovementCommandMessage) message;      
      MovementCommand sentCmd = getSentQueue().poll();
      MovementCommand curCmd = mCMessage.getCommand();
      if (sentCmd != null && sentCmd.equals(curCmd)) {
        // Let the vehicle manager know we've finished this command.
        LOG.debug("Movement order" + curCmd.toString() + "executed");
        getProcessModel().commandExecuted(curCmd);
        getProcessModel().setVehiclePosition(curCmd.getStep().getDestinationPoint().getName());
        if (getSentQueue().size() <= 1 && getCommandQueue().isEmpty()) {
              getProcessModel().setVehicleState(Vehicle.State.IDLE);
              SerialCommunication.clearCommunications();
              if(!"Charging-station".equals(vehicle.getCurrentPosition().getName())){
                if(orderCreator == null){
                orderCreator = TransportOrderCreatorFactory.getTransportOrderCreator();
              }
              orderCreator.createTransportOrderByLocation(vehicle, "Charging-station", "RECHARGE");
              }
              }
              
      }      
    }else if(message instanceof StatusMessage){
      StatusMessage sMessage = (StatusMessage) message;
      LOG.debug("Charge Reading: " + sMessage.getCharge());
      getProcessModel().setVehicleEnergyLevel(sMessage.getCharge());
      if(sMessage.getCharge() < 30 && vehicle.getState() != Vehicle.State.CHARGING){
        if(orderCreator == null){
          orderCreator = TransportOrderCreatorFactory.getTransportOrderCreator();
        }
        orderCreator.createTransportOrderByLocation(vehicle, "Charging-station", "RECHARGE");
        getProcessModel().setVehicleState(Vehicle.State.CHARGING);
      }
      if(sMessage.getCharge() > 60 && vehicle.getState() == Vehicle.State.CHARGING){
        getProcessModel().setVehicleState(Vehicle.State.IDLE);
      }
    }else if(message instanceof EmergencyMessage){
      LOG.debug("Emergency message detected");
    }
  }
   
  // Todo: retask the vehicle based on the location update
   public void updateLocation(int locationId){
      getProcessModel().setVehiclePosition("Point-0" + locationId);
      MovementCommand last = getSentQueue().poll();
      Point destination = last.getFinalDestination();
      if(orderCreator == null){
          orderCreator = TransportOrderCreatorFactory.getTransportOrderCreator();
      }
      orderCreator.createTransportOrderByPoint(vehicle, destination.getName());
  }

  @Override
  public synchronized void initVehiclePosition(String newPos) {
    getProcessModel().setVehiclePosition(newPos);
  }

  @Override
  public synchronized ExplainedBoolean canProcess(List<String> operations) {
    requireNonNull(operations, "operations");
    final boolean canProcess = isEnabled();
    final String reason = canProcess ? "" : "adapter not enabled";
    return new ExplainedBoolean(canProcess, reason);
  }

  /**
   * Sets the energy storage for this vehicle.
   *
   * @param energyStorage The energy storage for the vehicle to simulate
   */
  public void setEnergyStorage(EnergyStorage energyStorage) {
    this.energyStorage = energyStorage;
  }

  /**
   * Get the energy storage of this vehicle.
   *
   * @return The energy storage of this vehicle.
   */
  public EnergyStorage getEnergyStorage() {
    return energyStorage;
  }

  /**
   * Get the current absolute energy value of the simulated {@link EnergyStorage}.
   *
   * @return the current energy in Ws
   */
  public double getEnergy() {
    return energyStorage.getEnergy();
  }

  /**
   * Get the current energy level of the simulated {@link EnergyStorage}.
   *
   * @return the current energy level in %
   */
  public int getEnergyLevel() {
    return energyStorage.getEnergyLevel();
  }

  /**
   * Set the energy of vehicle.
   *
   * @param energy Energy level in percentage.
   */
  public void setEnergyLevel(int energy) {
    // Set new energy level in VehicleModel
    getProcessModel().setVehicleEnergyLevel(energy);
    // Set new energy in energyStorage
    energyStorage.setEnergyLevel(energy);
  }

  /**
   * Get the energy capacity of the simulated {@link EnergyStorage}.
   *
   * @return the energy capacity
   */
  public double getEnergyCapacity() {
    return energyStorage.getCapacity();
  }

  @Override
  protected synchronized boolean canSendNextCommand() {
    return super.canSendNextCommand()
        && (!getProcessModel().isSingleStepModeEnabled() || singleStepExecutionAllowed);
  }

  @Override
  protected synchronized void connectVehicle() {
    try{
      SerialCommunicationFactory serialCommunicationFactory;           
      serialCommunicationFactory = new SerialCommunicationFactory(this);
      this.serialCommunication = serialCommunicationFactory.getSerialCommunication();
    }catch(Exception e){
      System.out.println("Error!");
    }
  }

  @Override
  protected synchronized void disconnectVehicle() {
  }

  @Override
  protected synchronized boolean isVehicleConnected() {
    return true;
  }

  /**
   * Triggers the <code>VehicleCommunicatorTask</code> for execution in single
   * step mode.
   */
  protected synchronized void trigger() {
    singleStepExecutionAllowed = true;
  }

  /**
   * A task simulating a vehicle's behaviour.
   */
  private class VehicleSimulationTask
      extends CyclicTask {

    /**
     * The time that has passed for the velocity controller whenever
     * <em>advanceTime</em> has passed for real.
     */
    private int simAdvanceTime;
    /**
     * Energy level of the energy storage, the last time it was
     * changed by task.
     */
    private int lastEnergyLevel = energyStorage.getEnergyLevel();

    /**
     * Creates a new VehicleSimluationTask.
     */
    private VehicleSimulationTask() {
      super(0);
    }

    /**
     * Discharge the vehicle's {@code energyStorage} by the given amount over
     * {@code simAdvanceTime}.
     * Views and the {@code BasicCommunicationAdapter} are updated if needed.
     *
     * @param power power in W
     */
    private void dischargeEnergy(double power) {
      energyStorage.discharge(power, simAdvanceTime);
      int currentEnergyLevel = energyStorage.getEnergyLevel();
      // Update energy level only if changed
      if (currentEnergyLevel != lastEnergyLevel) {
        getProcessModel().setVehicleEnergyLevel(currentEnergyLevel);
        lastEnergyLevel = currentEnergyLevel;
      }
    }

    /**
     * Charge the vehicle's {@code energyStorage} by the given amount over
     * {@code simAdvanceTime}.
     * Views and the {@code BasicCommunicationAdapter} are updated if needed.
     *
     * @param power power in W
     */
    private void chargeEnergy(double power) {
      energyStorage.charge(power, simAdvanceTime);
      int currentEnergyLevel = energyStorage.getEnergyLevel();
      // Update energy level only if changed
      if (currentEnergyLevel != lastEnergyLevel) {
        getProcessModel().setVehicleEnergyLevel(currentEnergyLevel);
        lastEnergyLevel = currentEnergyLevel;
      }
    }

    @Override
    protected void runActualTask() {
      try {
        // Don't do anything if no energy left
        if (energyStorage.getEnergy() <= 0.0) {
          Thread.sleep(ADVANCE_TIME);
          getProcessModel().getVelocityController().advanceTime(simAdvanceTime);
          return;
        }
        final MovementCommand curCommand;
        synchronized (LoopbackCommunicationAdapter.this) {
          curCommand = getSentQueue().peek();
        }
        simAdvanceTime = (int) (ADVANCE_TIME * configuration.simulationTimeFactor());
        if (curCommand == null) {
          Thread.sleep(ADVANCE_TIME);
          dischargeEnergy(getProcessModel().getIdlePower());
          getProcessModel().getVelocityController().advanceTime(simAdvanceTime);
        }
        else {
          // If we were told to move somewhere, simulate the journey.
          LOG.debug("Processing MovementCommand");
          final Step curStep = curCommand.getStep();
          // Simulate the movement.
          simulateMovement(curStep);
          // Simulate processing of an operation.
          if (!curCommand.isWithoutOperation()) {
            simulateOperation(curCommand.getOperation());
          }
          LOG.debug("Processed MovementCommand");
          if (!isTerminated()) {
            // Set the vehicle's state back to IDLE, but only if there aren't 
            // any more movements to be processed.
            if (getSentQueue().size() <= 1 && getCommandQueue().isEmpty()) {
              getProcessModel().setVehicleState(Vehicle.State.IDLE);
            }
            // Update GUI.
            synchronized (LoopbackCommunicationAdapter.this) {
              MovementCommand sentCmd = getSentQueue().poll();
              // If the command queue was cleared in the meantime, the kernel
              // might be surprised to hear we executed a command we shouldn't
              // have, so we only peek() at the beginning of this method and
              // poll() here. If sentCmd is null, the queue was probably cleared
              // and we shouldn't report anything back.
              if (sentCmd != null && sentCmd.equals(curCommand)) {
                // Let the vehicle manager know we've finished this command.
                LOG.debug("Movement order" + curCommand.toString() + "executed");
                getProcessModel().commandExecuted(curCommand);
                LoopbackCommunicationAdapter.this.notify();
              }
            }
          }
        }
      }
      catch (InterruptedException iexc) {
        throw new IllegalStateException("Unexpectedly interrupted", iexc);
      }
    }

    /**
     * Simulates the vehicle's movement. If the method parameter is null,
     * then the vehicle's state is failure and some false movement
     * must be simulated. In the other case normal step
     * movement will be simulated.
     *
     * @param step A step
     * @throws InterruptedException If an exception occured while sumulating
     */
    private void simulateMovement(Step step)
        throws InterruptedException {
      long pathLength;
      int maxVelocity;
      String pointName;
      Orientation orientation;
      if (step.getPath() == null) {
        return;
      }
      else {
        orientation = step.getVehicleOrientation();
        pathLength = step.getPath().getLength();
        switch (orientation) {
          case BACKWARD:
            maxVelocity = step.getPath().getMaxReverseVelocity();
            break;
          default:
            maxVelocity = step.getPath().getMaxVelocity();
            break;
        }
        pointName = step.getDestinationPoint().getName();
      }
      getProcessModel().setVehicleState(Vehicle.State.EXECUTING);
      getProcessModel().getVelocityController().addWayEntry(new WayEntry(pathLength,
                                                                         maxVelocity,
                                                                         pointName,
                                                                         orientation));
      // Advance the velocity controller by small steps until the
      // controller has processed all way entries.
      while (getProcessModel().getVelocityController().hasWayEntries() && !isTerminated()) {
        final WayEntry wayEntry = getProcessModel().getVelocityController().getCurrentWayEntry();
        Thread.sleep(ADVANCE_TIME);
        dischargeEnergy(getProcessModel().getMovementPower());
        getProcessModel().getVelocityController().advanceTime(simAdvanceTime);
        final WayEntry nextWayEntry = getProcessModel().getVelocityController().getCurrentWayEntry();
        if (wayEntry != nextWayEntry) {
          // Let the vehicle manager know that the vehicle has reached
          // the way entry's destination point.
          getProcessModel().setVehiclePosition(wayEntry.getDestPointName());
        }
      }
    }

    /**
     * Simulates a operation.
     *
     * @param operation A operation
     * @throws InterruptedException If an exception occured while simulating
     */
    private void simulateOperation(String operation)
        throws InterruptedException {
      assert operation != null;
      if (!isTerminated()) {
        LOG.debug("Operating...");
        boolean charging = operation.equals(getRechargeOperation());
        // Get additional specifications for this operation
        final OperationSpec opSpec = getProcessModel().getOperationSpecs().get(operation);
        final int operatingTime;
        if (opSpec != null) {
          operatingTime = opSpec.getOperatingTime();
        }
        else {
          operatingTime = getProcessModel().getDefaultOperatingTime();
        }
        // Calculate amount of power that needs to be charged per second to
        // get an energy level of 100% at the end of a charging-operation.
        double chargingPower = 0.0; // Power to charge per second 
        if (charging) {
          getProcessModel().setVehicleState(Vehicle.State.CHARGING);
          double toCharge = energyStorage.getCapacity() - energyStorage.getEnergy();
          chargingPower = toCharge / (operatingTime / 1000.0);
        }
        else {
          getProcessModel().setVehicleState(Vehicle.State.EXECUTING);
        }
        for (int timePassed = 0; timePassed < operatingTime && !isTerminated();
             timePassed += simAdvanceTime) {
          Thread.sleep(ADVANCE_TIME);
          if (charging) {
            chargeEnergy(chargingPower);
          }
          else {
            dischargeEnergy(getProcessModel().getOperationPower());
          }
          getProcessModel().getVelocityController().advanceTime(simAdvanceTime);
        }
        if (opSpec != null && opSpec.changesLoadCondition()) {
          // Update load handling devices as defined by this operation
          List<LoadHandlingDevice> devices = opSpec.getLoadCondition();
          getProcessModel().setVehicleLoadHandlingDevices(devices);
        }
      }
    }
  }
}
