package com.valeriopezone.obd2wificonnector

import com.github.eltonvs.obd.command.ATCommand

class CustomOBDCommand(cmd : String) : ATCommand() {
    override val tag = "CUSTOM_${cmd}"
    override val name = "CUSTOM CMD ${cmd}"
    override val pid = cmd
}