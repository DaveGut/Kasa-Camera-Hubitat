/*	Kasa Device Driver Series
		Copyright Dave Gutheinz
License:  https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/License.md
===== Link to list of changes =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Changes.pdf
===== Link to Documentation =====
	https://github.com/DaveGut/HubitatActive/blob/master/KasaDevices/Documentation.pdf

Notes on Camera: Older models do not support the 24/7 control function.  Need testing
on newer models to determine if these support that reporting.

===================================================================================================*/
metadata {
	definition (name: "kasaCameraFixed",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: ""
			   ) {
		capability "Configuration"
		capability "VideoCamera"
		command "on", [[name: "Privacy Mode On"]]
		command "off", [[name: "Privacy Mode Off"]]
		command "flip", [[name: "NOT IMPLEMENTED"]]
		command "mute", [[name: "NOT IMPLEMENTED"]]
		command "unmute", [[name: "NOT IMPLEMENTED"]]
		capability "Motion Sensor"
		command "setPollInterval", [[
			name: "Poll Interval in seconds",
			constraints: ["off", "5", "10", "15", "30"],
			type: "ENUM"]]
		command "nightVision", [[
			name: "Night Vision Mode",
			constraints: ["day", "night", "auto"],
			type: "ENUM"]]
		attribute "nightVision", "string"
		capability "Refresh"
		attribute "sdCard", "string"
		attribute "commsError", "bool"
	}
	preferences {
		input ("motionDetect", , "enum", title: "Motion Detect",
			   options: ["on", "off"], defaultValue: "on")
		input ("motionSens", "enum", title: "Motion Detect Sensitivity",
			   options: ["low", "medium", "high"], defaultValue: "low")
		input ("triggerTime", "number", title: "Record if motion lasts (msec)",
			   defaultValue: 1000)
		input ("personDetect", "enum", title: "Person Detect",
			   options: ["on", "off"], defaultValue: "on")
		input ("bcDetect", "enum", title: "Baby Cry Detect",
			   options: ["on", "off"], defaultValue: "off")
		input ("soundDetect", "enum", title: "Sound Detect",
			   options: ["on", "off"], defaultValue: "on")
		input ("soundDetSense", "enum", title: "Sound Detection Senvitivity",
			   options: ["low", "medium", "high"], defaultValue: "low")
		input ("cvrOnOff", "enum", title: "Continuous Video Record",
			   options: ["on", "off"], defaultValue: "on")
		input ("clipAudio", "enum", title: "Clip Audio Recording", 
			   options: ["on", "off"], defaultValue: "on")
		input ("ledOnOff", "enum", title: "LED",
				   options: ["on", "off"], defaultValue: "on")
		input ("infoLog", "bool", title: "Enable information logging",defaultValue: true)
		input ("logEnable", "bool", title: "Enable debug logging", defaultValue: true)
		input("credType", "enum", title: "Credential Type (CAUTION)",
			  options: ["single_pass", "dual_pass"], defaultValue: "single_pass")
	}
}

def installed() {
	def waitFor = configure(true)
	device.updateSetting("logEnable", [type:"bool", value: false])
}

def updated() {
	unschedule()
	def updStatus = [method: "updated"]
	if (logEnable) { runIn(1800, debugLogOff) }
	if (checkCreds) {
		updStatus << [credType: testCreds()]
	}
	updStatus << [textEnable: infoLog, logEnable: logEnable]
	updStatus << [pollInterval: setPollInterval(state.pollInterval)]
	updStatus << [setPreferences: setPreferences()]
	updateAttr("commsError", false)
	state.errorCount = 0
	state.remove("lastCmd")
	pauseExecution(5000)
	runEvery1Hour(refresh)
	logInfo(updStatus)
}

def setPreferences() {
	Map logData = [:]
	Map cmdData = [
		"smartlife.cam.ipcamera.motionDetect":[
			set_is_enable:[value: motionDetect],
			set_sensitivity:[value: motionSens],
			set_min_trigger_time:[
				day_mode_value: triggerTime,
				night_mode_value: triggerTime]],
		"smartlife.cam.ipcamera.led":[
			set_status:[value: ledOnOff]],
		"smartlife.cam.ipcamera.soundDetect":[
			set_is_enable:[value: soundDetect],
			set_sensitivity:[value: soundDetSense]],
		"smartlife.cam.ipcamera.intelligence":[
			set_pd_enable:[value: personDetect],
			set_bcd_enable:[value: bcDetect]],
		"smartlife.cam.ipcamera.delivery":[
			set_clip_audio_is_enable:[value:clipAudio]]
	]
	if (device.currentValue("sdCard") == "ok") {
		cmdData << ["smartlife.cam.ipcamera.vod":[
			set_is_enable:[value: cvrOnOff]]]
	}
	def respData = syncPost(cmdData, setPreferences)
	if (respData == "ERROR") {
		logData << [status: "FAILED", respData: respData]
	} else {
		logData << [status: "OK"]
	}
	refresh()
	return logData
}

def getPreferences() {
	Map prefs = [motionDetect: motionDetect, motionSens: motionSens,
				 triggerTime: triggerTime, personDetect: personDetect,
				 soundDetect: soundDetect, soundDetSense: soundDetSense,
				 cvrOnOff: cvrOnOff, clipAudio: clipAudio,
				 dbLedOnOff: dbLedOnOff, ledOnOff: ledOnOff]
	return prefs
}

//	===== Device Command Methods =====
def flip() { logWarn("FLIP COMMAND NOT IMPLEMENTED!") }
def mute() { logWarn("MUTE COMMAND NOT IMPLEMENTED!") }
def unmute() { logWarn("UNMUTE COMMAND NOT IMPLEMENTED!") }

//	===== refesh methods =====
def refresh() {
	def endTime = (now()/1000).toInteger()
	def startTime = endTime - 3600
	Map cmdData = [
		"smartlife.cam.ipcamera.motionDetect":[
			get_is_enable:[],
			get_sensitivity:[],
			get_min_trigger_time:[]],
		"smartlife.cam.ipcamera.led":[get_status:[]],
		"smartlife.cam.ipcamera.sdCard":[get_sd_card_state:[]],
		"smartlife.cam.ipcamera.soundDetect":[
			get_is_enable:[],
			get_sensitivity:[]],
		"smartlife.cam.ipcamera.intelligence":[
			get_pd_enable:[],
			get_bcd_enable:[]],
		"smartlife.cam.ipcamera.delivery":[get_clip_audio_is_enable:[]],
		"smartlife.cam.ipcamera.dayNight":[get_mode:[]],
		"smartlife.cam.ipcamera.vod":[
			get_is_enable:[],
			get_detect_zone_list: [start_time: startTime, end_time: endTime]],
		"smartlife.cam.ipcamera.switch":[get_is_enable:[]],
	]
	asyncPost(cmdData, "refresh")
	pauseExecution(3000)
	return "commandsSent"
}

//	===== Library Includes =====




// ~~~~~ start include (46) davegut.lib_kasaCam_transport ~~~~~
library ( // library marker davegut.lib_kasaCam_transport, line 1
	name: "lib_kasaCam_transport", // library marker davegut.lib_kasaCam_transport, line 2
	namespace: "davegut", // library marker davegut.lib_kasaCam_transport, line 3
	author: "Dave Gutheinz", // library marker davegut.lib_kasaCam_transport, line 4
	description: "Kasa Camera transport methods", // library marker davegut.lib_kasaCam_transport, line 5
	category: "utilities", // library marker davegut.lib_kasaCam_transport, line 6
	documentationLink: "" // library marker davegut.lib_kasaCam_transport, line 7
) // library marker davegut.lib_kasaCam_transport, line 8
import org.json.JSONObject // library marker davegut.lib_kasaCam_transport, line 9
import groovy.json.JsonOutput // library marker davegut.lib_kasaCam_transport, line 10
import groovy.json.JsonSlurper // library marker davegut.lib_kasaCam_transport, line 11
import java.net.URLEncoder // library marker davegut.lib_kasaCam_transport, line 12

def testCreds() { // library marker davegut.lib_kasaCam_transport, line 14
	def newCredType = credType // library marker davegut.lib_kasaCam_transport, line 15
	Map cmdData = [system: [get_sysinfo:[]]] // library marker davegut.lib_kasaCam_transport, line 16
	def respData = syncPost(cmdData, "testCreds") // library marker davegut.lib_kasaCam_transport, line 17
	if (!respData["system"]) { // library marker davegut.lib_kasaCam_transport, line 18
		if (newCredType == "single_pass") { // library marker davegut.lib_kasaCam_transport, line 19
			newCredType = "dual_pass" // library marker davegut.lib_kasaCam_transport, line 20
		} else { // library marker davegut.lib_kasaCam_transport, line 21
			newCredType = "single_pass" // library marker davegut.lib_kasaCam_transport, line 22
		} // library marker davegut.lib_kasaCam_transport, line 23
		device.updateSetting("credType", [type:"enum", value: newCredType]) // library marker davegut.lib_kasaCam_transport, line 24
	} // library marker davegut.lib_kasaCam_transport, line 25
	return newCredType // library marker davegut.lib_kasaCam_transport, line 26
} // library marker davegut.lib_kasaCam_transport, line 27

def getCreds() { // library marker davegut.lib_kasaCam_transport, line 29
	def creds = parent.credentials // library marker davegut.lib_kasaCam_transport, line 30
	if (credType == "dual_pass") {  // library marker davegut.lib_kasaCam_transport, line 31
		creds = parent.altCredentials } // library marker davegut.lib_kasaCam_transport, line 32
	return creds // library marker davegut.lib_kasaCam_transport, line 33
} // library marker davegut.lib_kasaCam_transport, line 34
def asyncPost(cmdBody, sourceMethod) { // library marker davegut.lib_kasaCam_transport, line 35
	Map cmdData = [body: cmdBody, source: sourceMethod] // library marker davegut.lib_kasaCam_transport, line 36
	state.lastCmd = cmdData // library marker davegut.lib_kasaCam_transport, line 37
	Map logData = [method: "asyncSend", cmdBody: cmdBody,  // library marker davegut.lib_kasaCam_transport, line 38
				   credType: credType] // library marker davegut.lib_kasaCam_transport, line 39
	def reqParams = [ // library marker davegut.lib_kasaCam_transport, line 40
		uri: "https://${getDataValue("deviceIp")}:10443/data/LINKIE.json", // library marker davegut.lib_kasaCam_transport, line 41
		body: "content=${stringifyCmd(cmdBody)}", // library marker davegut.lib_kasaCam_transport, line 42
		contentType: "text/plain", // library marker davegut.lib_kasaCam_transport, line 43
		ignoreSSLIssues: true, // library marker davegut.lib_kasaCam_transport, line 44
		headers: [ // library marker davegut.lib_kasaCam_transport, line 45
			"Authorization": "Basic ${getCreds()}", // library marker davegut.lib_kasaCam_transport, line 46
		], // library marker davegut.lib_kasaCam_transport, line 47
		timeout: 5, // library marker davegut.lib_kasaCam_transport, line 48
	] // library marker davegut.lib_kasaCam_transport, line 49
	try { // library marker davegut.lib_kasaCam_transport, line 50
		asynchttpPost("parseAsyncPost", reqParams, [cmd: cmdBody, source: sourceMethod]) // library marker davegut.lib_kasaCam_transport, line 51
		logData << [status: "OK"] // library marker davegut.lib_kasaCam_transport, line 52
	} catch (err) { // library marker davegut.lib_kasaCam_transport, line 53
		logData << [status: "FAILED", error: err] // library marker davegut.lib_kasaCam_transport, line 54
		logWarn(logData) // library marker davegut.lib_kasaCam_transport, line 55
		handleCommsError([error: "parseAsyncPost"]) // library marker davegut.lib_kasaCam_transport, line 56
	} // library marker davegut.lib_kasaCam_transport, line 57
	logDebug(logData) // library marker davegut.lib_kasaCam_transport, line 58
} // library marker davegut.lib_kasaCam_transport, line 59
def parseAsyncPost(resp, data) { // library marker davegut.lib_kasaCam_transport, line 60
	Map respData = [:] // library marker davegut.lib_kasaCam_transport, line 61
	if (resp.status == 200) { // library marker davegut.lib_kasaCam_transport, line 62
		byte[] respByte = resp.data.decodeBase64() // library marker davegut.lib_kasaCam_transport, line 63
		String encResp = hubitat.helper.HexUtils.byteArrayToHexString(respByte) // library marker davegut.lib_kasaCam_transport, line 64
		respData = new JsonSlurper().parseText(inputXOR(encResp)) // library marker davegut.lib_kasaCam_transport, line 65
		if (!respData.err_code) { // library marker davegut.lib_kasaCam_transport, line 66
			distRespData(respData, data.source) // library marker davegut.lib_kasaCam_transport, line 67
			setCommsError(false) // library marker davegut.lib_kasaCam_transport, line 68
		} else { // library marker davegut.lib_kasaCam_transport, line 69
			Map logData = [method: "parseAsyncPost", status: "errorInReturn", // library marker davegut.lib_kasaCam_transport, line 70
						   respData: respData] // library marker davegut.lib_kasaCam_transport, line 71
		handleCommsError([error: "parseAsyncPost"]) // library marker davegut.lib_kasaCam_transport, line 72
		handleCommsError(data) // library marker davegut.lib_kasaCam_transport, line 73
		} // library marker davegut.lib_kasaCam_transport, line 74
	} else { // library marker davegut.lib_kasaCam_transport, line 75
		Map logData = [method: "parseAsyncPost", status: resp.status, data: data,  // library marker davegut.lib_kasaCam_transport, line 76
					   resp: resp.properties] // library marker davegut.lib_kasaCam_transport, line 77
		logWarn(logData) // library marker davegut.lib_kasaCam_transport, line 78
		handleCommsError([error: "parseAsyncPost"]) // library marker davegut.lib_kasaCam_transport, line 79
	} // library marker davegut.lib_kasaCam_transport, line 80
} // library marker davegut.lib_kasaCam_transport, line 81

def stringifyCmd(cmdData) { // library marker davegut.lib_kasaCam_transport, line 83
	def cmdJson = JsonOutput.toJson(cmdData).toString() // library marker davegut.lib_kasaCam_transport, line 84
	def encCmd = outputXOR(cmdJson) // library marker davegut.lib_kasaCam_transport, line 85
 	byte[] bufByte = hubitat.helper.HexUtils.hexStringToByteArray(encCmd) // library marker davegut.lib_kasaCam_transport, line 86
	def b64Cmd = new String(bufByte.encodeBase64().toString()) // library marker davegut.lib_kasaCam_transport, line 87
	return URLEncoder.encode(b64Cmd) // library marker davegut.lib_kasaCam_transport, line 88
} // library marker davegut.lib_kasaCam_transport, line 89

def syncPost(cmdBody, sourceMethod) { // library marker davegut.lib_kasaCam_transport, line 91
	Map logData = [method: "syncSend", cmdBody: cmdBody, sourceMethod: sourceMethod,  // library marker davegut.lib_kasaCam_transport, line 92
				   credType: credType] // library marker davegut.lib_kasaCam_transport, line 93
	def reqParams = [ // library marker davegut.lib_kasaCam_transport, line 94
		uri: "https://${getDataValue("deviceIp")}:10443/data/LINKIE.json", // library marker davegut.lib_kasaCam_transport, line 95
		body: "content=${stringifyCmd(cmdBody)}", // library marker davegut.lib_kasaCam_transport, line 96
		contentType: "application/octet-stream", // library marker davegut.lib_kasaCam_transport, line 97
		ignoreSSLIssues: true, // library marker davegut.lib_kasaCam_transport, line 98
		headers: [ // library marker davegut.lib_kasaCam_transport, line 99
			"Authorization": "Basic ${getCreds()}", // library marker davegut.lib_kasaCam_transport, line 100
		], // library marker davegut.lib_kasaCam_transport, line 101
		timeout: 4, // library marker davegut.lib_kasaCam_transport, line 102
	] // library marker davegut.lib_kasaCam_transport, line 103
	Map respData = [:] // library marker davegut.lib_kasaCam_transport, line 104
	try { // library marker davegut.lib_kasaCam_transport, line 105
		httpPost(reqParams) { resp -> // library marker davegut.lib_kasaCam_transport, line 106
			logData << [status: resp.status] // library marker davegut.lib_kasaCam_transport, line 107
			if (resp.status == 200) { // library marker davegut.lib_kasaCam_transport, line 108
				byte[] data = parseInputStream(resp.data) // library marker davegut.lib_kasaCam_transport, line 109
				String dataB64 = new String(data) // library marker davegut.lib_kasaCam_transport, line 110
				byte[] respByte = dataB64.decodeBase64() // library marker davegut.lib_kasaCam_transport, line 111
				String encResp = hubitat.helper.HexUtils.byteArrayToHexString(respByte) // library marker davegut.lib_kasaCam_transport, line 112
				respData = new JsonSlurper().parseText(inputXOR(encResp)) // library marker davegut.lib_kasaCam_transport, line 113
				setCommsError(false) // library marker davegut.lib_kasaCam_transport, line 114
			} else { // library marker davegut.lib_kasaCam_transport, line 115
				logData << [errorType: "responseError", data: resp.data] // library marker davegut.lib_kasaCam_transport, line 116
				respData << [status: resp.status, errorType: "httpResponseError"] // library marker davegut.lib_kasaCam_transport, line 117
				logWarn(logData) // library marker davegut.lib_kasaCam_transport, line 118
				handleCommsError([error: "syncPost"]) // library marker davegut.lib_kasaCam_transport, line 119
			} // library marker davegut.lib_kasaCam_transport, line 120
		} // library marker davegut.lib_kasaCam_transport, line 121
	} catch (err) { // library marker davegut.lib_kasaCam_transport, line 122
		logData << [status: "httpError", error: err] // library marker davegut.lib_kasaCam_transport, line 123
		respData << [status: "httpError", errorType: "httpResponseError"] // library marker davegut.lib_kasaCam_transport, line 124
		logWarn(logData) // library marker davegut.lib_kasaCam_transport, line 125
		handleCommsError([error: "syncPost"]) // library marker davegut.lib_kasaCam_transport, line 126
	} // library marker davegut.lib_kasaCam_transport, line 127
	logDebug(logData) // library marker davegut.lib_kasaCam_transport, line 128
	return respData // library marker davegut.lib_kasaCam_transport, line 129
} // library marker davegut.lib_kasaCam_transport, line 130
def parseInputStream(data) { // library marker davegut.lib_kasaCam_transport, line 131
	def dataSize = data.available() // library marker davegut.lib_kasaCam_transport, line 132
	byte[] dataArr = new byte[dataSize] // library marker davegut.lib_kasaCam_transport, line 133
	data.read(dataArr, 0, dataSize) // library marker davegut.lib_kasaCam_transport, line 134
	return dataArr // library marker davegut.lib_kasaCam_transport, line 135
} // library marker davegut.lib_kasaCam_transport, line 136

//	===== Error Handling ===== // library marker davegut.lib_kasaCam_transport, line 138
def handleCommsError(data) { // library marker davegut.lib_kasaCam_transport, line 139
	def count = state.errorCount + 1 // library marker davegut.lib_kasaCam_transport, line 140
	state.errorCount = count // library marker davegut.lib_kasaCam_transport, line 141
	Map logData = [method: "handleCommsError", data: data, count: count] // library marker davegut.lib_kasaCam_transport, line 142
	logData << [count: count, data: data] // library marker davegut.lib_kasaCam_transport, line 143
	switch (count) { // library marker davegut.lib_kasaCam_transport, line 144
		case 1: // library marker davegut.lib_kasaCam_transport, line 145
			//	Unschedule poll then retry the command (in case error is transient) // library marker davegut.lib_kasaCam_transport, line 146
			unschedule("poll") // library marker davegut.lib_kasaCam_transport, line 147
			runIn(1, delayedPassThrough, [data: data]) // library marker davegut.lib_kasaCam_transport, line 148
			logData << [action: "retryCommand"] // library marker davegut.lib_kasaCam_transport, line 149
			logInfo(logData) // library marker davegut.lib_kasaCam_transport, line 150
			break // library marker davegut.lib_kasaCam_transport, line 151
		case 2: // library marker davegut.lib_kasaCam_transport, line 152
			logData << [checkConnect: parent.checkConnect()] // library marker davegut.lib_kasaCam_transport, line 153
			logData << [action: "retryCommand"] // library marker davegut.lib_kasaCam_transport, line 154
			runIn(1, delayedPassThrough, [data: data]) // library marker davegut.lib_kasaCam_transport, line 155
			logInfo(logData) // library marker davegut.lib_kasaCam_transport, line 156
			break // library marker davegut.lib_kasaCam_transport, line 157
		case 3: // library marker davegut.lib_kasaCam_transport, line 158
			logData << [commsError: setCommsError(true)] // library marker davegut.lib_kasaCam_transport, line 159
			logWarn(logData) // library marker davegut.lib_kasaCam_transport, line 160
			break // library marker davegut.lib_kasaCam_transport, line 161
		default: // library marker davegut.lib_kasaCam_transport, line 162
			logData << [status: "retriesDisabled"] // library marker davegut.lib_kasaCam_transport, line 163
			logDebug(logData) // library marker davegut.lib_kasaCam_transport, line 164
			break // library marker davegut.lib_kasaCam_transport, line 165
	} // library marker davegut.lib_kasaCam_transport, line 166
} // library marker davegut.lib_kasaCam_transport, line 167

def delayedPassThrough(data) { // library marker davegut.lib_kasaCam_transport, line 169
	asyncPost(data.cmd, data.source) // library marker davegut.lib_kasaCam_transport, line 170
} // library marker davegut.lib_kasaCam_transport, line 171

def setCommsError(status) { // library marker davegut.lib_kasaCam_transport, line 173
	if (status == false && state.errorCount == 0) { // library marker davegut.lib_kasaCam_transport, line 174
		//	no existing and no current error.  Do nothing // library marker davegut.lib_kasaCam_transport, line 175
	} else if (status == false) { // library marker davegut.lib_kasaCam_transport, line 176
		//	current error.  Reset attribute, reschedule poll interval to user-selected. // library marker davegut.lib_kasaCam_transport, line 177
		updateAttr("commsError", false) // library marker davegut.lib_kasaCam_transport, line 178
		state.errorCount = 0 // library marker davegut.lib_kasaCam_transport, line 179
		setPollInterval() // library marker davegut.lib_kasaCam_transport, line 180
		return [method: "setCommsError", status: "cleared"] // library marker davegut.lib_kasaCam_transport, line 181
	} else { // library marker davegut.lib_kasaCam_transport, line 182
		//	status is true.  set comms error, slow down polling. // library marker davegut.lib_kasaCam_transport, line 183
		updateAttr("commsError", true) // library marker davegut.lib_kasaCam_transport, line 184
		setPollInterval("error") // library marker davegut.lib_kasaCam_transport, line 185
		return [method: "setCommsError", status: "set"] // library marker davegut.lib_kasaCam_transport, line 186
	} // library marker davegut.lib_kasaCam_transport, line 187
} // library marker davegut.lib_kasaCam_transport, line 188
//	===== XOR Encode/Decode ===== // library marker davegut.lib_kasaCam_transport, line 189
private outputXOR(command) { // library marker davegut.lib_kasaCam_transport, line 190
	def str = "" // library marker davegut.lib_kasaCam_transport, line 191
	def encrCmd = "" // library marker davegut.lib_kasaCam_transport, line 192
 	def key = 0xAB // library marker davegut.lib_kasaCam_transport, line 193
	for (int i = 0; i < command.length(); i++) { // library marker davegut.lib_kasaCam_transport, line 194
		str = (command.charAt(i) as byte) ^ key // library marker davegut.lib_kasaCam_transport, line 195
		key = str // library marker davegut.lib_kasaCam_transport, line 196
		encrCmd += Integer.toHexString(str) // library marker davegut.lib_kasaCam_transport, line 197
	} // library marker davegut.lib_kasaCam_transport, line 198
   	return encrCmd // library marker davegut.lib_kasaCam_transport, line 199
} // library marker davegut.lib_kasaCam_transport, line 200
private inputXOR(encrResponse) { // library marker davegut.lib_kasaCam_transport, line 201
	String[] strBytes = encrResponse.split("(?<=\\G.{2})") // library marker davegut.lib_kasaCam_transport, line 202
	def cmdResponse = "" // library marker davegut.lib_kasaCam_transport, line 203
	def key = 0xAB // library marker davegut.lib_kasaCam_transport, line 204
	def nextKey // library marker davegut.lib_kasaCam_transport, line 205
	byte[] XORtemp // library marker davegut.lib_kasaCam_transport, line 206
	for(int i = 0; i < strBytes.length; i++) { // library marker davegut.lib_kasaCam_transport, line 207
		nextKey = (byte)Integer.parseInt(strBytes[i], 16) // library marker davegut.lib_kasaCam_transport, line 208
		XORtemp = nextKey ^ key // library marker davegut.lib_kasaCam_transport, line 209
		key = nextKey // library marker davegut.lib_kasaCam_transport, line 210
		cmdResponse += new String(XORtemp) // library marker davegut.lib_kasaCam_transport, line 211
	} // library marker davegut.lib_kasaCam_transport, line 212
	return cmdResponse // library marker davegut.lib_kasaCam_transport, line 213
} // library marker davegut.lib_kasaCam_transport, line 214

// ~~~~~ end include (46) davegut.lib_kasaCam_transport ~~~~~

// ~~~~~ start include (43) davegut.lib_kasaCam_common ~~~~~
library ( // library marker davegut.lib_kasaCam_common, line 1
	name: "lib_kasaCam_common", // library marker davegut.lib_kasaCam_common, line 2
	namespace: "davegut", // library marker davegut.lib_kasaCam_common, line 3
	author: "Dave Gutheinz", // library marker davegut.lib_kasaCam_common, line 4
	description: "Methods common to Kasa Camera variants", // library marker davegut.lib_kasaCam_common, line 5
	category: "utilities", // library marker davegut.lib_kasaCam_common, line 6
	documentationLink: "" // library marker davegut.lib_kasaCam_common, line 7
) // library marker davegut.lib_kasaCam_common, line 8

//	Installation / Configuration // library marker davegut.lib_kasaCam_common, line 10
def configure(inst=false) { // library marker davegut.lib_kasaCam_common, line 11
	logInfo([method: "configure", startDeviceIp: getDataValue("deviceIp")]) // library marker davegut.lib_kasaCam_common, line 12
	Map logData = [method: "configure", inst: inst] // library marker davegut.lib_kasaCam_common, line 13
	updateAttr("commsError", false) // library marker davegut.lib_kasaCam_common, line 14
	state.pollInterval = "30" // library marker davegut.lib_kasaCam_common, line 15
	state.errorCount = 0 // library marker davegut.lib_kasaCam_common, line 16
	if (!inst) { // library marker davegut.lib_kasaCam_common, line 17
		def waitFor = parent.findDevices(true) // library marker davegut.lib_kasaCam_common, line 18
		logData << [updatedDeviceIp: getDataValue("deviceIp")] // library marker davegut.lib_kasaCam_common, line 19
		logData << [credType: testCreds()] // library marker davegut.lib_kasaCam_common, line 20
	} // library marker davegut.lib_kasaCam_common, line 21
	logData << [refresh: refresh()] // library marker davegut.lib_kasaCam_common, line 22
	logData << [attributes: listAttributes()] // library marker davegut.lib_kasaCam_common, line 23
	logData << [preferences: getPreferences()] // library marker davegut.lib_kasaCam_common, line 24
	logInfo(logData) // library marker davegut.lib_kasaCam_common, line 25
	return // library marker davegut.lib_kasaCam_common, line 26
} // library marker davegut.lib_kasaCam_common, line 27

//	===== Command ===== // library marker davegut.lib_kasaCam_common, line 29
def on() { setCamera("on") } // library marker davegut.lib_kasaCam_common, line 30
def off() { setCamera("off") } // library marker davegut.lib_kasaCam_common, line 31
def setCamera(onOff) { // library marker davegut.lib_kasaCam_common, line 32
	Map cmdData = [ // library marker davegut.lib_kasaCam_common, line 33
		"smartlife.cam.ipcamera.switch":[ // library marker davegut.lib_kasaCam_common, line 34
			set_is_enable:[value: onOff], // library marker davegut.lib_kasaCam_common, line 35
			get_is_enable:[:]]] // library marker davegut.lib_kasaCam_common, line 36
	asyncPost(cmdData, "setCamera") // library marker davegut.lib_kasaCam_common, line 37
} // library marker davegut.lib_kasaCam_common, line 38

def nightVision(mode) { // library marker davegut.lib_kasaCam_common, line 40
	Map cmdData = [ // library marker davegut.lib_kasaCam_common, line 41
		"smartlife.cam.ipcamera.dayNight":[ // library marker davegut.lib_kasaCam_common, line 42
			set_mode:[value: mode], // library marker davegut.lib_kasaCam_common, line 43
			get_mode:[]]] // library marker davegut.lib_kasaCam_common, line 44
	asyncPost(cmdData, "setNightVision") // library marker davegut.lib_kasaCam_common, line 45
} // library marker davegut.lib_kasaCam_common, line 46

def setPollInterval(interval) { // library marker davegut.lib_kasaCam_common, line 48
	unschedule("poll") // library marker davegut.lib_kasaCam_common, line 49
log.debug interval // library marker davegut.lib_kasaCam_common, line 50
	//	update state.pollInterval unless called from a commsError. // library marker davegut.lib_kasaCam_common, line 51
	//	Then do not so poll is recovered on error recovery. // library marker davegut.lib_kasaCam_common, line 52
	if (interval == "error") { // library marker davegut.lib_kasaCam_common, line 53
		runEvery5Minutes("poll") // library marker davegut.lib_kasaCam_common, line 54
		interval = "5 minutes" // library marker davegut.lib_kasaCam_common, line 55
	} else { // library marker davegut.lib_kasaCam_common, line 56
		if (interval == null) {  // library marker davegut.lib_kasaCam_common, line 57
			interval = "30" // library marker davegut.lib_kasaCam_common, line 58
		} // library marker davegut.lib_kasaCam_common, line 59
		state.pollInterval = interval // library marker davegut.lib_kasaCam_common, line 60
		if (interval != "off") { // library marker davegut.lib_kasaCam_common, line 61
			schedule("3/${interval} * * * * ?", "poll") // library marker davegut.lib_kasaCam_common, line 62
		} // library marker davegut.lib_kasaCam_common, line 63
	} // library marker davegut.lib_kasaCam_common, line 64
	logDebug([method: "setPollInterval", pollInterval: interval]) // library marker davegut.lib_kasaCam_common, line 65
log.warn interval // library marker davegut.lib_kasaCam_common, line 66
	return interval // library marker davegut.lib_kasaCam_common, line 67
} // library marker davegut.lib_kasaCam_common, line 68
def poll() { // library marker davegut.lib_kasaCam_common, line 69
	Map cmdData = [system: [get_sysinfo:[]]] // library marker davegut.lib_kasaCam_common, line 70
	asyncPost(cmdData, "motionParse") // library marker davegut.lib_kasaCam_common, line 71
} // library marker davegut.lib_kasaCam_common, line 72

def rebootDev() { // library marker davegut.lib_kasaCam_common, line 74
	Map cmdData = [ // library marker davegut.lib_kasaCam_common, line 75
		"smartlife.cam.ipcamera.system":[ // library marker davegut.lib_kasaCam_common, line 76
			set_reboot:[]]] // library marker davegut.lib_kasaCam_common, line 77
	asyncPost(cmdData, "rebootDev") // library marker davegut.lib_kasaCam_common, line 78
} // library marker davegut.lib_kasaCam_common, line 79

//	===== Distribute Async Response and Parse Data ===== // library marker davegut.lib_kasaCam_common, line 81
def distRespData(respData, source) { // library marker davegut.lib_kasaCam_common, line 82
	Map logData = [method: "distRespData", source: source] // library marker davegut.lib_kasaCam_common, line 83
	def error = false // library marker davegut.lib_kasaCam_common, line 84
	respData.each { // library marker davegut.lib_kasaCam_common, line 85
		if (respData == "ERROR") { // library marker davegut.lib_kasaCam_common, line 86
			error = true // library marker davegut.lib_kasaCam_common, line 87
			logData << [respData: respData] // library marker davegut.lib_kasaCam_common, line 88
		} else if (it.value.err_code && it.value.err_code !=0) { // library marker davegut.lib_kasaCam_common, line 89
			error = true // library marker davegut.lib_kasaCam_common, line 90
			logData << ["${it.key}": it.value] // library marker davegut.lib_kasaCam_common, line 91
		} else { // library marker davegut.lib_kasaCam_common, line 92
			switch(it.key) { // library marker davegut.lib_kasaCam_common, line 93
				case "system": motionParse(it, source); break // library marker davegut.lib_kasaCam_common, line 94
				case "smartlife.cam.ipcamera.dayNight": parseDayNight(it, source); break // library marker davegut.lib_kasaCam_common, line 95
				case "smartlife.cam.ipcamera.dndSchedule": parseDndSchedule(it, source); break // library marker davegut.lib_kasaCam_common, line 96
				case "smartlife.cam.ipcamera.switch": parseSwitch(it, source); break // library marker davegut.lib_kasaCam_common, line 97
				case "smartlife.cam.ipcamera.audio": parseAudio(it, source); break // library marker davegut.lib_kasaCam_common, line 98
				case "smartlife.cam.ipcamera.ptz": parsePtz(it, source); break // library marker davegut.lib_kasaCam_common, line 99
				case "smartlife.cam.ipcamera.vod": parseVod(it, source); break // library marker davegut.lib_kasaCam_common, line 100
				case "smartlife.cam.ipcamera.delivery": parseDelivery(it, source); break // library marker davegut.lib_kasaCam_common, line 101
				case "smartlife.cam.ipcamera.intelligence": parseIntel(it, source); break // library marker davegut.lib_kasaCam_common, line 102
				case "smartlife.cam.ipcamera.soundDetect": parseSoundDetect(it, source); break // library marker davegut.lib_kasaCam_common, line 103
				case "smartlife.cam.ipcamera.led": parseLed(it, source); break // library marker davegut.lib_kasaCam_common, line 104
				case "smartlife.cam.ipcamera.sdCard": parseSdCard(it, source); break // library marker davegut.lib_kasaCam_common, line 105
				case "smartlife.cam.ipcamera.motionDetect": parseMotionDetect(it, source); break // library marker davegut.lib_kasaCam_common, line 106
				default:  // library marker davegut.lib_kasaCam_common, line 107
					logData << ["${it.key}": "unhandled", data: it] // library marker davegut.lib_kasaCam_common, line 108
					error = true // library marker davegut.lib_kasaCam_common, line 109
			} // library marker davegut.lib_kasaCam_common, line 110
		} // library marker davegut.lib_kasaCam_common, line 111
	} // library marker davegut.lib_kasaCam_common, line 112
	if (error == true) { // library marker davegut.lib_kasaCam_common, line 113
		logWarn(logData) // library marker davegut.lib_kasaCam_common, line 114
	} else { // library marker davegut.lib_kasaCam_common, line 115
		logDebug(logData) // library marker davegut.lib_kasaCam_common, line 116
	} // library marker davegut.lib_kasaCam_common, line 117
} // library marker davegut.lib_kasaCam_common, line 118

def motionParse(respData, source) { // library marker davegut.lib_kasaCam_common, line 120
	def resp = respData.value.get_sysinfo.system // library marker davegut.lib_kasaCam_common, line 121
	def lastActTime = resp.last_activity_timestamp // library marker davegut.lib_kasaCam_common, line 122
	def sysTime = resp.system_time // library marker davegut.lib_kasaCam_common, line 123
	def deltaTime = sysTime - lastActTime // library marker davegut.lib_kasaCam_common, line 124
	if (lastActTime > state.lastActiveTime) { // library marker davegut.lib_kasaCam_common, line 125
		updateAttr("motion", "active") // library marker davegut.lib_kasaCam_common, line 126
		state.lastActiveTime = lastActTime // library marker davegut.lib_kasaCam_common, line 127
	} else if (device.currentValue("motion") == "active" && // library marker davegut.lib_kasaCam_common, line 128
			   15 < deltaTime) { // library marker davegut.lib_kasaCam_common, line 129
		updateAttr("motion", "inactive") // library marker davegut.lib_kasaCam_common, line 130
	} // library marker davegut.lib_kasaCam_common, line 131
} // library marker davegut.lib_kasaCam_common, line 132

def parseSdCard(data, source) { // library marker davegut.lib_kasaCam_common, line 134
	Map logData = [method: "parseSdCard", source: source] // library marker davegut.lib_kasaCam_common, line 135
	data.value.each { // library marker davegut.lib_kasaCam_common, line 136
		def key = it.key // library marker davegut.lib_kasaCam_common, line 137
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 138
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 139
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 140
			switch(key) { // library marker davegut.lib_kasaCam_common, line 141
				case "get_sd_card_state": // library marker davegut.lib_kasaCam_common, line 142
					setting = it.value.state // library marker davegut.lib_kasaCam_common, line 143
					updateAttr("sdCard", setting) // library marker davegut.lib_kasaCam_common, line 144
					valueLog << [sdCard: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 145
					break // library marker davegut.lib_kasaCam_common, line 146
				default: // library marker davegut.lib_kasaCam_common, line 147
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 148
			} // library marker davegut.lib_kasaCam_common, line 149
		} else { // library marker davegut.lib_kasaCam_common, line 150
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 151
		} // library marker davegut.lib_kasaCam_common, line 152
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 153
	} // library marker davegut.lib_kasaCam_common, line 154
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 155
} // library marker davegut.lib_kasaCam_common, line 156

def parseDayNight(data, source) { // library marker davegut.lib_kasaCam_common, line 158
	Map logData = [method: "parseDayNight", source: source] // library marker davegut.lib_kasaCam_common, line 159
	data.value.each { // library marker davegut.lib_kasaCam_common, line 160
		def key = it.key // library marker davegut.lib_kasaCam_common, line 161
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 162
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 163
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 164
			switch(key) { // library marker davegut.lib_kasaCam_common, line 165
				case "set_mode": break // library marker davegut.lib_kasaCam_common, line 166
				case "get_mode": // library marker davegut.lib_kasaCam_common, line 167
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 168
					updateAttr("nightVision", setting) // library marker davegut.lib_kasaCam_common, line 169
					valueLog << [nightVision: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 170
					break // library marker davegut.lib_kasaCam_common, line 171
				default: // library marker davegut.lib_kasaCam_common, line 172
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 173
			} // library marker davegut.lib_kasaCam_common, line 174
		} else { // library marker davegut.lib_kasaCam_common, line 175
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 176
		} // library marker davegut.lib_kasaCam_common, line 177
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 178
	} // library marker davegut.lib_kasaCam_common, line 179
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 180
} // library marker davegut.lib_kasaCam_common, line 181

def parseDndSchedule(data, source) { // library marker davegut.lib_kasaCam_common, line 183
	Map logData = [method: "parseDndSchedule", source: source] // library marker davegut.lib_kasaCam_common, line 184
	data.value.each { // library marker davegut.lib_kasaCam_common, line 185
		def key = it.key // library marker davegut.lib_kasaCam_common, line 186
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 187
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 188
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 189
			switch(key) { // library marker davegut.lib_kasaCam_common, line 190
				case "set_dnd_enable": break // library marker davegut.lib_kasaCam_common, line 191
				case "get_dnd_enable": // library marker davegut.lib_kasaCam_common, line 192
					setting = it.value.enable // library marker davegut.lib_kasaCam_common, line 193
					updateAttr("doNotDisturb", setting) // library marker davegut.lib_kasaCam_common, line 194
					valueLog << [doNotDisturb: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 195
					break // library marker davegut.lib_kasaCam_common, line 196
				default: // library marker davegut.lib_kasaCam_common, line 197
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 198
			} // library marker davegut.lib_kasaCam_common, line 199
		} else { // library marker davegut.lib_kasaCam_common, line 200
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 201
		} // library marker davegut.lib_kasaCam_common, line 202
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 203
	} // library marker davegut.lib_kasaCam_common, line 204
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 205
} // library marker davegut.lib_kasaCam_common, line 206

def parseAudio(data, source) { // library marker davegut.lib_kasaCam_common, line 208
	Map logData = [method: "parseAudio", source: source] // library marker davegut.lib_kasaCam_common, line 209
	data.value.each { // library marker davegut.lib_kasaCam_common, line 210
		def key = it.key // library marker davegut.lib_kasaCam_common, line 211
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 212
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 213
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 214
			switch(key) { // library marker davegut.lib_kasaCam_common, line 215
				case "set_mic_config": break // library marker davegut.lib_kasaCam_common, line 216
				case "get_mic_config": // library marker davegut.lib_kasaCam_common, line 217
					setting = it.value.volume // library marker davegut.lib_kasaCam_common, line 218
					def mute = "muted" // library marker davegut.lib_kasaCam_common, line 219
					if (setting > 0) { // library marker davegut.lib_kasaCam_common, line 220
						state.lastVolume = setting // library marker davegut.lib_kasaCam_common, line 221
						mute = "unmuted" // library marker davegut.lib_kasaCam_common, line 222
					} // library marker davegut.lib_kasaCam_common, line 223
					updateAttr("mute", mute) // library marker davegut.lib_kasaCam_common, line 224
					updateAttr("volume", setting) // library marker davegut.lib_kasaCam_common, line 225
					valueLog << [volume: setting, mute: mute, status: "OK"] // library marker davegut.lib_kasaCam_common, line 226
				case "set_quickres_state":  // library marker davegut.lib_kasaCam_common, line 227
					valueLog << [status: "OK"] // library marker davegut.lib_kasaCam_common, line 228
					break // library marker davegut.lib_kasaCam_common, line 229
				default: // library marker davegut.lib_kasaCam_common, line 230
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 231
			} // library marker davegut.lib_kasaCam_common, line 232
		} else { // library marker davegut.lib_kasaCam_common, line 233
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 234
		} // library marker davegut.lib_kasaCam_common, line 235
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 236
	} // library marker davegut.lib_kasaCam_common, line 237
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 238
} // library marker davegut.lib_kasaCam_common, line 239

def parseSwitch(data, source) { // library marker davegut.lib_kasaCam_common, line 241
	Map logData = [method: "parseSwitch", source: source] // library marker davegut.lib_kasaCam_common, line 242
	data.value.each { // library marker davegut.lib_kasaCam_common, line 243
		def key = it.key // library marker davegut.lib_kasaCam_common, line 244
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 245
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 246
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 247
			switch(key) { // library marker davegut.lib_kasaCam_common, line 248
				case "set_is_enable": break; // library marker davegut.lib_kasaCam_common, line 249
				case "get_is_enable": // library marker davegut.lib_kasaCam_common, line 250
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 251
					updateAttr("camera", setting) // library marker davegut.lib_kasaCam_common, line 252
					valueLog << [camera: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 253
					runIn(5, loadCameraSettings) // library marker davegut.lib_kasaCam_common, line 254
					break // library marker davegut.lib_kasaCam_common, line 255
				default: // library marker davegut.lib_kasaCam_common, line 256
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 257
			} // library marker davegut.lib_kasaCam_common, line 258
		} else { // library marker davegut.lib_kasaCam_common, line 259
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 260
		} // library marker davegut.lib_kasaCam_common, line 261
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 262
	} // library marker davegut.lib_kasaCam_common, line 263
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 264
} // library marker davegut.lib_kasaCam_common, line 265

def loadCameraSettings() { // library marker davegut.lib_kasaCam_common, line 267
	Map settings = [md: motionDetect, // library marker davegut.lib_kasaCam_common, line 268
					sd: soundDetect, // library marker davegut.lib_kasaCam_common, line 269
					pd: personDetect, // library marker davegut.lib_kasaCam_common, line 270
					cvr: cvrOnOff] // library marker davegut.lib_kasaCam_common, line 271
		updateAttr("settings", settings) // library marker davegut.lib_kasaCam_common, line 272
} // library marker davegut.lib_kasaCam_common, line 273

def parseMotionDetect(data, source) { // library marker davegut.lib_kasaCam_common, line 275
	Map logData = [method: "parseMotionDetect", source: source] // library marker davegut.lib_kasaCam_common, line 276
	data.value.each { // library marker davegut.lib_kasaCam_common, line 277
		def key = it.key // library marker davegut.lib_kasaCam_common, line 278
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 279
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 280
			logData << ["${key}": [error: it.value.err_code]] // library marker davegut.lib_kasaCam_common, line 281
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 282
			switch(key) { // library marker davegut.lib_kasaCam_common, line 283
				case "set_sensitivity": break; // library marker davegut.lib_kasaCam_common, line 284
				case "get_sensitivity": // library marker davegut.lib_kasaCam_common, line 285
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 286
					device.updateSetting("motionSens", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 287
					valueLog << [motionSens: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 288
					break // library marker davegut.lib_kasaCam_common, line 289
				case "set_is_enable": break; // library marker davegut.lib_kasaCam_common, line 290
				case "get_is_enable": // library marker davegut.lib_kasaCam_common, line 291
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 292
					device.updateSetting("motionDetect", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 293
					valueLog << [motionDetect: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 294
					break // library marker davegut.lib_kasaCam_common, line 295
				case "set_min_trigger_time": break; // library marker davegut.lib_kasaCam_common, line 296
				case "get_min_trigger_time": // library marker davegut.lib_kasaCam_common, line 297
					setting = it.value.day_mode_value // library marker davegut.lib_kasaCam_common, line 298
					device.updateSetting("triggerTime", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 299
					valueLog << [triggerTime: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 300
					break // library marker davegut.lib_kasaCam_common, line 301
				default: // library marker davegut.lib_kasaCam_common, line 302
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 303
			} // library marker davegut.lib_kasaCam_common, line 304
		} else { // library marker davegut.lib_kasaCam_common, line 305
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 306
		} // library marker davegut.lib_kasaCam_common, line 307
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 308

	} // library marker davegut.lib_kasaCam_common, line 310
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 311
} // library marker davegut.lib_kasaCam_common, line 312

def parseLed(data, source) { // library marker davegut.lib_kasaCam_common, line 314
	Map logData = [method: "parseLed", source: source] // library marker davegut.lib_kasaCam_common, line 315
	data.value.each { // library marker davegut.lib_kasaCam_common, line 316
		def key = it.key // library marker davegut.lib_kasaCam_common, line 317
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 318
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 319
			logData << ["${key}": [error: it.value.err_code]] // library marker davegut.lib_kasaCam_common, line 320
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 321
			switch(key) { // library marker davegut.lib_kasaCam_common, line 322
				case "set_buttonled_status": break; // library marker davegut.lib_kasaCam_common, line 323
				case "get_buttonled_status": // library marker davegut.lib_kasaCam_common, line 324
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 325
					device.updateSetting("dbLedOnOff", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 326
					valueLog << [dbLedOnOff: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 327
					break // library marker davegut.lib_kasaCam_common, line 328
				case "set_status": break; // library marker davegut.lib_kasaCam_common, line 329
				case "get_status": // library marker davegut.lib_kasaCam_common, line 330
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 331
					device.updateSetting("ledOnOff", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 332
					valueLog << [ledOnOff: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 333
					break // library marker davegut.lib_kasaCam_common, line 334
				default: // library marker davegut.lib_kasaCam_common, line 335
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 336
			} // library marker davegut.lib_kasaCam_common, line 337
		} else { // library marker davegut.lib_kasaCam_common, line 338
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 339
		} // library marker davegut.lib_kasaCam_common, line 340
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 341

	} // library marker davegut.lib_kasaCam_common, line 343
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 344
} // library marker davegut.lib_kasaCam_common, line 345

def parseSoundDetect(data, source) { // library marker davegut.lib_kasaCam_common, line 347
	Map logData = [method: "parseSoundDetect", source: source] // library marker davegut.lib_kasaCam_common, line 348
	data.value.each { // library marker davegut.lib_kasaCam_common, line 349
		def key = it.key // library marker davegut.lib_kasaCam_common, line 350
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 351
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 352
			logData << ["${key}": [error: it.value.err_code]] // library marker davegut.lib_kasaCam_common, line 353
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 354
			switch(key) { // library marker davegut.lib_kasaCam_common, line 355
				case "set_is_enable": break; // library marker davegut.lib_kasaCam_common, line 356
				case "get_is_enable": // library marker davegut.lib_kasaCam_common, line 357
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 358
					device.updateSetting("soundDetect", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 359
					valueLog << [soundDetect: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 360
					break // library marker davegut.lib_kasaCam_common, line 361
				case "set_sensitivity": break; // library marker davegut.lib_kasaCam_common, line 362
				case "get_sensitivity": // library marker davegut.lib_kasaCam_common, line 363
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 364
					device.updateSetting("soundDetSense", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 365
					valueLog << [soundDetSense: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 366
					break // library marker davegut.lib_kasaCam_common, line 367
				default: // library marker davegut.lib_kasaCam_common, line 368
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 369
			} // library marker davegut.lib_kasaCam_common, line 370
		} else { // library marker davegut.lib_kasaCam_common, line 371
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 372
		} // library marker davegut.lib_kasaCam_common, line 373
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 374

	} // library marker davegut.lib_kasaCam_common, line 376
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 377
} // library marker davegut.lib_kasaCam_common, line 378

def parseIntel(data, source) { // library marker davegut.lib_kasaCam_common, line 380
	Map logData = [method: "parseIntel", source: source] // library marker davegut.lib_kasaCam_common, line 381
	data.value.each { // library marker davegut.lib_kasaCam_common, line 382
		def key = it.key // library marker davegut.lib_kasaCam_common, line 383
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 384
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 385
			logData << ["${key}": [error: it.value.err_code]] // library marker davegut.lib_kasaCam_common, line 386
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 387
			switch(key) { // library marker davegut.lib_kasaCam_common, line 388
				case "set_pd_enable": break; // library marker davegut.lib_kasaCam_common, line 389
				case "get_pd_enable": // library marker davegut.lib_kasaCam_common, line 390
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 391
					device.updateSetting("personDetect", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 392
					valueLog << [personDetect: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 393
					break // library marker davegut.lib_kasaCam_common, line 394
				default: // library marker davegut.lib_kasaCam_common, line 395
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 396
			} // library marker davegut.lib_kasaCam_common, line 397
		} else { // library marker davegut.lib_kasaCam_common, line 398
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 399
		} // library marker davegut.lib_kasaCam_common, line 400
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 401

	} // library marker davegut.lib_kasaCam_common, line 403
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 404
} // library marker davegut.lib_kasaCam_common, line 405

def parseDelivery(data, source) { // library marker davegut.lib_kasaCam_common, line 407
	Map logData = [method: "parseDelivery", source: source] // library marker davegut.lib_kasaCam_common, line 408
	data.value.each { // library marker davegut.lib_kasaCam_common, line 409
		def key = it.key // library marker davegut.lib_kasaCam_common, line 410
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 411
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 412
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 413
			switch(key) { // library marker davegut.lib_kasaCam_common, line 414
				case "set_clip_audio_is_enable": break; // library marker davegut.lib_kasaCam_common, line 415
				case "get_clip_audio_is_enable": // library marker davegut.lib_kasaCam_common, line 416
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 417
					device.updateSetting("clipAudio", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 418
					valueLog << [clipAudio: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 419
					break // library marker davegut.lib_kasaCam_common, line 420
				default: // library marker davegut.lib_kasaCam_common, line 421
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 422
			} // library marker davegut.lib_kasaCam_common, line 423
		} else { // library marker davegut.lib_kasaCam_common, line 424
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 425
		} // library marker davegut.lib_kasaCam_common, line 426
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 427
	} // library marker davegut.lib_kasaCam_common, line 428
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 429
} // library marker davegut.lib_kasaCam_common, line 430

def parseVod(data, source) { // library marker davegut.lib_kasaCam_common, line 432
	Map logData = [method: "parseVod", source: source] // library marker davegut.lib_kasaCam_common, line 433
	def error = false // library marker davegut.lib_kasaCam_common, line 434
	data.value.each { // library marker davegut.lib_kasaCam_common, line 435
		def key = it.key // library marker davegut.lib_kasaCam_common, line 436
		Map valueLog = [resp: it.value] // library marker davegut.lib_kasaCam_common, line 437
		if (it.value.err_code == 0) { // library marker davegut.lib_kasaCam_common, line 438
			def setting = "ERROR" // library marker davegut.lib_kasaCam_common, line 439
			switch(key) { // library marker davegut.lib_kasaCam_common, line 440
				case "set_is_enable": break; // library marker davegut.lib_kasaCam_common, line 441
				case "get_is_enable": // library marker davegut.lib_kasaCam_common, line 442
					setting = it.value.value // library marker davegut.lib_kasaCam_common, line 443
					device.updateSetting("cvrOnOff", [type:"enum", value: setting]) // library marker davegut.lib_kasaCam_common, line 444
					logData << [cvrOnOff: setting, status: "OK"] // library marker davegut.lib_kasaCam_common, line 445
					break // library marker davegut.lib_kasaCam_common, line 446
				case "get_detect_zone_list": // library marker davegut.lib_kasaCam_common, line 447
					setting = it.value.list // library marker davegut.lib_kasaCam_common, line 448
					state.recentEvents = setting // library marker davegut.lib_kasaCam_common, line 449
					logData << [recentEvents: setting, status: "OK"]	 // library marker davegut.lib_kasaCam_common, line 450
					break // library marker davegut.lib_kasaCam_common, line 451
				default: // library marker davegut.lib_kasaCam_common, line 452
					valueLog << [status: "unhandled"] // library marker davegut.lib_kasaCam_common, line 453
			} // library marker davegut.lib_kasaCam_common, line 454
		} else { // library marker davegut.lib_kasaCam_common, line 455
			valueLog << [status: "notParsed"] // library marker davegut.lib_kasaCam_common, line 456
		} // library marker davegut.lib_kasaCam_common, line 457
		logData << ["${key}": valueLog] // library marker davegut.lib_kasaCam_common, line 458
	} // library marker davegut.lib_kasaCam_common, line 459
	logDebug(logData) // library marker davegut.lib_kasaCam_common, line 460
} // library marker davegut.lib_kasaCam_common, line 461

def updateAttr(attr, value) { // library marker davegut.lib_kasaCam_common, line 463
	if (device.currentValue(attr) != value) { // library marker davegut.lib_kasaCam_common, line 464
		sendEvent(name: attr, value: value) // library marker davegut.lib_kasaCam_common, line 465
	} // library marker davegut.lib_kasaCam_common, line 466
} // library marker davegut.lib_kasaCam_common, line 467

// ~~~~~ end include (43) davegut.lib_kasaCam_common ~~~~~

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
