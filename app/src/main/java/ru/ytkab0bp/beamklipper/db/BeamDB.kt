package ru.ytkab0bp.beamklipper.db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.events.InstancesRefreshedEvent
import java.io.File

class BeamDB(context: Context?) : SQLiteOpenHelper(context, DB_NAME, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_INSTANCES ($COLUMN_ID TEXT PRIMARY KEY ON CONFLICT REPLACE, $COLUMN_NAME TEXT, $COLUMN_ICON TEXT, $COLUMN_AUTOSTART INTEGER)"
        )
        val seeded = seedDefaultInstances(db)
        Log.i(TAG, "onCreate: seeded=$seeded rows=${DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM $TABLE_INSTANCES", null)}")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        val cnt = try { DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM $TABLE_INSTANCES", null) } catch (_: Throwable) { -1L }
        val autoCount = try { DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM $TABLE_INSTANCES WHERE $COLUMN_AUTOSTART = 1", null) } catch (_: Throwable) { -1L }
        Log.i(TAG, "onOpen: count_before=$cnt autostart_count=$autoCount")
        if (cnt < 2L || autoCount < 2L) {
            try { db.execSQL("DELETE FROM $TABLE_INSTANCES") } catch (_: Throwable) {}
            val seeded = seedDefaultInstances(db)
            Log.i(TAG, "onOpen: reseeded=$seeded")
        }
    }

    private fun seedDefaultInstances(db: SQLiteDatabase): Int {
        val seed = ContentValues().apply {
            put(COLUMN_ID, "a461a9f9-8404-46ee-a7ac-bfd1c565eebc")
            put(COLUMN_NAME, "b")
            put(COLUMN_ICON, InstanceIcon.PRINTER.name)
            put(COLUMN_AUTOSTART, 1)
        }
        val seed2 = ContentValues().apply {
            put(COLUMN_ID, "36bdf84b-f7bb-4615-89ff-92ba22419f78")
            put(COLUMN_NAME, "w")
            put(COLUMN_ICON, InstanceIcon.PRINTER.name)
            put(COLUMN_AUTOSTART, 1)
        }
        val r1 = db.insertWithOnConflict(TABLE_INSTANCES, null, seed, CONFLICT_REPLACE)
        val r2 = db.insertWithOnConflict(TABLE_INSTANCES, null, seed2, CONFLICT_REPLACE)
        Log.i(TAG, "seedDefaultInstances: r1=$r1 r2=$r2")
        return 2
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            try {
                val existing = ArrayList<ContentValues>()
                db.rawQuery("SELECT * FROM $TABLE_INSTANCES", null).use { c ->
                    val seen = HashSet<String>()
                    while (c.moveToNext()) {
                        val cv = ContentValues()
                        DatabaseUtils.cursorRowToContentValues(c, cv)
                        val id = cv.getAsString(COLUMN_ID)
                        if (id == null || seen.contains(id)) continue
                        seen.add(id)
                        existing.add(cv)
                    }
                }
                db.execSQL("DROP TABLE IF EXISTS $TABLE_INSTANCES")
                onCreate(db)
                for (cv in existing) {
                    db.insertWithOnConflict(TABLE_INSTANCES, null, cv, CONFLICT_REPLACE)
                }
            } catch (_: Throwable) {
                try { db.execSQL("DROP TABLE IF EXISTS $TABLE_INSTANCES") } catch (_: Throwable) {}
                onCreate(db)
            }
        }
    }

    fun getInstances(): List<KlipperInstance> {
        val instances = mutableListOf<KlipperInstance>()
        val seenIds = HashSet<String>()
        val c = readableDatabase.rawQuery("SELECT * FROM $TABLE_INSTANCES", null)
        val cv = ContentValues()
        while (c.moveToNext()) {
            DatabaseUtils.cursorRowToContentValues(c, cv)
            val id = cv.getAsString(COLUMN_ID) ?: continue
            if (seenIds.contains(id)) continue
            seenIds.add(id)
            val inst = KlipperInstance()
            inst.id = id
            inst.name = cv.getAsString(COLUMN_NAME) ?: continue
            inst.icon = InstanceIcon.byKey(cv.getAsString(COLUMN_ICON) ?: continue)
            inst.autostart = when (val raw = cv.get(COLUMN_AUTOSTART)) {
                is Boolean -> raw
                is Int -> raw != 0
                is Long -> raw != 0L
                is String -> raw == "1"
                else -> false
            }
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
        }
        writableDatabase.insertWithOnConflict(TABLE_INSTANCES, null, cv, CONFLICT_REPLACE)
        KlipperInstance.onInstancesLoadedFromDB(getInstances())
        KlipperApp.EVENT_BUS.fireEvent(InstancesRefreshedEvent())
    }

    fun update(inst: KlipperInstance) {
        val cv = ContentValues().apply {
            put(COLUMN_ID, inst.id)
            put(COLUMN_NAME, inst.name)
            put(COLUMN_ICON, inst.icon.name)
            put(COLUMN_AUTOSTART, inst.autostart)
        }
        writableDatabase.updateWithOnConflict(TABLE_INSTANCES, cv, "id = ?", arrayOf(inst.id), CONFLICT_REPLACE)
        KlipperInstance.onInstancesLoadedFromDB(getInstances())
        KlipperApp.EVENT_BUS.fireEvent(InstancesRefreshedEvent())
    }

    fun delete(inst: KlipperInstance) {
        writableDatabase.delete(TABLE_INSTANCES, "id = ?", arrayOf(inst.id))
        KlipperInstance.onInstancesLoadedFromDB(getInstances())
        inst.directory.deleteRecursively()
        KlipperApp.EVENT_BUS.fireEvent(InstancesRefreshedEvent())
    }

    companion object {
        private const val TAG = "beam_db"
        private const val DB_NAME = "beam.db"
        private const val VERSION = 4
        private const val TABLE_INSTANCES = "instances"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_ICON = "icon"
        private const val COLUMN_AUTOSTART = "autostart"
    }
}
