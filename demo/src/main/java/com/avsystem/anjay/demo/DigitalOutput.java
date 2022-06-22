package com.avsystem.anjay.demo;

import com.avsystem.anjay.*;

import java.security.InvalidParameterException;
import java.util.*;
import java.util.function.Supplier;

public class DigitalOutput implements AnjayObject {

    private final class Resource {
        public static final int DIGITAL_OUTPUT_STATE = 5550;
        public static final int DIGITAL_OUTPUT_POLARITY = 5551;
        public static final int APPLICATION_TYPE = 5750;
    }

    private static final Integer OUTPUT_OID = 3201;
    private final Anjay anjay;
    private final Map<Integer, DigitalOutput.Instance> instances = new TreeMap<>();

    public DigitalOutput(Anjay anjay) {
        this.anjay = anjay;
    }

    private class Instance {
        private final int iid;

        private String applicationType;
        private String applicationTypeBackup;

        private boolean polarity = false;
        private boolean output = false;

        private final Supplier<Boolean> readOutput;

        public Instance(int iid, String application_type, Supplier<Boolean> readOutput) {
            this.iid = iid;
            this.applicationType = application_type;
            this.readOutput = readOutput;
        }

        public boolean getPolarity() {
            return polarity;
        }

        public void setPolarity(boolean polarity){
            this.polarity = polarity;
        }

        public int getIid() {
            return iid;
        }

        public String getApplicationType() {
            return applicationType;
        }

        public void setApplicationType(String applicationType) {
            this.applicationType = applicationType;
        }

        private void update(){
            System.out.println("@@@@@ SUPPLIER SAYS THE LED IS " + readOutput.get());
            setOutput(readOutput.get());
        }
        public boolean getOutput() {
            update();
            return output;
        }

        public void setOutput(boolean output) {
            this.output = output;
            anjay.notifyChanged(OUTPUT_OID, iid, Resource.DIGITAL_OUTPUT_STATE);

        }
    }

    @Override
    public int oid() {
        return OUTPUT_OID;
    }

    @Override
    public SortedSet<Integer> instances() {
        return new TreeSet<>(instances.keySet());
    }

    public synchronized void instanceAdd(int iid, String applicationType, Supplier<Boolean> readOutput) {
        if (instances.containsKey(iid)) {
            throw new InvalidParameterException("IID already in use");
        } else {
            instances.put(iid, new DigitalOutput.Instance(iid, applicationType, readOutput));
            anjay.notifyInstancesChanged(OUTPUT_OID);
        }
    }

    @Override
    public synchronized void instanceRemove(int iid) {
        if (instances.remove(iid) != null) {
            throw new IllegalArgumentException("Invalid IID");
        } else {
            anjay.notifyInstancesChanged(OUTPUT_OID);
        }
    }

    public synchronized void update(int iid, boolean output) {
        DigitalOutput.Instance inst = instances.get(iid);
        if (inst == null) {
            throw new IllegalArgumentException("Invalid IID");
        } else {
            inst.setOutput(output);
        }
    }

    @Override
    public SortedSet<ResourceDef> resources(int iid) {
        TreeSet<ResourceDef> resourceDefs = new TreeSet<>();
        resourceDefs.add(new ResourceDef(DigitalOutput.Resource.APPLICATION_TYPE, ResourceKind.RW, true));
        resourceDefs.add(new ResourceDef(DigitalOutput.Resource.DIGITAL_OUTPUT_POLARITY, ResourceKind.RW, true));
        resourceDefs.add(new ResourceDef(DigitalOutput.Resource.DIGITAL_OUTPUT_STATE, ResourceKind.RW, true));
        return resourceDefs;
    }

    @Override
    public void resourceRead(int iid, int rid, AnjayOutputContext context) throws Exception {
        switch (rid) {
            case DigitalOutput.Resource.APPLICATION_TYPE:
                context.retString(this.instances.get(iid).getApplicationType());
                break;
            case DigitalOutput.Resource.DIGITAL_OUTPUT_POLARITY:
                context.retBoolean(this.instances.get(iid).getPolarity());
                break;
            case DigitalOutput.Resource.DIGITAL_OUTPUT_STATE:
                context.retBoolean(this.instances.get(iid).getOutput());
                break;
            default:
                throw new IllegalArgumentException("Unsupported resource " + rid);
        }
    }

    @Override
    public void resourceWrite(int iid, int rid, AnjayInputContext context) throws Exception {
        switch (rid) {
            case DigitalOutput.Resource.APPLICATION_TYPE:
                instances.get(iid).setApplicationType(context.getString());
                break;
            case DigitalOutput.Resource.DIGITAL_OUTPUT_POLARITY:
                instances.get(iid).setPolarity(context.getBoolean());
                break;
            case DigitalOutput.Resource.DIGITAL_OUTPUT_STATE:
                //instances.get(iid).setOutput(context.getBoolean());
                MqttMessageSender.sendLedDownlink(context.getBoolean());
                break;
            default:
                throw new IllegalArgumentException("Unsupported resource " + rid);
        }
    }

    @Override
    public void resourceReset(int iid, int rid) throws Exception {
        AnjayObject.super.resourceReset(iid, rid);
    }

    @Override
    public SortedSet<Integer> resourceInstances(int iid, int rid) throws Exception {
        return AnjayObject.super.resourceInstances(iid, rid);
    }

    @Override
    public void transactionBegin() throws Exception {
        for (DigitalOutput.Instance instance : instances.values()) {
            instance.applicationTypeBackup = instance.applicationType;
        }
    }

    @Override
    public void transactionValidate() throws Exception {    }

    @Override
    public void transactionCommit() throws Exception {
        for (DigitalOutput.Instance instance : instances.values()) {
            instance.applicationTypeBackup = null;
        }
    }

    @Override
    public void transactionRollback() throws Exception {
        for (DigitalOutput.Instance instance : instances.values()) {
            instance.applicationType = instance.applicationTypeBackup;
        }
    }

    public static DigitalOutput install(Anjay anjay) {
        DigitalOutput newOutput = new DigitalOutput(anjay);
        anjay.registerObject(newOutput);
        return newOutput;
    }
}
