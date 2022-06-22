/*
 * Copyright 2020-2021 AVSystem <avsystem@avsystem.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.avsystem.anjay.demo;

import com.avsystem.anjay.*;
import com.avsystem.anjay.AnjayAccessControl.AccessMask;
import com.avsystem.anjay.AnjayFirmwareUpdate.InitialState;
import com.avsystem.anjay.AnjayFirmwareUpdate.Result;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DemoClient implements Runnable {
    private Anjay.Configuration config;
    private AnjaySecurityObject securityObject;
    private AnjayServerObject serverObject;
    private AnjayAttrStorage attrStorage;
    private AnjayAccessControl accessControl;
    private DemoCommands demoCommands;
    public DemoArgs args;
    private DigitalOutput output;

    class FirmwareUpdateHandlers implements AnjayFirmwareUpdateHandlers {
        private File file;
        private FileOutputStream stream;
        private AnjayFirmwareUpdate firmwareUpdate;

        public void streamOpen(Optional<String> packageUri, Optional<byte[]> etag)
                throws Exception {
            this.file = new File("downloaded_firmware");
            this.stream = new FileOutputStream(file, false);
        }

        public void streamWrite(byte[] data) throws AnjayFirmwareUpdateException {
            try {
                this.stream.write(data);
            } catch (Exception e) {
                throw new AnjayFirmwareUpdateException(
                        AnjayFirmwareUpdateException.NOT_ENOUGH_SPACE, null);
            }
        }

        public void streamFinish() throws AnjayFirmwareUpdateException {
            try {
                this.stream.close();
            } catch (Exception e) {
                throw new AnjayFirmwareUpdateException(
                        AnjayFirmwareUpdateException.INTEGRITY_FAILURE, null);
            }
        }

        public void reset() {
            try {
                this.stream.close();
            } catch (Exception e) {
                Logger.getAnonymousLogger().log(Level.SEVERE, "failed to close stream: ", e);
            }
        }

        public String getName() {
            return "name";
        }

        public String getVersion() {
            return "1.0";
        }

        public void performUpgrade() throws AnjayFirmwareUpdateException {
            try {
                this.firmwareUpdate.setResult(Result.SUCCESS);
            } catch (Exception e) {
                throw new AnjayFirmwareUpdateException(
                        AnjayFirmwareUpdateException.INTEGRITY_FAILURE, null);
            }
        }

        public AnjaySecurityConfig getSecurityConfig(String uri) {
            if (DemoClient.this.args.fwCertFile == null) {
                return null;
            }

            AnjaySecurityInfoCert info = new AnjaySecurityInfoCert();
            info.serverCertValidation = true;
            info.trustedCerts = new LinkedList<>();

            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                try (InputStream is = new FileInputStream(DemoClient.this.args.fwCertFile)) {
                    for (Certificate cert : factory.generateCertificates(is)) {
                        info.trustedCerts.add(
                                new AnjaySecurityInfoCert.Certificate(cert.getEncoded()));
                    }
                }
            } catch (CertificateException | IOException e) {
                throw new RuntimeException(e);
            }
            return new AnjaySecurityConfig(info);
        }

        public void setFirmwareUpdateObject(AnjayFirmwareUpdate firmwareUpdate) {
            this.firmwareUpdate = firmwareUpdate;
        }
    }

    public DemoClient(DemoArgs commandlineArgs) {
        this.args = commandlineArgs;
        this.config = new Anjay.Configuration();
        this.config.endpointName = this.args.endpointName;
        this.config.inBufferSize = 4000;
        this.config.outBufferSize = 4000;
        this.config.msgCacheSize = 4000;
        this.config.disableLegacyServerInitiatedBootstrap = this.args.bootstrapClientInitiatedOnly;
        this.config.udpTxParams =
                Optional.of(
                        new Anjay.CoapUdpTxParams(
                                this.args.ackTimeout,
                                this.args.ackRandomFactor,
                                this.args.maxRetransmit,
                                this.args.nstart));
        this.config.msgCacheSize = this.args.cacheSize;
    }

    private Optional<byte[]> readFile(String path) throws IOException {
        return Optional.of(Files.readAllBytes(Paths.get(path)));
    }

    public void configureDefaultServer() throws Exception {
        this.securityObject.purge();
        this.serverObject.purge();
        AnjaySecurityObject.Instance securityInstance = new AnjaySecurityObject.Instance();
        securityInstance.ssid = 1;
        securityInstance.serverUri = Optional.of(this.args.serverUri);

        if (this.args.securityMode == AnjaySecurityObject.SecurityMode.PSK) {
            securityInstance.publicCertOrPskIdentity =
                    Optional.ofNullable(this.args.identityOrCert);
            securityInstance.privateCertOrPskKey = Optional.ofNullable(this.args.pskOrPrivKey);
        } else if (this.args.securityMode == AnjaySecurityObject.SecurityMode.CERTIFICATE) {
            if (this.args.clientCertFile != null) {
                securityInstance.publicCertOrPskIdentity = readFile(this.args.clientCertFile);
                securityInstance.privateCertOrPskKey = readFile(this.args.keyFile);
            } else {
                securityInstance.publicCertOrPskIdentity =
                        Optional.ofNullable(this.args.identityOrCert);
                securityInstance.privateCertOrPskKey = Optional.ofNullable(this.args.pskOrPrivKey);
            }
        } else if (this.args.securityMode != AnjaySecurityObject.SecurityMode.NOSEC) {
            throw new RuntimeException("Unsupported security mode " + this.args.securityMode);
        }
        securityInstance.securityMode = this.args.securityMode;

        if (this.args.bootstrapClientInitiatedOnly) {
            this.args.bootstrap = true;
        }
        securityInstance.bootstrapServer = this.args.bootstrap;
        if (securityInstance.bootstrapServer) {
            securityInstance.clientHoldoffS = Optional.of(this.args.bootstrapHoldoff);
            securityInstance.bootstrapTimeoutS = Optional.of(this.args.bootstrapTimeout);
        }
        this.securityObject.addInstance(securityInstance);

        if (!securityInstance.bootstrapServer) {
            AnjayServerObject.Instance serverInstance = new AnjayServerObject.Instance();
            serverInstance.ssid = 1;
            serverInstance.lifetime = this.args.lifetime;
            serverInstance.binding = "U";
            int iid = this.serverObject.addInstance(serverInstance);
            this.accessControl.setAcl(
                    1,
                    iid,
                    serverInstance.ssid,
                    AccessMask.READ | AccessMask.WRITE | AccessMask.EXECUTE);
        }
    }

    private void maybeRestoreState() throws Exception {
        if (this.args.dmPersistenceFile != null) {
            try (FileInputStream restoreStream = new FileInputStream(this.args.dmPersistenceFile)) {
                this.securityObject.restore(restoreStream);
                this.serverObject.restore(restoreStream);
                this.accessControl.restore(restoreStream);
            } catch (Exception e) {
                Logger.getAnonymousLogger()
                        .log(
                                Level.SEVERE,
                                "failed to restore some of Security, Server and Access Control objects: ",
                                e);
                throw e;
            }
        } else {
            this.configureDefaultServer();
        }

        if (this.args.attributeStoragePersistenceFile != null) {
            try (FileInputStream restoreStream =
                    new FileInputStream(this.args.attributeStoragePersistenceFile)) {
                this.attrStorage.restore(restoreStream);
            } catch (Exception e) {
                Logger.getAnonymousLogger()
                        .log(Level.SEVERE, "failed to restore Attribute storage: ", e);
                throw e;
            }
        }
    }

    private void maybePersistState() {
        if (this.args.dmPersistenceFile != null
                && (this.securityObject.isModified()
                        || this.serverObject.isModified()
                        || this.accessControl.isModified())) {
            try (FileOutputStream persistStream =
                    new FileOutputStream(this.args.dmPersistenceFile)) {
                this.securityObject.persist(persistStream);
                this.serverObject.persist(persistStream);
                this.accessControl.persist(persistStream);
            } catch (Exception e) {
                Logger.getAnonymousLogger()
                        .log(
                                Level.SEVERE,
                                "failed to persist some of Security, Server and Access Control objects: ",
                                e);
            }
        }
        if (this.args.attributeStoragePersistenceFile != null && this.attrStorage.isModified()) {
            try (FileOutputStream persistStream =
                    new FileOutputStream(this.args.attributeStoragePersistenceFile)) {
                this.attrStorage.persist(persistStream);
            } catch (Exception e) {
                Logger.getAnonymousLogger()
                        .log(Level.SEVERE, "failed to persist Attribute storage: ", e);
            }
        }
    }

    @Override
    public void run() {
        try (Anjay anjay = new Anjay(this.config);
                AnjayEventLoop eventLoop = new AnjayEventLoop(anjay, 100L)) {
            Thread stdinThread =
                    new Thread(
                            () -> {
                                try (BufferedReader reader =
                                        new BufferedReader(new InputStreamReader(System.in))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        demoCommands.schedule(eventLoop, line);
                                    }
                                } catch (IOException e) {
                                    Logger.getAnonymousLogger()
                                            .log(Level.WARNING, "failed to read from stdin: ", e);
                                } finally {
                                    eventLoop.interrupt();
                                }
                            });
            stdinThread.start();

            this.securityObject = AnjaySecurityObject.install(anjay);
            this.serverObject = AnjayServerObject.install(anjay);
            this.attrStorage = AnjayAttrStorage.install(anjay);
            this.accessControl = AnjayAccessControl.install(anjay);
            this.demoCommands = new DemoCommands(anjay, this, this.attrStorage, this.accessControl);

            InitialState initialState = new InitialState();
            FirmwareUpdateHandlers fwuHandlers = new FirmwareUpdateHandlers();
            AnjayFirmwareUpdate firmwareUpdate =
                    AnjayFirmwareUpdate.install(anjay, fwuHandlers, initialState);
            fwuHandlers.setFirmwareUpdateObject(firmwareUpdate);

            try {
                this.maybeRestoreState();
            } catch (Exception e) {
                this.configureDefaultServer();

                this.attrStorage.purge();
                AnjayAttributes.ObjectInstanceAttrs attrs =
                        new AnjayAttributes.ObjectInstanceAttrs();
                attrs.maxPeriod = 5;
            }

            if (this.args.accessEntries != null) {
                for (DemoArgs.AccessEntry accessEntry : this.args.accessEntries) {
                    this.accessControl.setAcl(
                            accessEntry.oid,
                            accessEntry.iid,
                            accessEntry.ssid,
                            accessEntry.accessMask);
                }
            }

            Logger.getAnonymousLogger().log(Level.INFO, "*** DEMO STARTUP FINISHED ***");

            output = DigitalOutput.install(anjay);
            output.instanceAdd(
                    0,
                    "LED Control",
                    new Supplier<>() {
                        @Override
                        public Boolean get() {
                            return RegisteredObjects.led.getState();
                        }
                    });

            AnjayBasicIpsoSensor thermometer = AnjayBasicIpsoSensor.install(anjay, 3303);
            thermometer.instanceAdd(
                    0,
                    "Celcius",
                    Optional.of(-200.0),
                    Optional.of(200.0),
                    new Supplier<>() {
                        @Override
                        public Double get() {
                            return RegisteredObjects.thermometer.getValue();
                        }
                    });

            Anjay3dIpsoSensor accelerometerObject = Anjay3dIpsoSensor.install(anjay, 3313);


            accelerometerObject.instanceAdd(
                    0,
                    "m/s2",
                    Optional.of(-100.0),
                    Optional.of(100.0),
                    new Supplier<Anjay3dIpsoSensor.Coordinates>() {
                        @Override
                        public Anjay3dIpsoSensor.Coordinates get() {
                            return new Anjay3dIpsoSensor.Coordinates(
                                    RegisteredObjects.accelerometer.getX(),
                                    RegisteredObjects.accelerometer.getY(),
                                    RegisteredObjects.accelerometer.getZ());
                        }
                    });

            eventLoop.scheduleTask(
                    new Consumer<>() {
                        @Override
                        public void accept(AnjayEventLoop eventLoop) {
                            maybePersistState();
                            thermometer.update(0);
                            accelerometerObject.update(0);
                            eventLoop.scheduleTask(this, Instant.now().plusMillis(2000L));
                        }
                    },
                    Instant.now().plusMillis(2000L));

            try {
                eventLoop.run();
            } catch (IOException e) {
                stdinThread.interrupt();
            } finally {
                stdinThread.join();
            }
        } catch (Throwable t) {
            System.out.println("Unhandled exception happened during main loop: " + t);
            t.printStackTrace();
        }
    }
}
