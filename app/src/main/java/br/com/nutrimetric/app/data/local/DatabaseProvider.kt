package br.com.nutrimetric.app.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var appDatabase: AppDatabase? = null

    @Volatile
    private var tacoDatabase: TacoDatabase? = null

    fun getAppDatabase(context: Context): AppDatabase {
        return appDatabase ?: synchronized(this) {
            appDatabase ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pratobr_database"
            ).fallbackToDestructiveMigration(dropAllTables = true)
             .build().also { appDatabase = it }
        }
    }

    fun getTacoDatabase(context: Context): TacoDatabase {
        return tacoDatabase ?: synchronized(this) {
            if (tacoDatabase == null) {
                val dbFile = context.getDatabasePath("taco.db")
                if (!dbFile.exists()) {
                    try {
                        // 1. Ensure databases directory exists
                        dbFile.parentFile?.mkdirs()
                        
                        // 2. Copy the asset database file to the system database directory
                        context.assets.open("database/taco.db").use { input ->
                            dbFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        android.util.Log.d("DatabaseProvider", "Banco de dados taco.db copiado com sucesso dos assets.")

                        // 3. Patch the room_master_table identity_hash to match what Room expects
                        val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath,
                            null,
                            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                        )
                        try {
                            val cursor = sqliteDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'", null)
                            val tableExists = cursor.use { it.moveToFirst() }
                            if (tableExists) {
                                sqliteDb.execSQL("UPDATE room_master_table SET identity_hash = 'e35b32d9c9497b960def12eb8d8125d3'")
                                android.util.Log.d("DatabaseProvider", "Identity hash patcheado com sucesso!")
                            }
                        } catch (ex: Exception) {
                            android.util.Log.e("DatabaseProvider", "Erro ao patchear o hash da tabela master", ex)
                        } finally {
                            sqliteDb.close()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DatabaseProvider", "Erro ao preparar taco.db", e)
                    }
                }

                // Build Room Database using the already copied and patched SQLite file
                tacoDatabase = Room.databaseBuilder(
                    context.applicationContext,
                    TacoDatabase::class.java,
                    "taco.db"
                ).fallbackToDestructiveMigration()
                 .build()
            }
            tacoDatabase!!
        }
    }
}
