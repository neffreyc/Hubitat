# Hubitat
Repository for Hubitat Apps and Drivers

## Tank Utility Driver
This driver retrieves and saves values from Tank Utility API every 3 hours.  This device driver includes a tile attribute that can be used by the attribute template in the dashboards.  The tile has a div that is assigned to norm, warn, alarm which allows for some CSS formating in the dashboards to give color to the tile.  Battery status will show up in the upper left corner if the status for the battery is not "ok".  This statusis based off of the battery_warn and battery_crit flags from the API. Finally, the lastreading
date will appear under the reading if the last reading is over 2 days old, indicating a stale reading.

### ATTRIBUTES
- battery - (ok, warning, critical) Retrieved from the warn and crit flags returned by the Tank Utility API
- lastreading - The date of the last reading.  Returned from Tank Utility
- lastfilled - The date in which the value increased more than 5%.  Keep an eye on my monthly filling
- capacity - Capacity of the Tank, returned from Tank Utility
- level - % of the tank that is filled
- tile - For used to display a guage on the dashboards using the attribute template
  - Battery will show in upper left if set to warning (yellow) or critical (red)
  - Date underneath is the lastreading value if the last reading is over 2 days old to indicate it is stale
  - Color change based of the tank level compared to the Alarm and Warning Level set
 
 ![Image of tile](https://github.com/neffreyc/Hubitat/blob/main/Images/TankUtilityGauge.png)
 ![Image of tile](https://github.com/neffreyc/Hubitat/blob/main/Images/TankUtilityGuages.png)
 
### SETUP
 - Tank Utility User Name: The email use used to set up Tank Utility account
 - Tank Utility Password: The password used to set up Tank Utility account
 - Warning Level: The level at which you concider it to be a warning. Used to create the tile attribute.
 - Alarm Level: The level at which you concider it to be an alarm. Used to create the tile attribute.
 - Device Network ID: The Tank Utility ID from running the Devices API call.
 
 ```
       curl --user <my_username>:<my_password> https://data.tankutility.com/api/getToken
 ```      
 
 ```
       curl https://data.tankutility.com/api/devices?token=<my_personal_token>
 ```
