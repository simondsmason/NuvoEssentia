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
 *    version 1.15  @  2025-07-05  -  Fixed critical queue processing bug where communication failures would cause queue to get stuck;
 *                                     Added queue restart logic when connection is re-established; Prevents missing commands issue;
 *                                     Clean stable version without ACK monitoring - simple and reliable command processing
 *    version 1.16  @  2025-07-05  -  Fixed source playing status logic; Reverted to working zone-based approach from version 1.10;
 *                                     Removed broken #SRC message parsing; Source playing status now correctly derived from zone status;
 *                                     Added proper initialization and #ALLOFF handling for source playing states
 *    version 1.17  @  2025-07-07  -  Added volume control commands for all active zones; Renamed setAllZoneSource to setAllActiveZoneSource;
 *                                     Added raiseAllActiveZonesVolume and lowerAllActiveZonesVolume commands (1-25% range);
 *                                     Commands only affect zones that are currently ON; Enhanced logging for volume changes;
 *                                     Suppressed timeout messages to reduce log noise
 *    version 1.18  @  2025-07-07  -  Fixed command timing issue; Replaced runIn() with pauseExecution() for precise millisecond delays;
 *                                     Modified raiseAllActiveZonesVolume and lowerAllActiveZonesVolume to queue commands individually;
 *                                     All commands now respect commandSpacing setting consistently; Improved queue processing reliability;
 *                                     Enhanced debug logging for command timing and queue status
 *    version 1.19  @  2025-07-08  -  Enhanced parsing logic to distinguish between power state and parameter messages;
 *                                     Added new zone attributes: Relay, Bass EQ, Treble EQ, Volume Restore; Added commands to set these parameters;
 *                                     Updated getAllZoneStatus() to send both CONSR and SETSR for complete zone information;
 *                                     Heartbeat now includes parameter polling; Added getAllZoneParameters() command;
 *                                     Improved error handling - unrecognized responses are logged but don't affect zone state;
 *                                     Added comprehensive command documentation; Removed validateConnection from UI commands
 *    version 1.20  @  2025-01-08  -  Fixed BASS/TREBLE command formatting to use 3-digit format with leading zeros;
 *                                     Removed invalid relay command and UI elements (OR command not supported by Nuvo);
 *                                     Fixed volume reset commands to use VRSTON/VRSTOFF format instead of VRST1/VRST0;
 *                                     Resolved #? syntax errors from malformed commands
 *    version 1.21  @  2025-01-08  -  Fixed BASS/TREBLE range from -10/+10 to correct -12/+12 range per Nuvo documentation;
 *                                     Updated UI descriptions to clarify positive values don't need plus sign;
 *                                     Improved command formatting for better user experience
 *    version 1.22  @  2025-01-08  -  Fixed critical bug: negative BASS/TREBLE values were missing minus sign in commands;
 *                                     Commands now properly format negative values (e.g., *Z11BASS-11 instead of *Z11BASS11)
 */

import groovy.transform.Field

@Field static final String DRIVER_NAME = "Nuvo Essentia"
@Field static final String DRIVER_VERSION = "1.22"

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
        
        command "zoneOn", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"]]
        command "zoneOff", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"]]
        command "setZoneSource", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], 
                                 [name: "Source", type: "NUMBER", description: "Source number (1-6)"]]
        command "setZoneVolume", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], 
                                 [name: "Volume", type: "NUMBER", description: "Volume level (1-100, higher is louder)"]]
        command "setZoneMute", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"],
                               [name: "State", type: "ENUM", description: "Mute state (ON/OFF)", constraints: ["ON", "OFF"]]]
        command "setAllActiveZoneSource", [[name: "Source", type: "NUMBER", description: "Source number (1-6) to set for all active zones"]]
        command "raiseAllActiveZonesVolume", [[name: "Percentage", type: "NUMBER", description: "Percentage to increase volume (1-25)", constraints: ["1..25"]]]
        command "lowerAllActiveZonesVolume", [[name: "Percentage", type: "NUMBER", description: "Percentage to decrease volume (1-25)", constraints: ["1..25"]]]
        
        command "getZoneStatus", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"]]
        command "getAllZoneStatus"
        command "getAllZoneParameters"
        command "allOff"
        
        command "setZoneBass", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], [name: "Bass", type: "NUMBER", description: "Bass EQ (-12 to +12, use minus for negative, no plus for positive)"]]
        command "setZoneTreble", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], [name: "Treble", type: "NUMBER", description: "Treble EQ (-12 to +12, use minus for negative, no plus for positive)"]]
        command "setZoneVolumeRestore", [[name: "Zone", type: "NUMBER", description: "Zone number (1-12)"], [name: "RestoreState", type: "ENUM", description: "Volume Restore (ON/OFF)", constraints: ["ON", "OFF"]]]
        
        for (int i = 1; i <= 12; i++) {
            attribute "Zone ${i} Status", "string"
            attribute "Zone ${i} Source", "number"
            attribute "Zone ${i} Volume", "string" 
            attribute "Zone ${i} Group", "number"
            attribute "Zone ${i} EQBass", "string"
            attribute "Zone ${i} EQTreble", "string"
            attribute "Zone ${i} VolumeRestore", "string"
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
    input name: "commandSpacing", type: "number", title: "Command Spacing (Milliseconds)", description: "Delay between queued commands to prevent buffer overrun (200-1000ms)", defaultValue: 500, required: true, range: "200..1000"
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
    state.consecutiveCommunicationFailures = 0
    state.commandQueue = []
    state.queueProcessing = false
    
    // Initialize source playing states
    initializeSourcePlayingStates()
    
    // Clear any existing connection
    try {
        closeConnection()
    } catch (Exception e) {
        logD "Error closing existing connection: ${e.message}"
    }
    
    // Schedule initial connection
    runIn(2, "openConnection")
}

/**
 * Open a new connection to the Nuvo Essentia system
 */
def openConnection() {
    if (state.socketConnected) {
        logD "Connection already open"
        return
    }
    
    logI "Connecting to ${settings.serverIp}:${settings.serverPort} (communication failures: ${state.consecutiveCommunicationFailures ?: 0})"
    
    try {
        closeConnection()
        runIn(1, "establishConnection")
    } catch (Exception e) {
        logE "Error in openConnection: ${e.message}"
        handleCommunicationFailure("Connection setup failed: ${e.message}")
    }
}

def establishConnection() {
    try {
        logD "closing existing connections..."
        closeConnection()
        
        def connectionString = "${settings.serverIp}:${settings.serverPort}"
        logD "Establishing telnet connection to ${connectionString}"
        
        // Use the Elan driver approach for raw socket connection
        interfaces.rawSocket.connect([eol: '\r'], settings.serverIp, settings.serverPort.toInteger())
        
        // Schedule connection validation
        logD "Scheduling connection validation in 3 seconds"
        runIn(3, "validateConnection")
        
    } catch (Exception e) {
        logE "Error establishing connection: ${e.message}"
        handleCommunicationFailure("Connection establishment failed: ${e.message}")
    }
}

def validateConnection() {
    if (state.connectionValidationPending == true) {
        logD "Connection validation already in progress"
        return
    }
    
    state.connectionValidationPending = true
    logD "Validating connection using real command/response test"
    
    try {
        sendCommandImmediate("*Z01CONSR")
        
        // Set timeout for validation
        runIn(10, "connectionValidationTimeout")
        
    } catch (Exception e) {
        logE "Error during connection validation: ${e.message}"
        state.connectionValidationPending = false
        handleCommunicationFailure("Connection validation failed: ${e.message}")
    }
}

def connectionValidationTimeout() {
    if (state.connectionValidationPending == true) {
        logW "Connection validation timed out"
        state.connectionValidationPending = false
        state.socketConnected = false
        handleCommunicationFailure("Connection validation timeout")
    }
}

/**
 * Close the current connection to the Nuvo Essentia system
 */
def closeConnection() {
    logD "closing existing connections..."
    try {
        interfaces.rawSocket.close()
    } catch (Exception e) {
        logD "Error closing socket: ${e.message}"
    }
    state.socketConnected = false
    state.connectionValidationPending = false
    unschedule("connectionValidationTimeout")
}

def handleCommunicationFailure(String reason) {
    state.consecutiveCommunicationFailures = (state.consecutiveCommunicationFailures ?: 0) + 1
    def maxFailures = settings.maxConnectionFailures ?: 15
    
    updateConnectionHealth()
    logW "Communication failure #${state.consecutiveCommunicationFailures}: ${reason}"
    
    // Stop queue processing to prevent failure loop
    if (state.queueProcessing) {
        logW "Stopping command queue processing due to communication failure"
        state.queueProcessing = false
        updateQueueStatus()
    }
    
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

/**
 * Reset the connection failure counter
 */
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

/**
 * Get current connection status information
 */
def connectionStatus() {
    def status = [
        socketConnected: state.socketConnected ?: false,
        lastResponseTime: state.lastResponseTime ? new Date(state.lastResponseTime).format('HH:mm:ss') : "Never",
        consecutiveFailures: state.consecutiveCommunicationFailures ?: 0,
        queueSize: state.commandQueue ? state.commandQueue.size() : 0,
        queueProcessing: state.queueProcessing ?: false,
        heartbeatZone: state.heartbeatZone ?: 1
    ]
    
    logI "Connection Status: ${status}"
    return status
}

/**
 * Force a reconnection attempt
 */
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
            // Also get parameters for the heartbeat zone
            def formattedZone = String.format("%02d", state.heartbeatZone)
            sendCommand("*Z${formattedZone}SETSR")
            
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
    // Suppress timeout messages - they're normal behavior
    if (status.contains("Read timed out")) {
        return
    }
    
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
        
        // Restart queue processing if there are pending commands
        if (state.commandQueue && state.commandQueue.size() > 0 && !state.queueProcessing) {
            logI "Restarting command queue processing (${state.commandQueue.size()} commands pending)"
            processCommandQueue()
        }
        
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
                
                // Restart queue processing if there are pending commands
                if (state.commandQueue && state.commandQueue.size() > 0 && !state.queueProcessing) {
                    logI "Restarting command queue processing (${state.commandQueue.size()} commands pending)"
                    processCommandQueue()
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

/**
 * Turn on Zone 1 (main zone) - equivalent to powerOn()
 */
def on() {
    logD "on()"
    powerOn()
}

/**
 * Turn off Zone 1 (main zone) - equivalent to powerOff()
 */
def off() {
    logD "off()"
    powerOff()
}

/**
 * Turn on Zone 1 (main zone)
 */
def powerOn() {
    logD "powerOn()"
    sendCommand("*Z01ON")
    sendEvent(name: "switch", value: "on")
}

/**
 * Turn off all zones
 */
def powerOff() {
    logD "powerOff()"
    sendCommand("*ALLOFF")
    sendEvent(name: "switch", value: "off")
}

/**
 * Turn off all zones
 */
def allOff() {
    logD "allOff()"
    sendCommand("*ALLOFF")
    sendEvent(name: "switch", value: "off")
}

/**
 * Turn on a specific zone (1-12)
 */
def zoneOn(def zoneNum) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    logD "Turning on zone ${formattedZone}"
    sendCommand("*Z${formattedZone}ON")
}

/**
 * Turn off a specific zone (1-12)
 */
def zoneOff(def zoneNum) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    logD "Turning off zone ${formattedZone}"
    sendCommand("*Z${formattedZone}OFF")
}

/**
 * Set the source for a specific zone (1-12)
 */
def setZoneSource(def zoneNum, def sourceNum) {
    int zone = zoneNum as Integer
    int source = sourceNum as Integer
    def formattedZone = String.format("%02d", zone)
    logD "Setting zone ${formattedZone} source to ${source}"
    sendCommand("*Z${formattedZone}SRC${source}")
}

/**
 * Set the volume for a specific zone (1-12)
 */
def setZoneVolume(def zoneNum, def volumeLevel) {
    int zone = zoneNum as Integer
    int userVolume = volumeLevel as Integer
    
    // Convert user volume (1-100) to Nuvo volume (0-79)
    // Nuvo: 0=loudest, 79=quietest
    // User: 1=quietest, 100=loudest
    int nuvoVolume = (int)((100 - userVolume) * 79 / 99)
    nuvoVolume = Math.max(0, Math.min(79, nuvoVolume))
    
    def formattedZone = String.format("%02d", zone)
    def formattedVolume = String.format("%02d", nuvoVolume)
    
    logD "Setting zone ${formattedZone} volume to ${userVolume} (Nuvo level: ${nuvoVolume})"
    sendCommand("*Z${formattedZone}VOL${formattedVolume}")
}

/**
 * Increase volume for all active zones by the specified percentage
 */
def raiseAllActiveZonesVolume(def percentage) {
    int percent = percentage as Integer
    
    // Validate percentage
    if (percent < 1 || percent > 25) {
        logE "Invalid percentage: ${percent}. Must be between 1 and 25."
        return
    }
    
    logI "Raising volume by ${percent}% for all active zones"
    
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
    
    // Queue volume commands one at a time to ensure proper spacing
    def volumeCommands = []
    activeZones.each { zoneNum ->
        def currentVolume = device.currentValue("Zone ${zoneNum} Volume")
        if (currentVolume) {
            int currentVol = currentVolume as Integer
            int newVolume = Math.min(100, currentVol + percent)
            
            logD "Zone ${zoneNum}: ${currentVol}% → ${newVolume}% (+${percent}%)"
            def formattedZone = String.format("%02d", zoneNum)
            int nuvoVolume = (int)((100 - newVolume) * 79 / 99)
            nuvoVolume = Math.max(0, Math.min(79, nuvoVolume))
            def formattedVolume = String.format("%02d", nuvoVolume)
            volumeCommands << "*Z${formattedZone}VOL${formattedVolume}"
        } else {
            logW "No volume data for zone ${zoneNum} - skipping"
        }
    }
    
    // Queue commands one at a time to ensure proper spacing
    volumeCommands.each { command ->
        sendCommand(command)
    }
    
    logI "Queued volume raise commands for ${volumeCommands.size()} active zones"
}

/**
 * Decrease volume for all active zones by the specified percentage
 */
def lowerAllActiveZonesVolume(def percentage) {
    int percent = percentage as Integer
    
    // Validate percentage
    if (percent < 1 || percent > 25) {
        logE "Invalid percentage: ${percent}. Must be between 1 and 25."
        return
    }
    
    logI "Lowering volume by ${percent}% for all active zones"
    
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
    
    // Queue volume commands one at a time to ensure proper spacing
    def volumeCommands = []
    activeZones.each { zoneNum ->
        def currentVolume = device.currentValue("Zone ${zoneNum} Volume")
        if (currentVolume) {
            int currentVol = currentVolume as Integer
            int newVolume = Math.max(1, currentVol - percent)
            
            logD "Zone ${zoneNum}: ${currentVol}% → ${newVolume}% (-${percent}%)"
            def formattedZone = String.format("%02d", zoneNum)
            int nuvoVolume = (int)((100 - newVolume) * 79 / 99)
            nuvoVolume = Math.max(0, Math.min(79, nuvoVolume))
            def formattedVolume = String.format("%02d", nuvoVolume)
            volumeCommands << "*Z${formattedZone}VOL${formattedVolume}"
        } else {
            logW "No volume data for zone ${zoneNum} - skipping"
        }
    }
    
    // Queue commands one at a time to ensure proper spacing
    volumeCommands.each { command ->
        sendCommand(command)
    }
    
    logI "Queued volume lower commands for ${volumeCommands.size()} active zones"
}

/**
 * Set mute state for a specific zone (1-12)
 */
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

/**
 * Set all active zones to the specified source
 */
def setAllActiveZoneSource(def sourceNum) {
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

def sendCommand(String command) {
    def queueTime = now()
    logD "[QUEUE] sendCommand called at ${queueTime}: ${command}"
    
    if (settings.enableCommandQueue == false) {
        logD "[QUEUE] Command queue disabled, sending immediately"
        sendCommandImmediate(command)
        return
    }
    
    if (!state.commandQueue) {
        state.commandQueue = []
        logD "[QUEUE] Initialized empty command queue"
    }
    
    state.commandQueue << [command: command, timestamp: queueTime]
    updateQueueStatus()
    logD "[QUEUE] Queued command: ${command} (Queue size: ${state.commandQueue.size()}) at ${queueTime}"
    
    if (!state.queueProcessing) {
        logD "[QUEUE] Queue not processing, starting processCommandQueue()"
        processCommandQueue()
    } else {
        logD "[QUEUE] Queue already processing, command will be processed later"
    }
}

/**
 * Clear the command queue
 */
def clearCommandQueue() {
    def queueSize = state.commandQueue ? state.commandQueue.size() : 0
    logI "Manually clearing command queue (${queueSize} commands)"
    state.commandQueue = []
    state.queueProcessing = false
    updateQueueStatus()
    logI "Command queue cleared"
}

def updateQueueStatus() {
    def queueSize = state.commandQueue ? state.commandQueue.size() : 0
    sendEvent(name: "commandQueueSize", value: queueSize)
    logD "Queue status updated: ${queueSize} commands pending"
}

def processCommandQueue() {
    // Enhanced debug: Track what's calling this function
    def caller = new Exception().getStackTrace()
    def callerInfo = "Unknown"
    if (caller.length > 1) {
        def callerMethod = caller[1].getMethodName()
        def callerClass = caller[1].getClassName()
        callerInfo = "${callerClass}.${callerMethod}"
    }
    
    def nowTime = now()
    logD "[QUEUE] processCommandQueue called at ${nowTime} by: ${callerInfo}"
    
    if (!state.commandQueue || state.commandQueue.isEmpty()) {
        state.queueProcessing = false
        updateQueueStatus()
        logD "[QUEUE] Command queue empty - stopping processing"
        return
    }
    
    // Check if already processing
    if (state.queueProcessing) {
        logD "[QUEUE] WARNING: Queue already processing! Called by: ${callerInfo}"
        return
    }
    
    state.queueProcessing = true
    updateQueueStatus()
    
    def command = state.commandQueue.remove(0)
    logD "[QUEUE] processCommandQueue processing at ${nowTime} (queue size before: ${state.commandQueue.size() + 1}, after: ${state.commandQueue.size()})"
    if (state.lastCommandSentTime) {
        def elapsed = nowTime - state.lastCommandSentTime
        logD "[QUEUE] Time since last command sent: ${elapsed} ms"
    }
    logD "[QUEUE] Processing command: ${command.command}"
    
    try {
        def sendTime = now()
        logD "[QUEUE] About to send command at ${sendTime}: ${command.command}"
        sendCommandImmediate(command.command)
        state.lastCommandSentTime = sendTime
        logD "[QUEUE] Command sent at ${sendTime}, lastCommandSentTime updated"
        
        // Schedule next command processing with precise timing
        def spacing = settings.commandSpacing ?: 500
        logD "[QUEUE] Pausing for ${spacing} ms before next command"
        pauseExecution(spacing)
        logD "[QUEUE] Pause complete, processing next command"
        processCommandQueue()
    } catch (Exception e) {
        logE "[QUEUE] Error processing command ${command.command}: ${e.message}"
        state.queueProcessing = false
        updateQueueStatus()
        handleCommunicationFailure("Command processing failed: ${e.message}")
    }
}

def sendCommandImmediate(String command) {
    try {
        logD "[QUEUE] Sending command at ${now()}: ${command}"
        interfaces.rawSocket.sendMessage(command + "\r")
    } catch (Exception e) {
        logE "Error sending command ${command}: ${e.message}"
        throw e
    }
}

def parseDeviceResponse(String asciiMessage) {
    // Normal response processing continues unchanged
    state.socketConnected = true
    sendEvent(name: "lastResponse", value: asciiMessage)
    
    // Check for #? error responses from Nuvo
    if (asciiMessage.startsWith("#?")) {
        logE "Nuvo error response: ${asciiMessage}"
        return
    }
    
    // Handle #ALLOFF response
    if (asciiMessage == "#ALLOFF") {
        logI "All zones turned off"
        for (int i = 1; i <= 12; i++) {
            sendEvent(name: "Zone ${i} Status", value: "OFF")
        }
        sendEvent(name: "switch", value: "off")
        initializeSourcePlayingStates()
        return
    }
    
    // Parse zone status responses
    if (asciiMessage.startsWith("#Z")) {
        parseZoneStatus(asciiMessage)
    }
    
    // Source playing status is handled by updateSourcePlayingStates() based on zone status
    
    // Parse tuner status
    if (asciiMessage.startsWith("#TUN")) {
        parseTunerStatus(asciiMessage)
    }
}

def parseZoneStatus(String message) {
    // Only update ON/OFF state if PWRON/PWROFF present
    def parts = message.split(",")
    def zonePart = parts[0]
    def zoneMatch = zonePart =~ /#Z(\d+)/
    if (!zoneMatch.find()) {
        logW "Unrecognized zone status message: ${message}"
        return
    }
    def zoneNum = zoneMatch.group(1) as Integer
    boolean powerStateUpdated = false
    if (zonePart.contains("PWRON")) {
        sendEvent(name: "Zone ${zoneNum} Status", value: "ON")
        powerStateUpdated = true
    } else if (zonePart.contains("PWROFF")) {
        sendEvent(name: "Zone ${zoneNum} Status", value: "OFF")
        powerStateUpdated = true
    }
    // Only update source/volume/group if present
    parts.each { part ->
        if (part.startsWith("SRC")) {
            def sourceMatch = part =~ /SRC(\d+)/
            if (sourceMatch.find()) {
                def sourceNum = sourceMatch.group(1) as Integer
                sendEvent(name: "Zone ${zoneNum} Source", value: sourceNum)
                scheduleSourceUpdate()
            }
        } else if (part.startsWith("GRP")) {
            def groupMatch = part =~ /GRP(\d+)/
            if (groupMatch.find()) {
                def groupNum = groupMatch.group(1) as Integer
                sendEvent(name: "Zone ${zoneNum} Group", value: groupNum)
            }
        } else if (part.startsWith("VOL-")) {
            def nuvoVolume = part.substring(4) as Integer
            int userVolume = 100 - (int)(nuvoVolume * 99 / 79)
            userVolume = Math.max(1, Math.min(100, userVolume))
            sendEvent(name: "Zone ${zoneNum} Volume", value: userVolume.toString())
        } else if (part.startsWith("OR")) {
            // OR status is read-only - indicates if zone DIP switches are overridden
            // No corresponding command to set this state
        } else if (part.startsWith("BASS")) {
            sendEvent(name: "Zone ${zoneNum} EQBass", value: part.replace("BASS", ""))
        } else if (part.startsWith("TREB")) {
            sendEvent(name: "Zone ${zoneNum} EQTreble", value: part.replace("TREB", ""))
        } else if (part.startsWith("VRST")) {
            def restoreState = part == "VRST1" ? "ON" : "OFF"
            sendEvent(name: "Zone ${zoneNum} VolumeRestore", value: restoreState)
        }
    }
    if (!powerStateUpdated && !parts.any { it.startsWith("SRC") || it.startsWith("GRP") || it.startsWith("VOL-") || it.startsWith("OR") || it.startsWith("BASS") || it.startsWith("TREB") || it.startsWith("VRST") }) {
        logW "Unrecognized zone status message: ${message}"
    }
    logD "Parsed zone ${zoneNum}: ${powerStateUpdated ? device.currentValue("Zone ${zoneNum} Status") : "(no power state)"}, Source ${device.currentValue("Zone ${zoneNum} Source")}, Volume ${device.currentValue("Zone ${zoneNum} Volume")}, Relay ${device.currentValue("Zone ${zoneNum} Relay")}, Bass ${device.currentValue("Zone ${zoneNum} EQBass")}, Treble ${device.currentValue("Zone ${zoneNum} EQTreble")}, VolumeRestore ${device.currentValue("Zone ${zoneNum} VolumeRestore")}" 
}

void initializeSourcePlayingStates() {
    for (int i = 1; i <= 6; i++) {
        sendEvent(name: "Source ${i} Playing", value: "FALSE")
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
            logD "Source ${i} Playing: ${newState}"
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

def parseTunerStatus(String message) {
    // Parse tuner status responses
    // This is a placeholder - implement based on actual tuner response format
    logD "Tuner status: ${message}"
}

/**
 * Get status for a specific zone (1-12)
 */
def getZoneStatus(def zoneNum) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    logI "Getting status for Zone ${zone}"
    sendCommand("*Z${formattedZone}CONSR")
}

/**
 * Get complete status (power, source, volume, group, relay, bass, treble, volume restore) for all zones
 */
def getAllZoneStatus() {
    logI "Getting status for all zones (CONSR + SETSR)"
    for (int zone = 1; zone <= 12; zone++) {
        def formattedZone = String.format("%02d", zone)
        // Send CONSR first, then SETSR for each zone
        sendCommand("*Z${formattedZone}CONSR")
        sendCommand("*Z${formattedZone}SETSR")
    }
}

/**
 * Get configuration parameters (relay, bass, treble, volume restore) for all zones. Does not include power/source/volume status.
 */
def getAllZoneParameters() {
    logI "Getting parameters for all zones (SETSR only)"
    for (int zone = 1; zone <= 12; zone++) {
        def formattedZone = String.format("%02d", zone)
        sendCommand("*Z${formattedZone}SETSR")
    }
}

/**
 * Manually trigger a heartbeat check for the current zone
 */
def forceHeartbeat() {
    logI "Manual heartbeat check initiated"
    checkConnection()
}

def updateConnectionHealth() {
    def health = "UNKNOWN"
    def failures = state.consecutiveCommunicationFailures ?: 0
    
    if (failures == 0) {
        health = "HEALTHY"
    } else if (failures < 5) {
        health = "DEGRADED"
    } else if (failures < 10) {
        health = "POOR"
    } else {
        health = "CRITICAL"
    }
    
    sendEvent(name: "connectionHealth", value: health)
    sendEvent(name: "connectionFailures", value: failures)
}

/**
 * Set the bass EQ for a specific zone (1-12)
 */
def setZoneBass(def zoneNum, def bassVal) {
    int zone = zoneNum as Integer
    int bass = bassVal as Integer
    if (bass < -12 || bass > 12) {
        logE "Invalid bass value: ${bass}. Must be between -12 and 12."
        return
    }
    def formattedZone = String.format("%02d", zone)
    def sign = bass >= 0 ? "+" : "-"
    def formattedBass = String.format("%02d", Math.abs(bass))
    logD "Setting zone ${formattedZone} bass to ${bass}"
    sendCommand("*Z${formattedZone}BASS${sign}${formattedBass}")
}

/**
 * Set the treble EQ for a specific zone (1-12)
 */
def setZoneTreble(def zoneNum, def trebVal) {
    int zone = zoneNum as Integer
    int treb = trebVal as Integer
    if (treb < -12 || treb > 12) {
        logE "Invalid treble value: ${treb}. Must be between -12 and 12."
        return
    }
    def formattedZone = String.format("%02d", zone)
    def sign = treb >= 0 ? "+" : "-"
    def formattedTreble = String.format("%02d", Math.abs(treb))
    logD "Setting zone ${formattedZone} treble to ${treb}"
    sendCommand("*Z${formattedZone}TREB${sign}${formattedTreble}")
}

/**
 * Set the volume restore state for a specific zone (1-12)
 */
def setZoneVolumeRestore(def zoneNum, def restoreState) {
    int zone = zoneNum as Integer
    def formattedZone = String.format("%02d", zone)
    def commandSuffix = restoreState.toString().toUpperCase() == "ON" ? "ON" : "OFF"
    logD "Setting zone ${formattedZone} volume restore to ${restoreState}"
    sendCommand("*Z${formattedZone}VRST${commandSuffix}")
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