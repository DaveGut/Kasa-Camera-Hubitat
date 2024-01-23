# Tapo Integration Installation

## Install app and driver code
	a.	Use Hubitat Package Manager
 		1.	Search for Tag Lights and Switches or name tapo
   		2.	Select driver for your device based on description in HPM.
   	b.	Manual Installation
		1.	Use links in list at bottom of page to get to raw code.
  		2.	Copy and paste this data to a new app or new driver page
		3.	Install drivers for device type.
    
## Install device into the Kasa/Tapo phone application (iPhone / Android)
	a.	Use manufacturer's instruction.
 	b.	After installation, CREATE a STATIC IP (DHCP Reservation) for the device on 
   		your WiFi Router.

## Install devices via the Hubitat kasaCamera Application
	a.	Open a Log Page to view messages/errors during the installation process.
 	b.	Create/Open the App in Hubitat using "add user app"
 	c.	If you use non-standard IP segments, update by selecting Modify LAN
  		Configuraiton and update the segment.
	d.	Select Enter/Update tpLink Credentials.  These are used to generate the
 		credentials (version of username/password) for LAN control.
   	e.	Select "Scan LAN for Kasa cameras and add".  It will take around 30 seconds to obtain the 
    		device data.
	f.	From the Add Kasa Cameras to Hubitat page, select the devices to install 
 		from the drop-down list.  Then select Next.
   	g.	Exit the app (press done on the Tapo Device Installation page.
	h.	Go to the Hubitat Devices Page and insure all devices installed and working.
 		Note:  The log page has logs of success and failures.

## Link to driver and app code.

  Application: https://raw.githubusercontent.com/DaveGut/kasaCam_Hubitat/main/App/Kasa_Cameras.groovy

  ### Cameras

  Basic (fixed) Camera: https://raw.githubusercontent.com/DaveGut/kasaCam_Hubitat/main/Drivers/kasaCameraFixed.groovy
  
  Pan-Tilt-Zoom Camera: https://raw.githubusercontent.com/DaveGut/kasaCam_Hubitat/main/Drivers/kasaCameraPTZ.groovy
  
  Doorbell: https://raw.githubusercontent.com/DaveGut/kasaCam_Hubitat/main/Drivers/kasaDoorbell.groovy
