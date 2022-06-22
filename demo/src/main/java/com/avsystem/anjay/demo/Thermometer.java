package com.avsystem.anjay.demo;

public class Thermometer {

    private double value = 0;

    public synchronized double getValue() {
        return value;
    }

    public synchronized void setValue(double value) {
        this.value = value;
    }
}
