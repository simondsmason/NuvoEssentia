# NuvoEssentia
Hubitat Nuvo Essentia Driver

The USR-TCP232-302 device:
- Converts TCP/IP network commands to RS232 serial communication
- Default IP: 192.168.0.7, Port: 23 (configurable)
- Connects to Nuvo Essentia via DB9 serial cable
- Requires power supply and network connection

## Features
- Multi-zone control (up to 12 zones)
- Volume, source, and mute control
- Command queuing to prevent buffer overruns
- Robust connection management with automatic recovery
- Notification support for connection issues
- Source playing state tracking
- Works with USR-TCP232-302 and compatible IP-to-serial converters

## Installation
1. **Hardware Setup:**
   - Connect USR-TCP232-302 to your network
   - Connect USR device to Nuvo Essentia via RS232 cable
   - Configure USR device for TCP Server mode on port 23
   
2. **Driver Installation:**
   - Copy the driver code from `NuvoEssentia.groovy`
   - In Hubitat web interface, go to "Drivers Code"
   - Click "New Driver" 
   - Paste the code and save
   - Create new device using this driver type

## Diagnostic Tools
- `SimpleUsrTcpTestDriver.groovy`: Minimal test driver for troubleshooting USR device connectivity
- Use this driver to verify network connectivity and basic command/response functionality

## Configuration
- **Server IP**: IP address of your USR-TCP232-302 device (default: 192.168.0.7)
- **Server Port**: Usually 23 (telnet)
- **Heartbeat Interval**: 25 seconds recommended
- **Max Connection Failures**: 15 recommended
- **Command Spacing**: 1000ms recommended to prevent USR buffer overruns

## USR Device Configuration
The USR-TCP232-302 should be configured as:
- **Work Mode**: TCP Server
- **Local Port**: 23
- **Baud Rate**: 9600 (to match Nuvo Essentia)
- **Data Bits**: 8, **Stop Bits**: 1, **Parity**: None

## Troubleshooting
- Verify USR device is accessible via telnet
- Check serial cable connections to Nuvo Essentia
- Monitor driver logs for connection status
- Use "Connection Status" command for diagnostics

## Compatible Hardware
- USR-TCP232-302 (tested)
- Other TCP-to-RS232 converters with similar functionality
- Any device that can bridge TCP port 23 to RS232 serial

## Version History
- v1.16: Fixed source playing status and volume conversion
  - Reverted to working zone-based source playing logic from v1.10
  - Fixed volume conversion formula (user 1-100 to Nuvo 0-79)
  - Removed broken #SRC message parsing
  - Added proper initialization and #ALLOFF handling
- v1.15: Fixed critical queue processing bug, added queue restart logic
- v1.14: Fixed connection state detection, removed connection banning, added notifications
- v1.13: Added failure counter and notification support
- v1.12: Improved connection stability
- Earlier versions: Basic functionality and improvements

## Support
For issues related to:
- **Driver functionality**: Check Hubitat community forums
- **USR device setup**: Refer to USR-TCP232-302 manual
- **Nuvo Essentia commands**: Consult Nuvo documentation
