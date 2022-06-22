package com.avsystem.anjay.demo;

public class Accelerometer {

    private double x = 0;
    private double y = 0;
    private double z = 0;

    public synchronized double getX() {
        return x;
    }

    public synchronized void setX(double x) {
        this.x = x;
    }

    public synchronized double getY() {
        return y;
    }

    public synchronized void setY(double y) {
        this.y = y;
    }

    public synchronized double getZ() {
        return z;
    }

    public synchronized void setZ(double z) {
        this.z = z;
    }
}


