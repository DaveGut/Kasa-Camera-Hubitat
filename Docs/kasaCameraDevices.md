# Kasa Camera Devices / Device Types

Unless noted in this document, commands and settings for the Camera models are the same as the commands and settings within the Tapo/Kasa phone app.

### Unique Commands
     a.  On/Off.  These are the same as the Privacy Mode switch within the Camera.
     b.  Flip.  This is a Hubitat capability command NOT IMPLEMENTED.
     c.  Mute / Unmute.  Implemented as volume control in the Doorbell.
         NOT IMPLEMENTED in the fixed nor PTZ Cameral
     d.  Poll interval.  Sets the motion detect poll interval.
     e.  Refresh: Gets the latest values of the Hubitat attributes for the device.
     f.  Configure: Checks the device's IP, determines the Credential type, then 
         updates all attributes for the device (does a Refresh).

### State Variables
     a.  pollInterval.  Motion detect poll interval.
     b.  lastActivityTime.  Data used to determine if the latest poll indicates a
         New activity to report.
     c.  lastCmd. Last command sent to device.  Used in error retry and handling.
     d.  Error count.  number of consecutive LAN errors.

### Unique Atrributes
     a. commsError. If true, a comms error is encountered and the device is essentially
        not working in Hubitat.  Polling is modified to every 5 minutes until the condition
        is corrected.  NOTE: The Configure command may correct the problem is it is just
        due to a LAN IP address change.  The App also may correct the issue since every
        three hours, the app 

### Unique (not camera settings) Preferences
     a.  Credential Type.  Used in communications to grab the corrected encoded
         credentials (there are two).  User can manually change - but FIRST try
         the Configure Command.
     
    
    
