/**
 *  Simple USR TCP Test Driver
 *  Allows sending raw commands to a USR-TCP232 device and logs all responses.
 */

metadata {
    definition(name: "Simple USR TCP Test Driver", namespace: "test", author: "AI") {
        capability "Initialize"
        command "sendRawCommand", [[name: "Command", type: "STRING", description: "Raw command to send"]]
        command "openConnection"
        command "closeConnection"
        command "sendTestCommand"
    }
    preferences {
        input name: "serverIp", type: "text", title: "USR IP Address", required: true
        input name: "serverPort", type: "number", title: "USR Port", defaultValue: 23, required: true
        input name: "logDebug", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "testCommand", type: "text", title: "Test Command (for Send Test Command button)", required: false
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

def sendRawCommand(String cmd) {
    try {
        logD "Sending: ${cmd}"
        interfaces.rawSocket.sendMessage(cmd + "\r")
    } catch (e) {
        logE "Error sending command: ${e.message}"
    }
}

def sendTestCommand() {
    if (settings.testCommand) {
        logD "Sending test command from preferences: ${settings.testCommand}"
        sendRawCommand(settings.testCommand)
    } else {
        logD "No test command set in preferences."
    }
}

def parse(String description) {
    logD "Received: ${description}"
}

def socketStatus(String status) {
    logD "Socket status: ${status}"
}

void logD(msg) { if (settings.logDebug) log.debug "${device.displayName}: ${msg}" }
void logE(msg) { log.error "${device.displayName}: ${msg}" } 