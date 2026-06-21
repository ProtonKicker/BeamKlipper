package ru.ytkab0bp.beamklipper.serial

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber

object KlipperProbeTable {
    private var mInstance: ProbeTable? = null

    @JvmStatic
    fun getInstance(): ProbeTable {
        if (mInstance == null) {
            mInstance = UsbSerialProber.getDefaultProbeTable()
            mInstance!!.addProduct(0x1D50, 0x614E, CdcAcmSerialDriver::class.java)
            mInstance!!.addProduct(0xDD8, 0x3701, Ch34xSerialDriver::class.java)
        }
        return mInstance!!
    }
}
