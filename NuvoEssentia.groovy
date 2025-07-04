/**
 *  Driver Name: Nuvo Essentia
 *  Platform: Hubitat Elevation
 *  
 *  Copyright 2025 Simon Mason
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History
 *    version 1.00  @  2025-04-02  -  Initial release for Nuvo Essentia
 *    version 1.01  @  2025-04-03  -  Enhanced status display for serial communications
 *    version 1.02  @  2025-04-19  -  Added Source Playing states to track active sources
 *    version 1.03  @  2025-06-02  -  Fixed timing issues with source playing updates and heartbeat checks;
 *                                     Added debounced source update scheduling; Improved socket timeout handling;
 *                                     Added concurrent update prevention; Enhanced error handling in source updates
 *    version 1.04  @  2025-06-02  -  Enhanced timeout resilience; Added command queuing; Improved timing coordination;
 *                                     Extended heartbeat intervals; Better read timeout recovery
 *    version 1.05  @  2025-06-16  -  Fixed read timeout handling with intelligent threshold detection;
 *                                     Added consecutive timeout tracking; Implemented forced reconnection after timeout threshold;
 *                                     Improved connection state detection; Enhanced command response verification
 *    version 1.06  @  2025-06-16  -  Smart preference change handling to prevent unnecessary reconnections;
 *                                     Only reinitialize connection when connection settings change, not logging preferences;
 *                                     Prevents all zones turning off when toggling debug/hex logging options
 *    version 1.07  @  2025-06-16  -  Fixed remaining old timeout handling code that was still treating timeouts as normal;
 *                                     Ensured all timeout detection uses new consecutive counting logic
 *    version 1.08  @  2025-06-19  -  Changed heartbeat interval from minutes to seconds to prevent Hubitat's 30-second rawSocket timeout;
 *                                     Default heartbeat now 25 seconds; Range 15-300 seconds; Prevents timeout/reconnection cycles
 *    version 1.09  @  2025-06-19  -  Enhanced timeout resilience with 5-timeout threshold and exponential backoff;
 *                                     Added connection state tracking to prevent timing conflicts; Implemented rotating zone heartbeat;
 *                                     Minimum 20-second heartbeat interval; Improved connection verification logic
 *    version 1.10  @  2025-06-20  -  Simplified connection management with single source of truth approach;
 *                                     Eliminated race conditions and competing timers; Connection state based solely on device communication;
 *                                     Ignores Hubitat socket status noise; Self-healing connection logic
 *    version 1.11  @  2025-07-03  -  Added command queuing system to prevent buffer overruns on Nuvo unit;
 *                                     Commands are now queued and sent with proper spacing to avoid overwhelming device;
 *                                     Added queue status logging and configurable command spacing; Enhanced reliability for rapid command sequences;
 *                                     Added setAllZoneSource command to change all active zones to specified source
 *    version 1.12  @  2025-07-04  -  Improved connection stability and timeout handling; Increased response timeout from 8 to 30 minutes;
 *                                     Enhanced response detection logging; More conservative reconnection logic; Better heartbeat management;
 *                                     Fixed pathological reconnection loops; Added diagnostic logging for connection health
 *    version 1.13  @  2025-07-04  -  Fixed hardcoded timing issues that ignored user configuration settings;
 *                                     Added connection failure counter with circuit breaker protection; Respects reconnectInterval and heartbeatInterval;
 *                                     Added manual recovery commands; Prevents pathological reconnection loops requiring hub reboot;
 *                                     Added notification support for connection issues
 *    version 1.14  @  2025-07-04  -  Fixed connection state detection logic; Separated setup errors from actual communication failures;
 *                                     Uses real command/response validation instead of relying on socket status exceptions;
 *                                     Removed connection banning - focuses on communication success rather than setup completion
 */

import groovy.transform.Field

@Field static final String DRIVER_NAME = "Nuvo Essentia"
@Field static final String DRIVER_VERSION = "1.14"

metadata {
    definition(name: DRIVER_NAME, namespace: "simonmason", author: "Simon Mason") {
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "Actuator"
        
        command "closeConnection"
        command "openConnection"
        command "powerOn"
        command "powerOff"
        command "sendCommand", [[name: "Command", type: "STRING", description: "Raw command string for the Nuvo Essentia"]]
        command "connectionStatus"
        command "forceHeartbeat"
        command "clearCommandQueue"
        command "resetConnectionFailures"
        command "forceReconnect"
        command "validateConnection"
        
        command "zoneOn", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"]]
        command "zoneOff", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"]]
        command "setZoneSource", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], 
                                 [name: "Source", type: "NUMBER", description: "Source number (1-6)"]]
        command "setZoneVolume", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], 
                                 [name: "Volume", type: "NUMBER", description: "Volume level (1-100, higher is louder)"]]
        command "setZoneMute", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"],
                               [name: "State", type: "ENUM", description: "Mute state (ON/OFF)", constraints: ["ON", "OFF"]]]
        command "setAllZoneSource", [[name: "Source", type: "NUMBER", description: "Source number (1-6) to set for all active zones"]]
        
        command "getZoneStatus", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"]]
        command "getAllZoneStatus"
        command "allOff"
        
        for (int i = 1; i <= 12; i++) {
            attribute "Zone ${i} Status", "string"
            attribute "Zone ${i} Source", "number"
            attribute "Zone ${i} Volume", "string" 
            attribute "Zone ${i} Group", "number"
            attribute "Zone ${i} EQBass", "string"
            attribute "Zone ${i} EQTreble", "string"
        }
        
        for (int i = 1; i <= 6; i++) {
            attribute "Source ${i} Playing", "string"
        }
        
        attribute "tuner1Status", "string"
        attribute "tuner2Status", "string"
        attribute "tuner1Band", "string"
        attribute "tuner2Band", "string"
        attribute "tuner1Frequency", "string"
        attribute "tuner2Frequency", "string"
        attribute "tuner1Preset", "string"
        attribute "tuner2Preset", "string"
        attribute "tuner1Signal", "string"
        attribute "tuner2Signal", "string"
        attribute "lastResponse", "string"
        attribute "commandQueueSize", "number"
        attribute "connectionHealth", "string"
        attribute "connectionFailures", "number"
    }
}

preferences {
    input name: "serverIp", type: "text", title: "Server IP Address", description: "Enter the IP address of the Nuvo Essentia system", required: true
    input name: "serverPort", type: "number", title: "Server Port", description: "Enter the port number for the Nuvo Essentia system", defaultValue: 23, required: true
    input name: "baudRate", type: "enum", title: "Baud Rate", description: "Serial baud rate", options:["9600"], defaultValue: "9600", required: true
    input name: "reconnectInterval", type: "number", title: "Reconnect Interval", description: "Seconds between reconnection attempts if connection is lost", defaultValue: 30, required: true
    input name: "heartbeatInterval", type: "number", title: "Heartbeat Interval (Seconds)", description: "Seconds between status update checks (15-300, 0 = disabled)", defaultValue: 25, required: true, range: "0,15..300"
    input name: "connectionTimeout", type: "number", title: "Connection Timeout (Minutes)", description: "Minutes without response before reconnecting (10-60)", defaultValue: 30, required: true, range: "10..60"
    input name: "commandSpacing", type: "number", title: "Command Spacing (Milliseconds)", description: "Delay between queued commands to prevent buffer overrun (500-5000ms)", defaultValue: 1000, required: true, range: "500..5000"
    input name: "maxConnectionFailures", type: "number", title: "Max Connection Failures", description: "Stop reconnection attempts after this many failures (5-50)", defaultValue: 15, required: true, range: "5..50"
    input name: "enableCommandQueue", type: "bool", title: "Enable Command Queuing", description: "Queue commands to prevent buffer overruns (recommended)", defaultValue: true
    input name: "enableNotifications", type: "bool", title: "Enable Connection Notifications", description: "Send alerts when connection fails", defaultValue: false
    input name: "notificationDevice", type: "device", title: "Notification Device", description: "Select device for connection alerts (any device with notification capability)", required: false
    input name: "logDebug", type: "bool", title: "Enable debug logging", defaultValue: true
    input name: "logHexData", type: "bool", title: "Log hex data (verbose)", defaultValue: false
}

void installed() {
    logI "installed"
    initialize()
}

void updated() {
    logI "updated"
    
    boolean onlyLoggingChanged = true
    def connectionSettings = [
        "serverIp": settings.serverIp,
        "serverPort": settings.serverPort,
        "baudRate": settings.baudRate,
        "reconnectInterval": settings.reconnectInterval,
        "heartbeatInterval": settings.heartbeatInterval,
        "connectionTimeout": settings.connectionTimeout,
        "commandSpacing": settings.commandSpacing,
        "enableCommandQueue": settings.enableCommandQueue,
        "maxConnectionFailures": settings.maxConnectionFailures
    ]
    
    if (state.lastConnectionSettings) {
        connectionSettings.each { key, value ->
            if (state.lastConnectionSettings[key] != value) {
                logD "Connection setting changed: ${key} from ${state.lastConnectionSettings[key]} to ${value}"
                onlyLoggingChanged = false
            }
        }
    } else {
        onlyLoggingChanged = false
    }
    
    state.lastConnectionSettings = connectionSettings
    
    if (onlyLoggingChanged) {
        logI "Only logging preferences changed - not reinitializing connection"
        if (state.socketConnected) {
            scheduleHeartbeat()
        }
    } else {
        logI "Connection settings changed - reinitializing"
        initialize()
    }
}

void initialize() {
    logD "initializing device..."
    unschedule()
    
    // Simplified state initialization
    state.socketConnected = false
    state.lastResponseTime = null
    state.heartbeatZone = 1
    state.consecutiveFailures = 0
    state.maxConsecutiveFailures = 3
    
    // Connection failure tracking  
    state.consecutiveCommunicationFailures = 0
    state.lastSuccessfulCommand = null
    state.connectionValidationPending = false
    
    // Initialize command queue
    initializeCommandQueue()
    
    // Remove old state variables that caused race conditions
    state.remove("connectionAttempts")
    state.remove("consecutiveTimeouts")
    state.remove("connectionInProgress")
    state.remove("sourceUpdatePending")
    state.remove("lastCommandTime")
    
    initializeSourcePlayingStates()
    updateConnectionHealth()
    openConnection()
}

void initializeCommandQueue() {
    state.commandQueue = state.commandQueue ?: []
    state.queueProcessing = false
    sendEvent(name: "commandQueueSize", value: 0)
    logD "Command queue initialized"
}

void initializeSourcePlayingStates() {
    for (int i = 1; i <= 6; i++) {
        sendEvent(name: "Source ${i} Playing", value: "FALSE")
    }
}

void updateConnectionHealth() {
    def failureCount = state.consecutiveCommunicationFailures ?: 0
    def maxFailures = settings.maxConnectionFailures ?: 15
    
    sendEvent(name: "connectionFailures", value: failureCount)
    
    if (failureCount >= (maxFailures * 0.8)) {
        sendEvent(name: "connectionHealth", value: "CRITICAL")
    } else if (failureCount >= (maxFailures * 0.5)) {
        sendEvent(name: "connectionHealth", value: "WARNING") 
    } else if (failureCount > 0) {
        sendEvent(name: "connectionHealth", value: "RECOVERING")
    } else {
        sendEvent(name: "connectionHealth", value: "GOOD")
    }
}

void updateSourcePlayingStates() {
    logD "Updating Source Playing states"
    
    if (state.sourceUpdatePending == true) {
        logD "Source update already pending, skipping"
        return
    }
    
    state.sourceUpdatePending = true
    
    try {
        def activeSources = [:]
        for (int i = 1; i <= 6; i++) {
            activeSources[i] = false
        }
        
        for (int zoneNum = 1; zoneNum <= 12; zoneNum++) {
            def zoneStatus = device.currentValue("Zone ${zoneNum} Status")
            def zoneSource = device.currentValue("Zone ${zoneNum} Source")
            
            if (zoneStatus == "ON" && zoneSource != null) {
                def sourceNum = zoneSource as Integer
                if (sourceNum >= 1 && sourceNum <= 6) {
                    activeSources[sourceNum] = true
                }
            }
        }
        
        for (int i = 1; i <= 6; i++) {
            def newState = activeSources[i] ? "TRUE" : "FALSE"
            sendEvent(name: "Source ${i} Playing", value: newState)
            logI "Source ${i} Playing: ${newState}"
        }
    } catch (Exception e) {
        logE "Error updating source playing states: ${e.message}"
    } finally {
        state.sourceUpdatePending = false
    }
}

void scheduleSourceUpdate() {
    unschedule("updateSourcePlayingStates")
    runIn(5, "updateSourcePlayingStates")
}

void uninstalled() {
    logW "uninstalled"
    closeConnection()
}

void refresh() {
    logI "refreshed"
    getAllZoneStatus()
}

def getZoneStatus(def zoneNum) {
    int zone = zoneNum as Integer
    logI "Getting status for Zone ${zone}"
    def formattedZone = String.format("%02d", zone)
    sendCommand("*Z${formattedZone}CONSR")
}

def getAllZoneStatus() {
    logI "Getting status for all zones"
    for (int i = 1; i <= 12; i++) {
        def formattedZone = String.format("%02d", i)
        sendCommand("*Z${formattedZone}CONSR")
    }
}

def closeConnection() {
    try {
        logD "closing existing connections..."
        interfaces.rawSocket.close()
        state.socketConnected = false
    }
    catch (Exception e) {
        logE "error disconnecting from ${settings.serverIp}:${settings.serverPort} - ${e.message}"
    }
}

def openConnection() {
    closeConnection()
    
    try {
        logI "Connecting to ${settings.serverIp}:${settings.serverPort} (communication failures: ${state.consecutiveCommunicationFailures ?: 0})"
        interfaces.rawSocket.connect([eol: '\r'], settings.serverIp, settings.serverPort.toInteger())
        
        // Start connection validation using real command/response instead of relying on socket status
        def validationDelay = 3 // Give socket time to establish
        logD "Scheduling connection validation in ${validationDelay} seconds"
        state.connectionValidationPending = true
        runIn(validationDelay, "validateConnection")
        
    } catch (Exception e) {
        logE "Error connecting to ${settings.serverIp}:${settings.serverPort} - ${e.message}"
        handleCommunicationFailure("Connection setup failed")
    }
}

def validateConnection() {
    logD "Validating connection using real command/response test"
    state.connectionValidationPending = false
    
    try {
        // Send a real command to test if communication works
        // Use zone 1 status query as our validation command  
        def testCommand = "*Z01CONSR"
        interfaces.rawSocket.sendMessage(testCommand + "\r")
        logD "Sent validation command: ${testCommand}"
        
        // Schedule validation timeout - if no response in 10 seconds, consider failed
        runIn(10, "connectionValidationTimeout")
        
    } catch (Exception e) {
        logE "Connection validation failed - cannot send commands: ${e.message}"
        handleCommunicationFailure("Validation command send failed")
    }
}

def connectionValidationTimeout() {
    if (state.connectionValidationPending == false) {
        // We already got a response and cleared this flag
        return
    }
    
    logW "Connection validation timed out - no response to test command"
    handleCommunicationFailure("Validation timeout - no response")
}

def handleCommunicationFailure(String reason) {
    state.consecutiveCommunicationFailures = (state.consecutiveCommunicationFailures ?: 0) + 1
    def maxFailures = settings.maxConnectionFailures ?: 15
    
    updateConnectionHealth()
    logW "Communication failure #${state.consecutiveCommunicationFailures}: ${reason}"
    
    // Send notifications for communication issues
    if (settings.enableNotifications && settings.notificationDevice) {
        try {
            if (state.consecutiveCommunicationFailures == 5) {
                def message = "Nuvo Essentia: 5 consecutive communication failures - check USR device and network"
                settings.notificationDevice.deviceNotification(message)
                logI "Sent notification: ${message}"
            } else if (state.consecutiveCommunicationFailures == 10) {
                def message = "Nuvo Essentia: CRITICAL - 10 consecutive failures, manual intervention may be required"
                settings.notificationDevice.deviceNotification(message)
                logI "Sent notification: ${message}"
            } else if (state.consecutiveCommunicationFailures >= maxFailures) {
                def message = "Nuvo Essentia: MAX FAILURES REACHED (${state.consecutiveCommunicationFailures}) - Consider checking system"
                settings.notificationDevice.deviceNotification(message)
                logI "Sent notification: ${message}"
            }
        } catch (Exception e) {
            logE "Error sending notification: ${e.message}"
        }
    }
    
    // Warning thresholds
    if (state.consecutiveCommunicationFailures == 5) {
        logW "COMMUNICATION ALERT: 5 consecutive communication failures - check USR device and network"
    } else if (state.consecutiveCommunicationFailures == 10) {
        logE "COMMUNICATION CRITICAL: 10 consecutive failures - manual intervention may be required"
    } else if (state.consecutiveCommunicationFailures >= maxFailures) {
        logE "MAX COMMUNICATION FAILURES REACHED (${state.consecutiveCommunicationFailures}) - System may need attention"
    }
    
    // Always retry - no more connection banning, focus on communication success
    def retryDelay = Math.min(settings.reconnectInterval ?: 30, 300) // Cap at 5 minutes
    logI "Scheduling reconnection attempt in ${retryDelay} seconds (failure ${state.consecutiveCommunicationFailures}/${maxFailures})"
    runIn(retryDelay, "openConnection")
}

def resetConnectionFailures() {
    def previousFailures = state.consecutiveCommunicationFailures ?: 0
    logI "Manually resetting communication failure counter (was: ${previousFailures})"
    state.consecutiveCommunicationFailures = 0
    updateConnectionHealth()
    sendEvent(name: "connectionHealth", value: "RESET - READY TO RETRY")
    
    // Send notification that connection was manually reset
    if (settings.enableNotifications && settings.notificationDevice && previousFailures > 0) {
        try {
            def message = "Nuvo Essentia: Communication failure counter manually reset (was: ${previousFailures} failures)"
            settings.notificationDevice.deviceNotification(message)
            logI "Sent reset notification: ${message}"
        } catch (Exception e) {
            logE "Error sending reset notification: ${e.message}"
        }
    }
}

def forceReconnect() {
    logI "Manual reconnection attempt initiated"
    unschedule("checkConnection")
    unschedule("openConnection")
    unschedule("validateConnection")
    unschedule("connectionValidationTimeout")
    openConnection()
}

def scheduleHeartbeat() {
    unschedule("checkConnection")
    if (settings.heartbeatInterval > 0) {
        def actualInterval = Math.max(settings.heartbeatInterval, 15)
        if (actualInterval != settings.heartbeatInterval) {
            logW "Adjusting heartbeat interval from ${settings.heartbeatInterval} to ${actualInterval} seconds for safety"
        }
        logD "Scheduling heartbeat check every ${actualInterval} seconds"
        runIn(actualInterval, "checkConnection")
    }
}

def checkConnection() {
    logD "Checking connection status (communication failures: ${state.consecutiveCommunicationFailures ?: 0})"
    
    if (state.connectionValidationPending == true) {
        logD "Connection validation in progress, delaying heartbeat check"
        def heartbeatDelay = Math.max(settings.heartbeatInterval ?: 25, 15)
        runIn(heartbeatDelay, "checkConnection")
        return
    }
    
    // More conservative timeout check - 30 minutes default instead of 8 minutes
    def timeoutMinutes = settings.connectionTimeout ?: 30
    def timeoutMillis = timeoutMinutes * 60 * 1000
    
    if (state.lastResponseTime) {
        def lastResponseAge = now() - state.lastResponseTime
        logD "Last response was ${lastResponseAge/1000} seconds ago (timeout: ${timeoutMinutes} minutes)"
        
        if (lastResponseAge > timeoutMillis) {
            logW "No response from device in ${lastResponseAge/60000} minutes, reconnecting (timeout: ${timeoutMinutes} min)"
            state.socketConnected = false
            handleCommunicationFailure("Response timeout - no communication for ${lastResponseAge/60000} minutes")
            return
        }
    } else {
        logD "No lastResponseTime recorded yet"
    }
    
    if (state.socketConnected == true) {
        try {
            if (state.sourceUpdatePending == true) {
                logD "Delaying heartbeat check due to pending source update"
                def heartbeatDelay = Math.max(settings.heartbeatInterval ?: 25, 15)
                runIn(heartbeatDelay, "checkConnection")
                return
            }
            
            if (state.queueProcessing == true) {
                logD "Delaying heartbeat check due to active command queue processing"
                def heartbeatDelay = Math.max(settings.heartbeatInterval ?: 25, 15)
                runIn(heartbeatDelay, "checkConnection")
                return
            }
            
            if (state.heartbeatZone == null) {
                state.heartbeatZone = 1
            }
            
            logD "Heartbeat check: querying Zone ${state.heartbeatZone}"
            getZoneStatus(state.heartbeatZone)
            
            state.heartbeatZone = (state.heartbeatZone % 12) + 1
            
            def actualInterval = Math.max(settings.heartbeatInterval ?: 25, 15)
            runIn(actualInterval, "checkConnection")
            
        } catch (Exception e) {
            logE "Error during connection check: ${e.message}"
            state.socketConnected = false
            handleCommunicationFailure("Heartbeat command failed: ${e.message}")
        }
    } else {
        logD "Socket not connected, attempting to open connection"
        handleCommunicationFailure("Socket marked as disconnected")
    }
}

def socketStatus(String status) {
    logD "Socket status: ${status}"
    
    // Be much more conservative about socket status - don't immediately fail on status messages
    // Focus on actual communication success/failure instead
    if (status.contains("Connection reset") || status.contains("Broken pipe") || status.contains("Stream closed")) {
        logI "Socket connection error detected: ${status}"
        state.socketConnected = false
        // Don't immediately trigger failure - let the validation/heartbeat system handle it
        logD "Will rely on communication validation to confirm connection state"
    } else {
        // Log but don't act on timeouts, InterruptedException, and other transient issues
        logD "Socket status (ignored): ${status}"
    }
}

def parse(String description) {
    def currentTime = now()
    state.lastResponseTime = currentTime
    state.consecutiveTimeouts = 0
    
    // Reset communication failure counter on successful response
    if (state.consecutiveCommunicationFailures > 0) {
        logI "Communication recovered after ${state.consecutiveCommunicationFailures} failures"
        state.consecutiveCommunicationFailures = 0
        updateConnectionHealth()
        
        // Send recovery notification
        if (settings.enableNotifications && settings.notificationDevice && state.consecutiveCommunicationFailures >= 5) {
            try {
                def message = "Nuvo Essentia: Communication recovered after ${state.consecutiveCommunicationFailures} failures"
                settings.notificationDevice.deviceNotification(message)
                logI "Sent recovery notification: ${message}"
            } catch (Exception e) {
                logE "Error sending recovery notification: ${e.message}"
            }
        }
    }
    
    // Clear validation timeout since we got a response
    unschedule("connectionValidationTimeout")
    state.connectionValidationPending = false
    
    // Mark connection as working since we're getting responses
    if (!state.socketConnected) {
        state.socketConnected = true
        logI "Connection established - receiving device responses"
        
        // Start regular heartbeat monitoring
        def heartbeatDelay = Math.max(settings.heartbeatInterval ?: 25, 15)
        unschedule("checkConnection")
        runIn(heartbeatDelay, "checkConnection")
    }
    
    logD "Response received - updating lastResponseTime to ${new Date(currentTime).format('HH:mm:ss')}"
    
    String asciiMessage = ""
    boolean isHex = description?.matches("[0-9A-Fa-f]+")
    
    if (isHex) {
        try {
            asciiMessage = new String(description.decodeHex())
        } catch (Exception e) {
            asciiMessage = description
        }
    } else if (description?.startsWith("telnet:")) {
        def map = stringToMap(description)
        if (map.containsKey("msg")) {
            asciiMessage = map.msg.trim()
        } else {
            asciiMessage = description
        }
    } else {
        asciiMessage = description
    }
    
    if (settings.logDebug) {
        if (settings.logHexData) {
            log.debug "${device.displayName} :: parse hex: ${description}"
        } else {
            log.debug "${device.displayName} :: parse: ${asciiMessage}"
        }
    }
    
    if (description?.startsWith("telnet:")) {
        def map = stringToMap(description)
        if (map.containsKey("telnet")) {
            if (map.telnet == "connected") {
                logI "Telnet connected to ${settings.serverIp}:${settings.serverPort}"
                state.socketConnected = true
                state.consecutiveTimeouts = 0
                state.lastResponseTime = currentTime
                
                // Clear validation timeout and start heartbeat
                unschedule("connectionValidationTimeout") 
                state.connectionValidationPending = false
                
                // Reset failure counter on successful connection
                if (state.consecutiveCommunicationFailures > 0) {
                    logI "Connection established after ${state.consecutiveCommunicationFailures} failures"
                    state.consecutiveCommunicationFailures = 0
                    updateConnectionHealth()
                    
                    // Send recovery notification
                    if (settings.enableNotifications && settings.notificationDevice && state.consecutiveCommunicationFailures >= 5) {
                        try {
                            def message = "Nuvo Essentia: Connection recovered after ${state.consecutiveCommunicationFailures} failures"
                            settings.notificationDevice.deviceNotification(message)
                            logI "Sent recovery notification: ${message}"
                        } catch (Exception e) {
                            logE "Error sending recovery notification: ${e.message}"
                        }
                    }
                }
                
                // Start heartbeat monitoring
                def heartbeatDelay = Math.max(settings.heartbeatInterval ?: 25, 15)
                runIn(heartbeatDelay, "checkConnection")
                
            } else if (map.telnet == "disconnected") {
                logW "Telnet disconnected from ${settings.serverIp}:${settings.serverPort}"
                state.socketConnected = false
                handleCommunicationFailure("Telnet disconnected")
            } else {
                logD "Telnet status: ${map.telnet}"
            }
            return
        }
    }

    if (asciiMessage) {
        parseDeviceResponse(asciiMessage)
    }
}

private static byte[] decodeHex(String hex) {
    return hex.decodeHex()
}

def on() {
    logD "on()"
    powerOn()
}

def off() {
    logD "off()"
    powerOff()
}

def powerOn() {
    logD "powerOn()"
    sendCommand("*Z01ON")
    sendEvent(name: "switch", value: "on")
}

def powerOff() {
    logD "powerOff()"
    sendCommand("*ALLOFF")
    sendEvent(name: "switch", value: "off")
}

def allOff() {
    logD "allOff()"
    sendCommand("*ALLOFF")
    sendEvent(name: "switch", value: "off")
}

def zoneOn(def zoneNum) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    logD "Turning on zone ${formattedZone}"
    sendCommand("*Z${formattedZone}ON")
}

def zoneOff(def zoneNum) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    logD "Turning off zone ${formattedZone}"
    sendCommand("*Z${formattedZone}OFF")
}

def setZoneSource(def zoneNum, def sourceNum) {
    int zone = zoneNum as Integer
    int source = sourceNum as Integer
    def formattedZone = String.format("%02d", zone)
    logD "Setting zone ${formattedZone} source to ${source}"
    sendCommand("*Z${formattedZone}SRC${source}")
}

def setZoneVolume(def zoneNum, def volumeLevel) {
    int zone = zoneNum as Integer
    int userVolume = volumeLevel as Integer
    
    int nuvoVolume = 79 - (int)((userVolume - 1) * 79 / 99)
    nuvoVolume = Math.max(0, Math.min(79, nuvoVolume))
    
    def formattedZone = String.format("%02d", zone)
    def formattedVolume = String.format("%02d", nuvoVolume)
    
    logD "Setting zone ${formattedZone} volume to ${userVolume} (Nuvo level: ${nuvoVolume})"
    sendCommand("*Z${formattedZone}VOL${formattedVolume}")
}

def setZoneMute(def zoneNum, def muteState) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    if (muteState.toString().toUpperCase() == "ON") {
        logD "Muting zone ${formattedZone}"
        sendCommand("*Z${formattedZone}MTON")
    } else {
        logD "Unmuting zone ${formattedZone}"
        sendCommand("*Z${formattedZone}MTOFF")
    }
}

def setAllZoneSource(def sourceNum) {
    int source = sourceNum as Integer
    
    // Validate source number
    if (source < 1 || source > 6) {
        logE "Invalid source number: ${source}. Must be between 1 and 6."
        return
    }
    
    logI "Setting all active zones to source ${source}"
    
    def activeZones = []
    
    // Find all zones that are currently ON
    for (int zoneNum = 1; zoneNum <= 12; zoneNum++) {
        def zoneStatus = device.currentValue("Zone ${zoneNum} Status")
        if (zoneStatus == "ON") {
            activeZones.add(zoneNum)
        }
    }
    
    if (activeZones.isEmpty()) {
        logI "No zones are currently active - no changes made"
        return
    }
    
    logI "Found ${activeZones.size()} active zones: ${activeZones.join(', ')}"
    
    // Set source for each active zone using the command queue
    activeZones.each { zoneNum ->
        def formattedZone = String.format("%02d", zoneNum)
        logD "Queueing zone ${formattedZone} source change to ${source}"
        sendCommand("*Z${formattedZone}SRC${source}")
    }
    
    logI "Queued source ${source} commands for ${activeZones.size()} active zones"
}

def clearCommandQueue() {
    logI "Clearing command queue"
    state.commandQueue = []
    state.queueProcessing = false
    updateQueueStatus()
}

def sendCommand(String command) {
    if (settings.enableCommandQueue == false) {
        // Use immediate sending if queue is disabled
        sendCommandImmediate(command)
        return
    }
    
    // Add command to queue
    if (!state.commandQueue) {
        state.commandQueue = []
    }
    
    state.commandQueue << [
        command: command,
        timestamp: now()
    ]
    
    updateQueueStatus()
    logD "Queued command: ${command} (Queue size: ${state.commandQueue.size()})"
    
    // Start processing if not already running
    if (!state.queueProcessing) {
        processCommandQueue()
    }
}

def processCommandQueue() {
    if (!state.commandQueue || state.commandQueue.size() == 0) {
        state.queueProcessing = false
        updateQueueStatus()
        logD "Command queue processing complete - queue empty"
        return
    }
    
    state.queueProcessing = true
    def commandData = state.commandQueue.remove(0)
    updateQueueStatus()
    
    try {
        // Send the actual command
        sendCommandImmediate(commandData.command)
        logD "Sent queued command: ${commandData.command} (${state.commandQueue.size()} remaining)"
        
        // Schedule next command with proper spacing
        def spacing = Math.max(settings.commandSpacing ?: 1000, 500)  // Minimum 500ms
        runInMillis(spacing, "processCommandQueue")
        
    } catch (Exception e) {
        logE "Error processing queued command: ${e.message}"
        // Continue processing queue even if one command fails
        def spacing = Math.max(settings.commandSpacing ?: 1000, 500)
        runInMillis(spacing, "processCommandQueue")
    }
}

def updateQueueStatus() {
    def queueSize = state.commandQueue?.size() ?: 0
    sendEvent(name: "commandQueueSize", value: queueSize)
}

def sendCommandImmediate(String command) {
    try {
        if (state.socketConnected != true) {
            logW "Socket appears to be disconnected, attempting to reconnect before sending command"
            openConnection()
            pauseExecution(500)
        }
        
        String msg = formatCommand(command)
        logD "Sending command immediately: ${command}"
        interfaces.rawSocket.sendMessage(msg)
        state.lastSuccessfulCommand = now()
        pauseExecution(100)
    } catch (Exception e) {
        logE "Error sending command: ${command} - ${e.message}"
        state.socketConnected = false
        handleCommunicationFailure("Command send failed: ${e.message}")
    }
}

def connectionStatus() {
    def timeoutMinutes = settings.connectionTimeout ?: 30
    def maxFailures = settings.maxConnectionFailures ?: 15
    
    log.info "${device.displayName} connection status: ${state.socketConnected ? 'Connected' : 'Disconnected'}"
    log.info "Last response received: ${state.lastResponseTime ? new Date(state.lastResponseTime).format('yyyy-MM-dd HH:mm:ss') : 'Never'}"
    log.info "Last successful command: ${state.lastSuccessfulCommand ? new Date(state.lastSuccessfulCommand).format('yyyy-MM-dd HH:mm:ss') : 'Never'}"
    log.info "Connection timeout setting: ${timeoutMinutes} minutes"
    log.info "Consecutive communication failures: ${state.consecutiveCommunicationFailures ?: 0}/${maxFailures}"
    log.info "Connection validation pending: ${state.connectionValidationPending ?: false}"
    log.info "Current heartbeat zone: ${state.heartbeatZone ?: 1}"
    log.info "Command queue enabled: ${settings.enableCommandQueue != false}"
    log.info "Command queue size: ${state.commandQueue?.size() ?: 0}"
    log.info "Queue processing: ${state.queueProcessing ?: false}"
    log.info "Command spacing: ${settings.commandSpacing ?: 1000}ms"
    log.info "Heartbeat interval: ${settings.heartbeatInterval ?: 25} seconds"
    log.info "Reconnect interval: ${settings.reconnectInterval ?: 30} seconds"
    
    if (state.lastResponseTime) {
        def lastResponseAge = now() - state.lastResponseTime
        log.info "Time since last response: ${lastResponseAge/1000} seconds (${lastResponseAge/60000} minutes)"
        
        def timeoutMillis = timeoutMinutes * 60 * 1000
        if (lastResponseAge > timeoutMillis) {
            log.warn "Response timeout exceeded! (${lastResponseAge/60000} > ${timeoutMinutes} minutes)"
        } else {
            log.info "Response timeout status: OK (${lastResponseAge/60000} < ${timeoutMinutes} minutes)"
        }
    }
    
    // Test connection with real command
    getZoneStatus(1)
}

def forceHeartbeat() {
    logI "Force-starting heartbeat regardless of connection state"
    state.socketConnected = true
    state.connectionInProgress = false
    state.consecutiveTimeouts = 0
    
    if (state.heartbeatZone == null) {
        state.heartbeatZone = 1
    }
    
    logI "Forcing heartbeat start with zone ${state.heartbeatZone}"
    runIn(1, "checkConnection")
}

String formatCommand(String command) {
    return command + "\r"
}

def parseDeviceResponse(String asciiMessage) {
    if (asciiMessage == null || asciiMessage.trim() == "") {
        return
    }
    
    try {
        state.socketConnected = true
        sendEvent(name: "lastResponse", value: asciiMessage)
        
        if (asciiMessage.startsWith("#")) {
            if (asciiMessage == "#ALLOFF") {
                logI "All zones turned off"
                for (int i = 1; i <= 12; i++) {
                    sendEvent(name: "Zone ${i} Status", value: "OFF")
                }
                sendEvent(name: "switch", value: "off")
                initializeSourcePlayingStates()
            } else if (asciiMessage.startsWith("#Z")) {
                def zoneMatch = asciiMessage =~ /#Z(\d+)/
                if (zoneMatch.find()) {
                    def zoneNum = zoneMatch.group(1)
                    logD "Processing response for zone ${zoneNum}: ${asciiMessage}"
                    
                    def powerMatch = asciiMessage =~ /PWR(ON|OFF)/
                    if (powerMatch.find()) {
                        def powerStatus = powerMatch.group(1)
                        def displayZone = zoneNum.toInteger()
                        sendEvent(name: "Zone ${displayZone} Status", value: powerStatus)
                        
                        if (zoneNum == "01") {
                            sendEvent(name: "switch", value: powerStatus.toLowerCase())
                        }
                        
                        logI "Zone ${displayZone} power status: ${powerStatus}"
                    }
                    
                    def sourceMatch = asciiMessage =~ /SRC(\d)/
                    if (sourceMatch.find()) {
                        def sourceNum = sourceMatch.group(1).toInteger()
                        def displayZone = zoneNum.toInteger()
                        sendEvent(name: "Zone ${displayZone} Source", value: sourceNum)
                        logI "Zone ${displayZone} source: ${sourceNum}"
                        scheduleSourceUpdate()
                    }
                    
                    def groupMatch = asciiMessage =~ /GRP(\d)/
                    if (groupMatch.find()) {
                        def groupStatus = groupMatch.group(1).toInteger()
                        def displayZone = zoneNum.toInteger()
                        sendEvent(name: "Zone ${displayZone} Group", value: groupStatus)
                        logI "Zone ${displayZone} group: ${groupStatus}"
                    }
                    
                    def volMatch = asciiMessage =~ /VOL-(\d+|MT|XM)/
                    if (volMatch.find()) {
                        def volumeLevel = volMatch.group(1)
                        def displayZone = zoneNum.toInteger()
                        
                        if (volumeLevel == "MT") {
                            sendEvent(name: "Zone ${displayZone} Volume", value: "MUTE")
                            logI "Zone ${displayZone} volume: MUTE"
                        } else if (volumeLevel == "XM") {
                            sendEvent(name: "Zone ${displayZone} Volume", value: "EXT_MUTE")
                            logI "Zone ${displayZone} volume: EXTERNAL MUTE"
                        } else {
                            try {
                                int nuvoVolume = volumeLevel.toInteger() 
                                int userVolume = 1 + (int)((79 - nuvoVolume) * 99 / 79)
                                
                                sendEvent(name: "Zone ${displayZone} Volume", value: userVolume)
                                logI "Zone ${displayZone} volume: ${userVolume} (Nuvo value: ${volumeLevel})"
                            } catch (Exception e) {
                                sendEvent(name: "Zone ${displayZone} Volume", value: volumeLevel)
                                logE "Error converting volume: ${e.message}"
                            }
                        }
                    }
                    
                    return
                }
            } else if (asciiMessage == "#ALLOFF") {
                logI "All zones turned off"
                for (int i = 1; i <= 12; i++) {
                    sendEvent(name: "Zone ${i} Status", value: "OFF")
                }
                sendEvent(name: "switch", value: "off")
                initializeSourcePlayingStates()
            } else if (asciiMessage == "#EXTMON") {
                logI "External mute activated"
            } else if (asciiMessage == "#EXTMOFF") {
                logI "External mute deactivated"
            } else if (asciiMessage == "#ALLMON") {
                logI "All zones muted"
            } else if (asciiMessage == "#ALLMOFF") {
                logI "All zones unmuted"
            } else if (asciiMessage.startsWith("#IRSET")) {
                logI "IR carrier frequency settings updated"
            } else if (asciiMessage == "#ALLV+") {
                logI "All zones volume ramping up"
            } else if (asciiMessage == "#ALLV-") {
                logI "All zones volume ramping down"
            } else if (asciiMessage == "#ALLHLD") {
                logI "All zones volume ramp halted"
            } else {
                logD "Unhandled system response: $asciiMessage"
            }
        } else if (asciiMessage.startsWith("?")) {
            logW "Error response received: $asciiMessage"
        }
        
    } catch (Exception e) {
        logE "Error parsing response: ${e.message}"
    }
}

void logD(String msg) {
    if (settings.logDebug) log.debug "${device.displayName} :: ${msg}"
}

void logE(String msg) {
    log.error "${device.displayName} :: ${msg}"
}

void logI(String msg) {
    log.info "${device.displayName} :: ${msg}"
}

void logW(String msg) {
    log.warn "${device.displayName} :: ${msg}"
}