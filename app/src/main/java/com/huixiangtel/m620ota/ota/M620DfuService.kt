package com.huixiangtel.m620ota.ota

import android.app.Activity
import com.huixiangtel.m620ota.MainActivity
import no.nordicsemi.android.dfu.DfuBaseService

/** Nordic DFU 前台服务，升级过程在后台执行并通过通知栏展示进度。 */
class M620DfuService : DfuBaseService() {

    /** 点击升级进度通知时回到主界面。 */
    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java
}
