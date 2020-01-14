package com.parrot.drone.groundsdk.device.peripheral.motor;

import androidx.annotation.NonNull;

public class MotorDetail {
    private String type = "Not Available";
    private String softwareVersion = "Not Available";;
    private String hardwareVersion = "Not Available";;
    private MotorError error = MotorError.NONE;

    public MotorDetail updateDetail(@NonNull String type, @NonNull String softwareVersion, @NonNull String hardwareVersion) {
        this.type = type;
        this.softwareVersion = softwareVersion;
        this.hardwareVersion = hardwareVersion;

        return this;
    }

    public MotorDetail updateError(@NonNull MotorError error) {
        this.error = error;
        return this;
    }

    public String getType() {
        switch (type) {
            case "R":
                return "Release";
            case "D":
                return "Debug";
            case "A":
                return "Alpha";
            case "T":
                return "Test-bench";
            default:
                return type;
        }
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public String getHardwareVersion() {
        return hardwareVersion;
    }

    public MotorError getError() {
        return error;
    }
}