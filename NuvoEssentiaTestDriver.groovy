/*
 *  NuvoEssentiaTestDriver.groovy
 *
 *  Version History:
 *  v1.0.0  @ 2025-07-07  - Simple TCP test driver for Nuvo Essentia
 *                              - Basic connection management
 *                              - Single sendCommand method for testing
 *                              - Hex data logging
 */

import groovy.transform.Field

/**
 *  Nuvo Essentia Test Driver
 *  Simple TCP test driver for sending commands to Nuvo Essentia
 */

metadata {
    definition(name: "Nuvo Essentia Test Driver", namespace: "test", author: "AI") {
        capability "Initialize"
        command "sendCommand", [[name: "Command", type: "STRING", description: "Raw command to send"]]
        command "openConnection"
        command "closeConnection"
    }
    preferences {
        input name: "serverIp", type: "text", title: "USR IP Address", required: true
        input name: "serverPort", type: "number", title: "USR Port", defaultValue: 23, required: true
        input name: "logDebug", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() { initialize() }
def updated() { initialize() }

def initialize() {
    logD "Initializing..."
    closeConnection()
    runIn(2, "openConnection")
}

def openConnection() {
    try {
        logD "Opening connection to ${settings.serverIp}:${settings.serverPort}"
        interfaces.rawSocket.connect([eol: '\r'], settings.serverIp, settings.serverPort.toInteger())
    } catch (e) {
        logE "Error opening connection: ${e.message}"
    }
}

def closeConnection() {
    try {
        logD "Closing connection"
        interfaces.rawSocket.close()
    } catch (e) {
        logD "Error closing connection: ${e.message}"
    }
}

def sendCommand(String cmd) {
    try {
        logI "Sending command: ${cmd}"
        interfaces.rawSocket.sendMessage(cmd + "\r")
    } catch (e) {
        logE "Error sending command: ${e.message}"
    }
}

def parse(String description) {
    logI "Received (hex): ${description}"
    
    // Decode hex data if present
    String asciiMessage = ""
    boolean isHex = description?.matches("[0-9A-Fa-f]+")
    
    if (isHex) {
        try {
            asciiMessage = new String(description.decodeHex())
            logI "Received (ASCII): ${asciiMessage}"
        } catch (Exception e) {
            logE "Error decoding hex: ${e.message}"
            asciiMessage = description
        }
    } else {
        asciiMessage = description
        logI "Received (ASCII): ${asciiMessage}"
    }
}

void socketStatus(String status) {
    if (status.contains("Read timed out")) {
        // Suppress timeout messages - they're normal behavior
        return
    } else {
        logE "Socket status: ${status}"
    }
}

void logD(msg) { if (settings.logDebug) log.debug "${device.displayName}: ${msg}" }
void logE(msg) { log.error "${device.displayName}: ${msg}" } 