/*	Kasa Camera Integration
	Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf

Objective:	Control of the three types of Kasa Cameras with drivers installed by this
	application.
a.	Fixed Camera
b.	Pan, Tilt, and Zoom Camera
c.	Kasa Doorbell
Limitations:	The following limitations applly (at this time)
a.	No video display / control functions
b.	No user voice or sound download functions.

Version 2.3.7a.
	NOTE:  Namespace Change.  At top of code for app and each driver.
===================================================================================================*/
//	=====	NAMESPACE	============
def nameSpace() { return "davegut" }
//	================================
import groovy.json.JsonSlurper


definition(
	name: "Kasa Cameras",
	namespace: nameSpace(),
	author: "Dave Gutheinz",
	description: "Application to install Kasa Cameras and Doorbells.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	installOnOpen: true,
	singleInstance: true,
	documentationLink: "",
	importUrl: ""
)

preferences {
	page(name: "initInstance")
	page(name: "startPage")
	page(name: "addDevicesPage")
	page(name: "removeDevicesPage")
	page(name: "enterCredentialsPage")
	page(name: "processCredentials")
}

def installed() { 
	updated()
}

def updated() {
	logInfo("updated: Updating device configurations and (if cloud enabled) Kasa Token")
	app.updateSetting("logEnable", [type:"bool", value: false])
	app?.updateSetting("appSetup", [type:"bool", value: false])
	app?.updateSetting("CheckConnectEnable", [type:"bool", value: true])
	state.remove("addedDevices")
	state.remove("failedAdds")
	scheduleChecks()
}

def scheduleChecks() {
	unschedule()
	runEvery3Hours(findDevices, [data: true])
}

def uninstalled() {
    getAllChildDevices().each { 
        deleteChildDevice(it.deviceNetworkId)
    }
}

def initInstance() {
	logDebug("initInstance")
	unschedule()
	runIn(1800, scheduleChecks)
	app.updateSetting("infoLog", true)
	app.updateSetting("logEnable", true)
	
	if (!state.devices) { state.devices = [:] }
	if (!lanSegment) {
		def hub = location.hub
		def hubIpArray = hub.localIP.split('\\.')
		def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
		app?.updateSetting("lanSegment", [type:"string", value: segments])
	}
	if (!ports) {
		app?.updateSetting("ports", [type:"string", value: "9999"])
	}
	if (!hostLimits) {
		app?.updateSetting("hostLimits", [type:"string", value: "1, 254"])
	}
	startPage()
}

def startPage() {
	logInfo("starting Kasa Integration")
	if (selectedRemoveDevices) { removeDevices() }
	if (selectedAddDevices) { addDevices() }
	try {
		state.segArray = lanSegment.split('\\,')
		state.portArray = ports.split('\\,')
		def rangeArray = hostLimits.split('\\,')
		def array0 = rangeArray[0].toInteger()
		def array1 = array0 + 2
		if (rangeArray.size() > 1) {
			array1 = rangeArray[1].toInteger()
		}
		state.hostArray = [array0, array1]
	} catch (e) {
		logWarn("startPage: Invalid entry for Lan Segements, Host Array Range, or Ports. Resetting to default!")
		def hub = location.hubs[0]
		def hubIpArray = hub.localIP.split('\\.')
		def segments = [hubIpArray[0],hubIpArray[1],hubIpArray[2]].join(".")
		app?.updateSetting("lanSegment", [type:"string", value: segments])
		app?.updateSetting("ports", [type:"string", value: "9999"])
		app?.updateSetting("hostLimits", [type:"string", value: "1, 254"])
	}

	return dynamicPage(name:"startPage",
					   title:"<b>Kasa Camera Integration</b>",
					   uninstall: true,
					   install: true) {
		section() {
			paragraph "<b>LAN Configuration</b>:  [LanSegments: ${state.segArray},  " +
				"Ports ${state.portArray},  hostRange: ${state.hostArray}]"
			input "appSetup", "bool",
				title: "<b>Modify LAN Configuration</b>",
				submitOnChange: true,
				defaultalue: false
			if (appSetup) {
				input "lanSegment", "string",
					title: "<b>Lan Segments</b> (ex: 192.168.50, 192,168.01)",
					submitOnChange: true
				input "hostLimits", "string",
					title: "<b>Host Address Range</b> (ex: 5, 100)",
					submitOnChange: true
				input "ports", "string",
					title: "<b>Ports for Port Forwarding</b> (ex: 9999, 8000)",
					submitOnChange: true
			}
			
			def credText = "<b>Credentials are set</b>"
			if (!credentials || !altCredentials) {
				credText = "<b>Credentials require attention</b>\n"
				credText += "Kasa Camera device require use of LOCAL only credentials "
				credText += "that are encoded versions of your Kasa username and password.  Enter "
				credText += "these credentials below to install Kasa Matter and Kasa Hub devices."
			}
			paragraph credText
			href "enterCredentialsPage",
				title: "<b>Enter/Update tpLink Credentials</b>",
				description: credText
			paragraph " "
			if (credentials || altCredentials) {
				href "addDevicesPage",
					title: "<b>Scan LAN for Kasa cameras and add</b>",
					description: "Primary Method to discover and add devices."
				paragraph " "
				href "removeDevicesPage",
					title: "<b>Remove Kasa Cameras</b>",
					description: "Select to remove selected Kasa cameras from Hubitat."
				paragraph " "
				input "logEnable", "bool",
					title: "<b>Debug logging</b>",
					submitOnChange: true,
					defaultValue: true
			}
		}
	}
}

//	Create credentials for drivers.
def enterCredentialsPage() {
	logInfo("enterCredentialsPage")
	return dynamicPage (name: "enterCredentialsPage", 
    					title: "Enter TP-Link Credentials",
						nextPage: startPage,
                        install: false) {
		section() {
			String currState = "<b>Current Credentials</b> = "
			if (state.userCredentials) {
				currState += "${state.userCredentials}"
			} else {
				currState += "NOT SET"
			}
			paragraph currState
			input ("userName", "email",
            		title: "TP-Link Email Address", 
                    required: false,
                    submitOnChange: true)
			input ("userPassword", "password",
            		title: "TP-Link Account Password",
                    required: false,
                    submitOnChange: true)
			if (userName && userPassword && userName != null && userPassword != null) {
				logDebug("enterCredentialsPage: [username: ${userName}, pwdLen: ${userPassword.length()}]")
				href "processCredentials", title: "Create Encoded Credentials",
					description: "You may have to press this twice."
			}
		}
	}
}
private processCredentials() {
	Map logData = [userName: userName, userPassword: userPassword]
	String creds = "${userName}:${userPassword}".bytes.encodeBase64().toString()
	app?.updateSetting("credentials", [type: "password", value: creds])
	logData << [credentials: credentials]
	String encPw = userPassword.bytes.encodeBase64().toString()
	String altCreds = "${userName}:${encPw}".bytes.encodeBase64().toString().toUpperCase()
	app?.updateSetting("altCredentials", [type: "password", value: altCreds])
	logData << [altCredentials: altCredentials]
	logInfo(logData)
	return startPage()
}

def addDevicesPage() {
	logInfo([method: "addDevicesPage", findDevs: findDevs])
	def waitFor = findDevices(false)
	def devices = state.devices
	Map uninstalledDevices = [:]
	List installedDevices = []
	Map requiredDrivers = [:]
	devices.each {
		def isChild = getChildDevice(it.value.dni)
		if (!isChild) {
			uninstalledDevices["${it.value.dni}"] = "${it.value.alias}, ${it.value.type}"
			requiredDrivers["${it.value.type}"] = "${it.value.type}"
		} else {
			installedDevices << it.value.alias
		}
	}
	uninstalledDevices.sort()
	def reqDrivers = []
	requiredDrivers.each {
		reqDrivers << it.key
	}

	return dynamicPage(name:"addDevicesPage",
					   title: "Add Kasa Cameras to Hubitat",
					   nextPage: startPage,
					   install: false) {
		def text = "<b>Missing Devices?</b> "
		text += "Try turning on/off the device via the phone app and then rediscover."
		text += "\n\n<b>Installed Devices:</b> ${installedDevices}"
	 	section() {
			paragraph text
			input ("selectedAddDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to add (${uninstalledDevices.size() ?: 0} available).\n\t" +
				   "Total Devices: ${devices.size()}",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: uninstalledDevices)
		}
	}
}

def addDevices() {
	Map logData = [method: "addDevices", selectedDevices: selectedAddDevices]
	def hub = location.hubs[0]
	Map addedDevices = [:]
	Map failedAdds = [:]
	selectedAddDevices.each { dni ->
		def isChild = getChildDevice(dni)
		if (!isChild) {
			def device = state.devices.find { it.value.dni == dni }
			def deviceData = [:]
			deviceData["deviceIp"] = device.value.ip
			deviceData["devicePort"] = device.value.port
			deviceData["deviceId"] = device.value.deviceId
			deviceData["model"] = device.value.model
			try {
				addChildDevice(
					nameSpace(),
					device.value.type,
					device.value.dni,
					[
						"label": device.value.alias.replaceAll("[\u201C\u201D]", "\"").replaceAll("[\u2018\u2019]", "'").replaceAll("[^\\p{ASCII}]", ""),
						"data" : deviceData
					]
				)
				addedDevices << ["${device.value.alias}": [dni: dni]]
				logData << [added: addedDevices]
			} catch (error) {
				failedAdds << ["${device.value.alias}": 
							   [dni: dni, driver: device.value.type, error: error]]
				logData << [failedToAdd: failedAdds]
			}
		}
		pauseExecution(3000)
	}
	logInfo(logData)
	app?.removeSetting("selectedAddDevices")
}

def findDevices(check = false) {
	Map logData = [method: "findDevices", check: check]
	def action = "getLanData"
	def interval = 10
	if (check == true) { 
		action = "getCheckData"
		interval = 5		
	} else {
		state.devices = [:]
	}
	logData << [action: action, interval: interval]
	def delay = 1000 * (interval + 5)
	def start = state.hostArray.min().toInteger()
	def finish = state.hostArray.max().toInteger() + 1
	logData << [hostArray: state.hostArray, portArray: state.portArray, pollSegment: state.segArray]
	if (!check) {
		logInfo(logData)
	} else {
		logDebug(logData)
	}
	state.portArray.each {
		def port = it.trim()
		List deviceIPs = []
		state.segArray.each {
			def pollSegment = it.trim()
			logDebug("findDevices: Searching for LAN deivces on IP Segment = ${pollSegment}, port = ${port}")
            for(int i = start; i < finish; i++) {
				deviceIPs.add("${pollSegment}.${i.toString()}")
			}
			sendLanCmd(deviceIPs.join(','), port, """{"system":{"get_sysinfo":{}}}""", action, interval)
			pauseExecution(delay)
		}
	}
	return "findCommandsSent"
}

def getLanData(response) {
	if (response instanceof Map) {
		def resp = parseLanMessage(response.description)
		def waitFor = checkDevice(resp)
		waitFor = parseLanData(resp)
	} else {
		response.each {
			def resp = parseLanMessage(it.description)
			def waitFor = checkDevice(resp)
			waitFor = parseLanData(resp)
		}
	}
}
def parseLanData(resp) {
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def ip = convertHexToIP(resp.ip)
		def port = convertHexToInt(resp.port)
		def clearResp = inputXOR(resp.payload)
		def cmdResp
		try {
			cmdResp = new JsonSlurper().parseText(clearResp).system.get_sysinfo
		} catch (err) {
			if (clearResp.contains("child_num")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("child_num")-2) + "}}}"
			} else if (clearResp.contains("children")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("children")-2) + "}}}"
			} else if (clearResp.contains("preferred")) {
				clearResp = clearResp.substring(0,clearResp.indexOf("preferred")-2) + "}}}"
			} else {
				logWarn("parseLanData: [error: msg too long, data: ${clearResp}]")
				return [error: "error", reason: "message to long"]
			}
			cmdResp = new JsonSlurper().parseText(clearResp).system.get_sysinfo
		}
		if (cmdResp.system) { cmdResp = cmdResp.system }
		if (cmdResp.type == "IOT.IPCAMERA") {
			def waitFor = parseDeviceData(cmdResp, ip, port)
		}
	} else {
		logWarn([method: "parseLanData", error: "return not LAN_TYPE_UDPCLIENT", resp: resp])
	}
}
def parseDeviceData(cmdResp, ip, port) {
	def kasaType = cmdResp.type
	if (kasaType == null) { kasaType = cmdResp.mic_type }
	def alias = cmdResp.alias
	Map logData = [method: "parseDeviceData", alias: "<b>${alias}</b>"]
	def devices = state.devices
	def type = "kasaCameraFixed"
	if (cmdResp.dev_name.contains("Spot")) {
		type = "kasaCameraPtz"
	} else if (cmdResp.dev_name.contains("Doorbell")) {
		type = "kasaDoorbell"
	}
	def device = [
		alias: alias, kasaType: kasaType,
		ip: ip, port: port, dni: cmdResp.mic_mac,
		model: cmdResp.model, type: type,
		deviceId: cmdResp.deviceId]
	devices[cmdResp.mic_mac] = device
	logData << [status: "createdDevice", devData: device]
	logInfo(logData)
	return
}

def checkDevice(resp) {
	if (resp.type == "LAN_TYPE_UDPCLIENT") {
		def child = getChildDevice(resp.mac)
		def ip = convertHexToIP(resp.ip)
		if (child && child.getDataValue("deviceIp") != ip) {
			child.updateAttr("commsError", "false")
			childData << [commsError: "false"]
			def port = convertHexToInt(resp.port)
			Map logData = [child: child, commsError: "false", ip: ip, port: port]
			child.updateDataValue("deviceIp", ip)
			child.updateDataValue("devicePort", port.toString())
			logInfo(logData)
		}
	}
	return
}

def getCheckData(response) {
	if (response instanceof Map) {
		def waitFor = checkDevice(parseLanMessage(response.description))
	} else {
		response.each {
			def waitFor = checkDevice(parseLanMessage(it.description))
		}
	}
}

def removeDevicesPage() {
	logInfo("removeDevicesPage")
	def devices = state.devices
	def installedDevices = [:]
	devices.each {
		def installed = false
		def isChild = getChildDevice(it.value.dni)
		if (isChild) {
			installedDevices["${it.value.dni}"] = "${it.value.alias}, type = ${it.value.type}, dni = ${it.value.dni}"
		}
	}
	logDebug("removeDevicesPage: newDevices = ${newDevices}")
	return dynamicPage(name:"removedDevicesPage",
					   title:"<b>Remove Kasa Devices from Hubitat</b>",
					   nextPage: startPage,
					   install: false) {
		section("Select Devices to Remove from Hubitat") {
			input ("selectedRemoveDevices", "enum",
				   required: false,
				   multiple: true,
				   title: "Devices to remove (${installedDevices.size() ?: 0} available)",
				   description: "Use the dropdown to select devices.  Then select 'Done'.",
				   options: installedDevices)
		}
	}
}

def removeDevices() {
	Map logData = [method: "removeDevices"]
	List deletedDevices = []
	def devices = state.devices
	selectedRemoveDevices.each { dni ->
		def device = state.devices.find { it.value.dni == dni }
		def isChild = getChildDevice(dni)
		if (isChild) {
			try {
				deleteChildDevice(dni)
				logData << ["${device.value.alias}": "deleted"]
			} catch (error) {
				logData << ["${device.value.alias}": "deleteFailed"]
			}
		}
	}
	app?.removeSetting("selectedRemoveDevices")
	logInfo(logData)
}

//	Command from children on comms failure
def checkConnect() {
	Map logData = [method: "checkConnect"]
	if (checkConnectEnable) {
		app?.updateSetting("checkConnectEnable", [type:"bool", value: false])
		runIn(900, enableCheckConnect)
		logData << [status: findDevices(true)]
	} else {
		logData << [status: "noCheck", reason: "Updated within 15 minutes"]
	}
	return logData
}

def enableCheckConnect() {
	logDebug("enableCheckConnect: checkConnect")
	app?.updateSetting("CheckConnectEnable", [type:"bool", value: true])
}

private sendLanCmd(ip, port, command, action, commsTo = 5) {
	def myHubAction = new hubitat.device.HubAction(
		outputXOR(command),
		hubitat.device.Protocol.LAN,
		[type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
		 destinationAddress: "${ip}:${port}",
		 encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
		 parseWarning: true,
		 timeout: commsTo,
		 callback: action])
	try {
		sendHubCommand(myHubAction)
	} catch (error) {
		logWarn("sendLanCmd: command to ${ip}:${port} failed. Error = ${error}")
	}
}

private outputXOR(command) {
	def str = ""
	def encrCmd = ""
 	def key = 0xAB
	for (int i = 0; i < command.length(); i++) {
		str = (command.charAt(i) as byte) ^ key
		key = str
		encrCmd += Integer.toHexString(str)
	}
   	return encrCmd
}

private inputXOR(encrResponse) {
	String[] strBytes = encrResponse.split("(?<=\\G.{2})")
	def cmdResponse = ""
	def key = 0xAB
	def nextKey
	byte[] XORtemp
	for(int i = 0; i < strBytes.length-1; i++) {
		nextKey = (byte)Integer.parseInt(strBytes[i], 16)	// could be negative
		XORtemp = nextKey ^ key
		key = nextKey
		cmdResponse += new String(XORtemp)
	}
	return cmdResponse
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }




// ~~~~~ start include (15) davegut.Logging ~~~~~
library ( // library marker davegut.Logging, line 1
	name: "Logging", // library marker davegut.Logging, line 2
	namespace: "davegut", // library marker davegut.Logging, line 3
	author: "Dave Gutheinz", // library marker davegut.Logging, line 4
	description: "Common Logging and info gathering Methods", // library marker davegut.Logging, line 5
	category: "utilities", // library marker davegut.Logging, line 6
	documentationLink: "" // library marker davegut.Logging, line 7
) // library marker davegut.Logging, line 8
//	Updated for Kasa // library marker davegut.Logging, line 9
def label() { // library marker davegut.Logging, line 10
	if (device) { return device.displayName }  // library marker davegut.Logging, line 11
	else { return app.getLabel() } // library marker davegut.Logging, line 12
} // library marker davegut.Logging, line 13

def listAttributes() { // library marker davegut.Logging, line 15
	def attrData = device.getCurrentStates() // library marker davegut.Logging, line 16
	Map attrs = [:] // library marker davegut.Logging, line 17
	attrData.each { // library marker davegut.Logging, line 18
		attrs << ["${it.name}": it.value] // library marker davegut.Logging, line 19
	} // library marker davegut.Logging, line 20
	return attrs // library marker davegut.Logging, line 21
} // library marker davegut.Logging, line 22

def setLogsOff() { // library marker davegut.Logging, line 24
	def logData = [logEnable: logEnable] // library marker davegut.Logging, line 25
	if (logEnable) { // library marker davegut.Logging, line 26
		runIn(1800, debugLogOff) // library marker davegut.Logging, line 27
		logData << [debugLogOff: "scheduled"] // library marker davegut.Logging, line 28
	} // library marker davegut.Logging, line 29
	return logData // library marker davegut.Logging, line 30
} // library marker davegut.Logging, line 31

def logTrace(msg){ log.trace "${label()}: ${msg}" } // library marker davegut.Logging, line 33

def logInfo(msg) {  // library marker davegut.Logging, line 35
	if (infoLog) { log.info "${label()}: ${msg}" } // library marker davegut.Logging, line 36
} // library marker davegut.Logging, line 37

def debugLogOff() { // library marker davegut.Logging, line 39
	device.updateSetting("logEnable", [type:"bool", value: false]) // library marker davegut.Logging, line 40
	logInfo("debugLogOff") // library marker davegut.Logging, line 41
} // library marker davegut.Logging, line 42

def logDebug(msg) { // library marker davegut.Logging, line 44
	if (logEnable) { log.debug "${label()}: ${msg}" } // library marker davegut.Logging, line 45
} // library marker davegut.Logging, line 46

def logWarn(msg) { log.warn "${label()}: ${msg}" } // library marker davegut.Logging, line 48

def logError(msg) { log.error "${label()}: ${msg}" } // library marker davegut.Logging, line 50

// ~~~~~ end include (15) davegut.Logging ~~~~~
