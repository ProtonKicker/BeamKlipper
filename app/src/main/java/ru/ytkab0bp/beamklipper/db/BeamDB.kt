package ru.ytkab0bp.beamklipper.db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.cloud.CloudAPI
import ru.ytkab0bp.beamklipper.events.InstanceCreatedEvent
import ru.ytkab0bp.beamklipper.events.InstanceDestroyedEvent
import ru.ytkab0bp.beamklipper.events.InstanceUpdatedEvent
import java.io.File

class BeamDB(context: Context?) : SQLiteOpenHelper(context, DB_NAME, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_INSTANCES ($COLUMN_ID TEXT, $COLUMN_NAME TEXT, $COLUMN_ICON TEXT, $COLUMN_AUTOSTART INTEGER, $COLUMN_REMOTE_ID TEXT, $COLUMN_REMOTE_TOKEN TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_INSTANCES ADD COLUMN $COLUMN_REMOTE_ID TEXT")
            db.execSQL("ALTER TABLE $TABLE_INSTANCES ADD COLUMN $COLUMN_REMOTE_TOKEN TEXT")
        }
    }

    fun getInstances(): List<KlipperInstance> {
        val instances = mutableListOf<KlipperInstance>()
        val c = readableDatabase.rawQuery("SELECT * FROM $TABLE_INSTANCES", null)
        val cv = ContentValues()
        while (c.moveToNext()) {
            DatabaseUtils.cursorRowToContentValues(c, cv)
            val inst = KlipperInstance()
            inst.id = cv.getAsString(COLUMN_ID)!!
            inst.name = cv.getAsString(COLUMN_NAME)!!
            inst.icon = InstanceIcon.byKey(cv.getAsString(COLUMN_ICON)!!)
            inst.autostart = cv[COLUMN_AUTOSTART] as? String == "1"
            inst.remoteId = if (cv.containsKey(COLUMN_REMOTE_ID)) cv.getAsString(COLUMN_REMOTE_ID) else null
            inst.remoteToken = if (cv.containsKey(COLUMN_REMOTE_TOKEN)) cv.getAsString(COLUMN_REMOTE_TOKEN) else null
            instances.add(inst)
        }
        c.close()
        return instances
    }

    fun insert(inst: KlipperInstance) {
        val cv = ContentValues().apply {
            put(COLUMN_ID, inst.id)
            put(COLUMN_NAME, inst.name)
            put(COLUMN_ICON, inst.icon.name)
            put(COLUMN_AUTOSTART, inst.autostart)
            put(COLUMN_REMOTE_ID, inst.remoteId)
            put(COLUMN_REMOTE_TOKEN, inst.remoteToken)
        }
        writableDatabase.insert(TABLE_INSTANCES, null, cv)
        KlipperInstance.onInstancesLoadedFromDB(getInstances())
        KlipperApp.EVENT_BUS.fireEvent(InstanceCreatedEvent(inst.id!!))
    }

    fun update(inst: KlipperInstance) {
        val cv = ContentValues().apply {
            put(COLUMN_ID, inst.id)
            put(COLUMN_NAME, inst.name)
            put(COLUMN_ICON, inst.icon.name)
            put(COLUMN_AUTOSTART, inst.autostart)
            put(COLUMN_REMOTE_ID, inst.remoteId)
            put(COLUMN_REMOTE_TOKEN, inst.remoteToken)
        }
        writableDatabase.update(TABLE_INSTANCES, cv, "id = ?", arrayOf(inst.id))
        KlipperInstance.onInstancesLoadedFromDB(getInstances())
        KlipperApp.EVENT_BUS.fireEvent(InstanceUpdatedEvent(inst.id!!))
    }

    fun delete(inst: KlipperInstance) {
        if (inst.remoteId != null) {
            CloudAPI.INSTANCE.remoteDeletePrinter(inst.remoteId) {}
        }
        writableDatabase.delete(TABLE_INSTANCES, "id = ?", arrayOf(inst.id))
        KlipperInstance.onInstancesLoadedFromDB(getInstances())
        deleteRecur(inst.directory)
        KlipperApp.EVENT_BUS.fireEvent(InstanceDestroyedEvent(inst.id!!))
    }

    companion object {
        private const val DB_NAME = "beam.db"
        private const val VERSION = 2
        private const val TABLE_INSTANCES = "instances"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_ICON = "icon"
        private const val COLUMN_AUTOSTART = "autostart"
        private const val COLUMN_REMOTE_ID = "remote_id"
        private const val COLUMN_REMOTE_TOKEN = "remote_token"

        private fun deleteRecur(f: File) {
            if (f.isDirectory) {
                f.listFiles()?.forEach { deleteRecur(it) }
            }
            f.delete()
        }
    }
}
