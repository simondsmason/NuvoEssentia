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

## New in v1.22

- **Fixed Critical Bug:** Negative BASS/TREBLE values were missing minus sign in commands
- **Corrected Command Formatting:** Commands now properly format negative values (e.g., `*Z11BASS-11` instead of `*Z11BASS11`)
- **Resolved #? Errors:** Eliminated syntax errors when setting negative BASS/TREBLE values

## Previous Features (v1.21)

- **Fixed BASS/TREBLE Range:** Updated range from -10/+10 to correct -12/+12 range per Nuvo documentation
- **Improved UI Experience:** Updated descriptions to clarify that positive values don't need plus sign (just enter `12` not `+12`)
- **Enhanced Command Formatting:** Driver automatically handles positive/negative sign formatting for optimal user experience

## Previous Fixes (v1.20)

- **Fixed Command Syntax:** Resolved `#?` syntax errors by fixing command formatting to match Nuvo documentation exactly:
  - **BASS/TREBLE commands** now use proper 3-digit format with leading zeros (e.g., `*Z01BASS+05` instead of `*Z01BASS+5`)
  - **Volume Restore commands** now use correct format (`*Z01VRSTON`/`*Z01VRSTOFF` instead of `*Z01VRST1`/`*Z01VRST0`)
  - **Removed invalid relay command** (`*ZxxORx`) which is not supported by Nuvo protocol
- **Enhanced Reliability:** Eliminated syntax errors that were causing communication failures

## Previous Features (v1.19)

- **Robust Parsing:** The driver distinguishes between power state messages and parameter/status messages (Bass, Treble, Volume Restore). Only messages containing `PWRON` or `PWROFF` update the ON/OFF state. Other messages update their respective attributes without affecting power state.
- **New Zone Attributes:**
  - `Zone X EQBass` (Bass EQ, -12 to +12)
  - `Zone X EQTreble` (Treble EQ, -12 to +12)
  - `Zone X VolumeRestore` (ON/OFF)
- **New Commands:**
  - `setZoneBass(zone, bass)` — Set bass EQ (-12 to +12) for a zone
  - `setZoneTreble(zone, treble)` — Set treble EQ (-12 to +12) for a zone
  - `setZoneVolumeRestore(zone, restoreState)` — Enable/disable volume restore for a zone
- **Unrecognized Responses:**
  - Any unrecognized zone status message is logged for debugging but does not change the zone's ON/OFF state.

## Usage Examples

- **Set Zone 5 Bass to +6:**
  - `setZoneBass(5, 6)` (no plus sign needed for positive values)
- **Set Zone 2 Treble to -4:**
  - `setZoneTreble(2, -4)` (minus sign required for negative values)
- **Set Zone 3 Bass to maximum +12:**
  - `setZoneBass(3, 12)`
- **Enable Volume Restore for Zone 7:**
  - `setZoneVolumeRestore(7, "ON")`

## Notes
- These commands can be used in Rule Machine, custom apps, or via the device page in Hubitat.
- The driver will not default a zone to OFF if a response is not recognized; it will simply log the message.

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
- **v1.22 (Current)**: Fixed critical minus sign bug in BASS/TREBLE commands
  - Fixed bug where negative BASS/TREBLE values were missing minus sign in commands
  - Commands now properly format negative values (e.g., *Z11BASS-11 instead of *Z11BASS11)
  - Resolved #? syntax errors when setting negative BASS/TREBLE values
- **v1.21**: Fixed BASS/TREBLE range and improved UI experience
  - Updated BASS/TREBLE range from -10/+10 to correct -12/+12 range per Nuvo documentation
  - Improved UI descriptions to clarify positive values don't need plus sign
  - Enhanced command formatting for better user experience
- **v1.20**: Fixed command syntax errors, removed invalid relay command
  - Fixed BASS/TREBLE command formatting to use 3-digit format with leading zeros
  - Fixed volume restore commands to use VRSTON/VRSTOFF format
  - Removed invalid relay command that was causing #? syntax errors
  - Enhanced reliability by eliminating malformed commands
- **v1.19**: Enhanced parsing logic, new zone parameters, improved status polling
  - Enhanced parsing logic to distinguish between power state and parameter messages
  - Added new zone attributes: EQBass, EQTreble, VolumeRestore
  - Added new commands: setZoneBass, setZoneTreble, setZoneVolumeRestore
  - Improved status polling and comprehensive documentation
- v1.18: Various improvements and bug fixes
- v1.17: Connection stability enhancements
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
