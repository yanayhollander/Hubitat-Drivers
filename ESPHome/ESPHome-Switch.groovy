/**
 *  MIT License
 *  Copyright 2022 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
metadata {
    definition(name: 'ESPHome Switch', namespace: 'esphome', author: 'Jonathan Bradshaw') {
        singleThreaded: true

        capability 'Actuator'
        capability 'Switch'
        capability 'Initialize'

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
    }

    preferences {
        input name: 'ipAddress',    // required setting for API library
                type: 'text',
                title: 'Device IP Address',
                required: true

        input name: 'password',     // optional setting for API library
                type: 'text',
                title: 'Device Password <i>(if required)</i>',
                required: false

        input name: 'switch',       // allows the user to select which entity to use
            type: 'enum',
            title: 'ESPHome Entity',
            required: state.entities?.size() > 0,
            options: state.entities?.collectEntries { k, v -> [ k, v.name ] }
            defaultValue: state.entities ? state.entities.keySet()[0] : '' // default to first

        input name: 'logEnable',    // if enabled the library will log debug details
                type: 'bool',
                title: 'Enable Debug Logging',
                required: false,
                defaultValue: false

        input name: 'logTextEnable',
              type: 'bool',
              title: 'Enable descriptionText logging',
              required: false,
              defaultValue: true
    }
}

public void initialize() {
    state.clear()

    // API library command to open socket to device, it will automatically reconnect if needed 
    openSocket()

    if (logEnable) {
        runIn(1800, 'logsOff')
    }
}

public void installed() {
    log.info "${device} driver installed"
}

public void logsOff() {
    espHomeSubscribeLogs(LOG_LEVEL_INFO, false) // disable device logging
    device.updateSetting('logEnable', false)
    log.info "${device} debug logging disabled"
}

public void updated() {
    log.info "${device} driver configuration updated"
    initialize()
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// driver commands
public void on() {
    if (logTextEnable) { log.info "${device} on" }
    espHomeSwitchCommand(key: settings.switch as int, state: true)
}

public void off() {
    if (logTextEnable) { log.info "${device} off" }
    espHomeSwitchCommand(key: settings.switch as int, state: false)
}

// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            // This will populate the cover dropdown with all the entities
            // discovered and the entity key which is required when sending commands
            if (message.platform == 'switch') {
                state.entities = (state.entities ?: [:]) + [ (message.key): message ]
            }
            break

        case 'state':
            // Check if the entity key matches the message entity key received to update device state
            if (settings.switch as Integer == message.key) {
                String value = message.state ? 'on' : 'off'
                if (device.currentValue('switch') != value) {
                    sendEvent([
                        name: 'switch',
                        value: value,
                        descriptionText: "Switch is ${value}"
                    ])
                }
            }
            break
    }
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
