# Master Bathroom Stereo Parsing Bug - November 24, 2025

**Date:** November 24, 2025  
**Time:** 4:22 PM EST  
**Zone:** Zone 02 (Master Bathroom)  
**Issue:** Nuvo driver incorrectly parsed `PWROFF` response as "ON", causing stereo to turn back on immediately after being turned off

## Problem Summary

The master bathroom stereo (Zone 02) was turned off when the lights were turned off, but immediately turned back on due to a parsing bug in the Nuvo Essentia driver. The driver received a correct `#Z02PWROFF` response from the Nuvo unit but incorrectly parsed it as "ON" instead of "OFF". This caused the "Power Detected" rule to see the zone as ON and turn the Power Virtual switch back on, which then triggered the zone to turn on again.

## Timeline of Events

### Initial Turn-Off Sequence (Correct Behavior)
1. **4:22:09.402 PM** - Master Bathroom Main Light: button 2 pushed
2. **4:22:09.869 PM** - Group: Master Bathroom Lights switch was turned off
3. **4:22:10.112 PM** - Master Bathroom Occupied (Virtual) was turned off
4. **4:22:10.286 PM** - A/V: Z02 - Master Bathroom - Power (Virtual) on HubitatC8Pro-2 was turned off
5. **4:22:10.605 PM** - Master Bathroom Vanity Mirror was turned on (user action)
6. **4:22:10.731 PM** - Group: Master Bathroom Lights switch was turned on (user action)
7. **4:22:10.873 PM** - Master Bathroom Occupied (Virtual) was turned on
8. **4:22:10.903 PM** - A/V: Z02 - Master Bathroom - Power (Virtual) was turned on (correct - lights came back on)

### The Bug Sequence (Incorrect Behavior)
9. **4:22:11.622 PM** - A/V: Z02 - Master Bathroom - Power (Virtual) was turned off
10. **4:22:11.688 PM** - A/V: Reset Switches - Event: Power Virtual switch off
11. **4:22:11.744 PM** - Nuvo: Turning off zone 02 (`*Z02OFF` command sent)
12. **4:22:11.959 PM** - Nuvo: Response received - `parse: #Z02PWROFF,SRC3,GRP1,VOL-47` ✅ **CORRECT RESPONSE**
13. **4:22:12.055 PM** - Nuvo: **Parsed zone 2: ON** ❌ **BUG! Should be OFF**
14. **4:22:12.270 PM** - A/V: Z02 - Master Bathroom - Power (Virtual) is off
15. **4:22:12.274 PM** - A/V: Z02 - Master Bathroom - Power (Virtual) was turned on ❌ **Triggered by incorrect ON status**
16. **4:22:12.368 PM** - Nuvo: Turning on zone 02 ❌ **Triggered by Power Virtual switch turning on**
17. **4:22:12.491 PM** - Zone 2 Status changed to ON (from "Power Detected" rule)

## Root Cause Analysis

### The Parsing Bug

The Nuvo Essentia driver received a correct `#Z02PWROFF` response from the Nuvo unit, indicating that Zone 02 was successfully turned off. However, the `parseZoneStatus()` function in `NuvoEssentia.groovy` incorrectly parsed this response as "ON" instead of "OFF".

**Expected Behavior:**
- Response: `#Z02PWROFF,SRC3,GRP1,VOL-47`
- `zonePart` = `#Z02PWROFF`
- `zonePart.contains("PWROFF")` should return `true`
- Status should be set to "OFF"

**Actual Behavior:**
- Response: `#Z02PWROFF,SRC3,GRP1,VOL-47` ✅ Correct
- Parsed as: "Parsed zone 2: ON" ❌ Incorrect

### Code Location

The parsing logic is in `NuvoEssentia.groovy` at lines 1167-1221:

```groovy
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
    // ... rest of parsing logic ...
    logD "Parsed zone ${zoneNum}: ${powerStateUpdated ? device.currentValue("Zone ${zoneNum} Status") : "(no power state)"}, ..."
}
```

### Why the Bug Occurred

The parsing logic appears correct at first glance, but the log shows it parsed as "ON" when it should have been "OFF". Possible causes:

1. **Race Condition:** Another response (`#Z02PWRON`) may have arrived and been processed between the `sendEvent` and the log statement, overwriting the OFF status
2. **String Matching Issue:** The `contains("PWROFF")` check may be failing for an unknown reason
3. **Event Timing:** The `sendEvent` may not have completed before `device.currentValue()` is called in the log statement
4. **Multiple Responses:** Multiple responses may be queued and processed out of order

### Evidence from Logs

**Nuvo Response Sequence:**
```
4:22:11.959 PM - Response received: #Z02PWROFF,SRC3,GRP1,VOL-47
4:22:12.055 PM - Parsed zone 2: ON, Source 3, Volume 42  ❌ BUG
4:22:12.248 PM - Response received: #Z02PWRON,SRC3,GRP1,VOL-47  (from zoneOn command)
4:22:12.255 PM - Parsed zone 2: ON, Source 3, Volume 42  ✅ Correct (zone was turned on)
```

**Hubitat Event Log:**
```
4:22:11.837 PM - command-zoneOff(2) called
4:22:11.967 PM - Zone 2 Status changed to OFF (Power Detected rule triggered)
4:22:12.411 PM - command-zoneOn(2) called  ❌ Should not have happened
4:22:12.491 PM - Zone 2 Status changed to ON (Power Detected rule triggered)
```

## Impact

1. **User Experience:** The stereo turned back on immediately after being turned off, causing confusion
2. **Automation Failure:** The light-based automation failed to turn off the stereo as intended
3. **Energy Waste:** The stereo remained on when it should have been off
4. **Reliability:** This is the first occurrence of this bug, suggesting it may be a rare race condition or timing issue

## Recommendations

### Immediate Actions

1. **Add Debug Logging:** Add detailed logging to `parseZoneStatus()` to track:
   - The exact `zonePart` value being checked
   - The result of `zonePart.contains("PWROFF")` and `zonePart.contains("PWRON")`
   - The value being set by `sendEvent`
   - The value read by `device.currentValue()` in the log statement

2. **Add Response Validation:** Before parsing, log the raw response to ensure it matches what's expected

3. **Add State Verification:** After `sendEvent`, verify the state was actually updated before logging

### Code Improvements

1. **Explicit State Tracking:** Store the parsed state in a local variable before calling `sendEvent`, then log that variable instead of reading `device.currentValue()`

2. **Response Queue Management:** Ensure responses are processed in order and that multiple responses don't overwrite each other

3. **Error Handling:** Add error handling if `sendEvent` fails or if the state doesn't match what was set

### Example Fix

```groovy
def parseZoneStatus(String message) {
    def parts = message.split(",")
    def zonePart = parts[0]
    def zoneMatch = zonePart =~ /#Z(\d+)/
    if (!zoneMatch.find()) {
        logW "Unrecognized zone status message: ${message}"
        return
    }
    def zoneNum = zoneMatch.group(1) as Integer
    boolean powerStateUpdated = false
    String newPowerState = null  // Track state explicitly
    
    logD "Parsing zone status: zonePart='${zonePart}', contains PWRON=${zonePart.contains('PWRON')}, contains PWROFF=${zonePart.contains('PWROFF')}"
    
    if (zonePart.contains("PWRON")) {
        newPowerState = "ON"
        sendEvent(name: "Zone ${zoneNum} Status", value: newPowerState)
        powerStateUpdated = true
    } else if (zonePart.contains("PWROFF")) {
        newPowerState = "OFF"
        sendEvent(name: "Zone ${zoneNum} Status", value: newPowerState)
        powerStateUpdated = true
    }
    
    // ... rest of parsing logic ...
    
    // Log using tracked state instead of reading device value
    logD "Parsed zone ${zoneNum}: ${powerStateUpdated ? newPowerState : "(no power state)"}, Source ${device.currentValue("Zone ${zoneNum} Source")}, ..."
}
```

## Testing

To reproduce and test the fix:

1. Turn on Zone 02 (Master Bathroom)
2. Turn off Zone 02 via command
3. Verify the response is `#Z02PWROFF`
4. Verify the parsed status is "OFF"
5. Verify the Power Virtual switch does NOT turn back on
6. Check logs for any parsing inconsistencies

## Related Files

- `NuvoEssentia.groovy` - Lines 1167-1221 (parseZoneStatus function)
- Hubitat Event Log - Zone 2 Status changes
- New Relic Logs - Nuvo driver parsing messages

## Status

**Status:** Open - Bug identified, fix pending  
**Priority:** High - Causes automation failures  
**First Occurrence:** November 24, 2025, 4:22 PM EST  
**Frequency:** First known occurrence (rare/intermittent issue suspected)

---

**Generated:** November 24, 2025  
**Analysis Tool:** New Relic Logs via nr_logs.py  
**Hub:** HubitatC8Pro-2






