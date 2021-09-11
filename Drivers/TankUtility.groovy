///////////////////////////////////////////////////////////////////////////////////////
// 
// Tank Utility Driver 
// This driver retrieves and saves values from Tank Utility API every 3 hours.  Tank 
// Utility device collects data every 6 hours but only transmits it back to the Tank
// Utility servers every 24 hours.  So there is no need to continuously poll the service.
// This device driver includes a tile attribute that can be used by the attribute template
// in the dashboards.  The tile has a div that is assigned to norm, warn, alarm which allows 
// for some CSS formating in the dashboards to give color to the tile.  Battery status will 
// show up in the upper left corner if the status for the battery is not "ok".  This status
// is based off of the battery_warn and battery_crit flags from the API. Finally, the lastreading
// date will appear under the reading if the last reading is over 2 days old, indicating a stale
// reading.
// 
// Tank Utility API: http://apidocs.tankutility.com
//
// SETUP
// Tank Utility User Name: The email use used to set up Tank Utility account
// Tank Utility Password: The password used to set up Tank Utility account
// Warning Level: The level at which you concider it to be a warning. Used to create the tile attribute.
// Alarm Level: The level at which you concider it to be an alarm. Used to create the tile attribute.
// Device Network ID: The Tank Utility ID from running the Devices API call.
//                    curl --user <my_username>:<my_password> https://data.tankutility.com/api/getToken
//                    curl https://data.tankutility.com/api/devices?token=<my_personal_token>
//
// Author: Jeff Campbell
//
// Release: 1.0.0  9/5/2021  Initial Release
///////////////////////////////////////////////////////////////////////////////////////
metadata {
	definition (name: "Tank Utility", namespace: "neffreyc" , author: "Jeff Campbell") {
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Temperature Measurement"

        attribute "battery", "string"
		attribute "lastreading", "date"
		attribute "capacity", "number"
		attribute "level", "number"
        attribute "tile", "string"
        attribute "lastfilled", "date"
	}
}

preferences {
    input "username", "email", title: "Tank Utility User Name", required: true
    input "password", "password", title: "Tank Utility Password", required: true
    input "warn", "number", title: "Warning Level (%)",  required: false
    input "alarm", "number", title: "Alarm Level (%)", required: false
}
    
void installed() {
	log.info "installed()"
	updated()
}

void updated() {
    log.info "updated"
    if (!"${username}".isEmpty() && !"${password}".isEmpty() )
    {
        poll()
        runEvery3Hours("poll")
    }
}
// parse the information that comes back from Tank Utility and set the attributes
def parse(Map frame) {
    if( frame.device.battery_crit )
    {
        if( !device.currentValue("battery") || device.currentValue("battery") != "critical" ) {
            sendEvent(name:"battery", value: "critical", descriptionText: "Battery : Critical")
        }
    }
    else if( frame.device.battery_warn )
    {
        if( !device.currentValue("battery") || device.currentValue("battery") != "warning" ) {
            sendEvent(name:"battery", value: "warning", descriptionText: "Battery : Warning" )
        }
    }
    else
    {
        if ( !device.currentValue("battery") || device.currentValue("battery") != "ok") {
            sendEvent(name:"battery", value: "ok", descriptionText: "Battery : OK" )
        }
    }
    
    // Set LastFilled to today's date if the tank level rises more than 5% since prior reading
    if( device.currentValue("level") ){
        if( frame.device.lastReading.tank > device.currentValue("level") + 5 ) {
            Date currentDate = new Date(now())   
            sendEvent(name: "lastfilled", value: currentDate, descriptionText: "Tank Last Filled: ${currentDate}")
        }
    }
    
    // Only send events when the last reading is newer than the prior reading
    if( !device.currentValue("lastreading") || device.currentValue("lastreading") != frame.device.lastReading.time_iso )
    {
        sendEvent(name: "level", value: (frame.device.lastReading.tank).toFloat().round(2), descriptionText: "Tank Level: ${frame.device.lastReading.tank}" )
        sendEvent(name: "lastreading", value: frame.device.lastReading.time_iso, descriptionText: "Tank Reading Time: ${frame.device.lastReading.time_iso}" )
        sendEvent(name: "capacity", value: frame.device.capacity )
        sendEvent(name: "temperature", value: frame.device.lastReading.temperature.toInteger(), descriptionText: "Tank Temperature: ${frame.device.lastReading.temperature}", unit: "F" )
    }
    
    // Generate the custom tile for dashboards
    generateTile(frame)
}


def refresh() {
	log.info "refresh called"
	poll()
}

void poll() {
	log.info "Executing 'poll' "
	String apiToken = getToken()
    if( apiToken )
    {
        getTankData( apiToken )
    }
}

// Get an API Token from Tank Utility
String getToken() {
    String token = null
    if( "${username}".isEmpty() || "${password}".isEmpty() )
    {
         log.debug "Username and/or password are empty"
         return null
    }
    
    String authorize = "${username}:${password}"
	String authorize_encoded = authorize.bytes.encodeBase64()
    
    def Params = [
		uri: "https://data.tankutility.com",
		path: "/api/getToken",
		headers: ['Authorization': "Basic $authorize_encoded"],
		timeout: 20
	]
    
    httpGet(Params){ resp ->
			if(resp.status == 200){
				if (resp.data.token) {
                    token = resp.data.token
				} 
			}
		}
  
    return token
}

// Get the data from Tank Utility 
def getTankData(String token) {
    log.info "Retrieving Tank Data"
    def Params = [
		uri: "https://data.tankutility.com",
        path: "/api/devices/${device.getDeviceNetworkId()}",
		query: [
            token: "${token}"
			],
		timeout: 20
	]
    
    httpGet(Params){ resp ->
					if(resp.status == 200){
                        parse( resp.data )
					}else{
                        log.error "Get device data ${resp.status}"
					}
				}

}

// Generate the tile attribute to be used with the dashboards.  This adds additional html tags 
// so that the dashboard can be formated with color if wished.
def generateTile(Map frame) {
    String alert = ""
    String battery_status = ""
    String level = ""
    String stale = ""
    String fill = "#fff"
    
    // Set alert
    // This will define the class used on a DIV that encapsulates the guage
    // This also defines the color of the guage and tank level text
    if( alarm && frame.device.lastReading.tank < alarm )
    {
        alert = "alarm"
        fill = "#f00"
    } 
    else if ( warn && frame.device.lastReading.tank < warn ) 
    {
        alert = "warn"
        fill = "#ff0"
    }
    else 
    {
        alert = "norm"
        fill = "#fff"
    }

    def offset = 188 - (188 * (frame.device.lastReading.tank.toInteger()/100))
    level = "<svg viewBox='0 0 100 100' class='gauge'>"
    level += "<path class='dial' fill='none' stroke='#bbb'stroke-opacity='50%' stroke-width='10' d='M 21.716 78.284 A 40 40 0 1 1 78.284 78.284' stroke-linecap='round'></path>"
    level += "<text x='50' y='60' fill='${fill}' class='value-text' font-size='28px' font-weight='bold' text-anchor='middle'>${frame.device.lastReading.tank.toInteger()}</text>"
    level += "<path class='value' fill='none' stroke='${fill}' stroke-width='10.5' d='M 21.716 78.284 A 40 40 0 1 1 78.284 78.284' stroke-linecap='round' style='stroke-dasharray:188; stroke-dashoffset:${offset};' ></path>"
    level += "</svg>"
    
    // Battery Status
    String batteryStatus = device.currentValue("battery")
    if (batteryStatus == "critical")
    {
        if( alert == "alarm" )
        {
            battery_status = "<div class='battery' style='position: absolute; top: 0px; cursor: pointer;'><i class='material-icons battery_alert' style='font-size:20px; color:white;'> battery_alert </i></div>"
        }
        else 
        {
            battery_status = "<div class='battery' style='position: absolute; top: 0px; cursor: pointer;'><i class='material-icons battery_alert' style='font-size:20px; color:red;'> battery_alert </i></div>"
        }
    }
    else if(batteryStatus == "warning")
    {
        if( alert == "warn" )
        {
            battery_status = "<div class='battery' style='position: absolute; top: 0px; cursor: pointer;'><i class='material-icons battery_alert' style='font-size:20px; color:white;'> battery_alert </i></div>"
        }
        else
        {
            battery_status = "<div class='battery' style='position: absolute; top: 0px; cursor: pointer;'><i class='material-icons battery_alert' style='font-size:20px; color:yellow;'> battery_alert </i></div>"
        }
    }
    else
    {
        battery_status = ""
    }
                        
    // The frame is stale if it is older than 2 days.  Display the last reading.
    Date lastReading = new Date(frame.device.lastReading.time)
    Date currentDate = new Date(now())
    if(lastReading.plus(2) < currentDate) 
    {
        stale = "<div id='stale'>${lastReading.format("EEE, MMM d,yyyy H:m")}</div>"
    }
    else
    {
        stale = ""
    }
    
    tile = "${battery_status}<div class='${alert}'><div id='level' style='width: 100px; height: inherit; margin: 0 auto;'>${level}</div>${stale}</div>"
    sendEvent( name: "tile", value: tile)
}
