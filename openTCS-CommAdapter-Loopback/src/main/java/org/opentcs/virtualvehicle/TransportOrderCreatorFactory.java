package org.opentcs.virtualvehicle;

public class TransportOrderCreatorFactory {

    private final static TransportOrderCreator transportOrderCreator = new TransportOrderCreator();

    public static TransportOrderCreator getTransportOrderCreator() {
        return transportOrderCreator;
    }
}
