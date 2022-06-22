package com.avsystem.anjay.demo;

public class Led {

    private boolean state = false;

    public boolean getState() {
        return state;
    }
    public void setState(boolean state) {
        this.state = state;
        System.out.println("@@@@@@ SET LED TO " + state);
    }

}
