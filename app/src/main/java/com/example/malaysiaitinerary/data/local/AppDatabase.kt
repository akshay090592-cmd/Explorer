package com.example.malaysiaitinerary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.malaysiaitinerary.data.local.dao.DocumentDao
import com.example.malaysiaitinerary.data.local.dao.ItineraryDao
import com.example.malaysiaitinerary.data.local.dao.ExpenseDao
import com.example.malaysiaitinerary.data.local.dao.WeatherDao

import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.data.local.entity.Expense
import com.example.malaysiaitinerary.data.local.entity.WeatherLog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.malaysiaitinerary.data.local.dao.TripDao
import com.example.malaysiaitinerary.data.local.entity.Trip

@Database(entities = [Trip::class, ItineraryItem::class, Document::class, Expense::class, WeatherLog::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun itineraryDao(): ItineraryDao
    abstract fun documentDao(): DocumentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun weatherDao(): WeatherDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "itinerary_database"
                )
                .addCallback(ItineraryDatabaseCallback(scope, context.applicationContext))
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun reseedDemoData(context: Context) {
        val today = java.time.LocalDate.now()
        val t1 = today.plusDays(1).toString()
        val t6 = today.plusDays(6).toString()

        if (tripDao().getTripCount() == 0) {
            tripDao().insertTrip(
                Trip(
                    id = 1L,
                    name = "Malaysia Sample Trip",
                    destination = "Malaysia",
                    startDate = t1,
                    endDate = t6,
                    isSample = true,
                    isActive = true
                )
            )
        }

        ItineraryDatabaseCallback.populateDatabase(itineraryDao(), today)
        ItineraryDatabaseCallback.populateDocuments(documentDao(), context, today)
        ItineraryDatabaseCallback.populateExpenses(expenseDao(), today)
    }

    private class ItineraryDatabaseCallback(
        private val scope: CoroutineScope,
        private val context: Context
    ) : RoomDatabase.Callback() {
        
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val totalCount = database.itineraryDao().getItemCount()
                    val nonDemoCount = database.itineraryDao().getNonDemoCount()
                    if (totalCount == 0 || nonDemoCount == 0) {
                        database.reseedDemoData(context)
                    }
                }
            }
        }




        companion object {
            suspend fun populateDatabase(itineraryDao: ItineraryDao, today: java.time.LocalDate) {
            itineraryDao.deleteAll()
            val t1 = today.plusDays(1).toString()
            val t2 = today.plusDays(2).toString()
            val t3 = today.plusDays(3).toString()
            val t4 = today.plusDays(4).toString()
            val t5 = today.plusDays(5).toString()
            val t6 = today.plusDays(6).toString()

            val items = listOf(
                // Day 1 (Tomorrow - T+1): Flight to KL & Check in
                ItineraryItem(
                    date = t1,
                    title = "Kempegowda International Airport",
                    locationName = "BLR Airport",
                    startTime = "08:00",
                    description = "Starting the demo journey from Bangalore.",
                    type = "ACTIVITY",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Kempegowda+International+Airport+Bengaluru",
                    imageUrl = "file:///android_asset/images/img_35.jpg",
                    isDemo = true
                ),
                ItineraryItem(
                    date = t1,
                    title = "Accommodation: WOLO Kuala Lumpur",
                    locationName = "WOLO KL",
                    startTime = "14:00",
                    description = "Check into your stylish hotel at Bukit Bintang.",
                    type = "ACCOMMODATION",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=WOLO+Kuala+Lumpur",
                    imageUrl = "file:///android_asset/images/img_0.jpg",
                    isDemo = true
                ),
                ItineraryItem(
                    date = t1,
                    title = "Lunch at Madam Kwan's",
                    locationName = "Madam Kwan's, Pavilion KL",
                    startTime = "15:00",
                    description = "Famous for Nasi Lemak and authentic Malaysian flavors.",
                    type = "MEAL",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Madam+Kwan%27s%2C+Pavilion+KL",
                    vegOptions = "Vegetable Curry | Stir-fried Lotus Root",
                    nonVegOptions = "Nasi Lemak with Chicken Curry | Beef Rendang",
                    imageUrl = "file:///android_asset/images/img_1.jpg",
                    isDemo = true
                ),
                ItineraryItem(
                    date = t1,
                    title = "Petronas Twin Towers Photography",
                    locationName = "Petronas Twin Towers",
                    startTime = "19:00",
                    description = "Golden-hour shots at the iconic Petronas Twin Towers.",
                    type = "ACTIVITY",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Petronas+Twin+Towers",
                    imageUrl = "file:///android_asset/images/img_3.jpg",
                    isDemo = true
                ),

                // Day 2 (T+2): Pudu & Batu Caves
                ItineraryItem(
                    date = t2,
                    title = "Breakfast at Ah Weng Koh Tea",
                    locationName = "ICC Pudu",
                    startTime = "08:30",
                    description = "Traditional Hainanese tea and toast.",
                    type = "MEAL",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=ICC+Pudu",
                    vegOptions = "Hainan Tea with Kaya Butter Toast",
                    nonVegOptions = "Nasi Lemak with Ayam Goreng",
                    imageUrl = "file:///android_asset/images/img_5.jpg",
                    isDemo = true
                ),
                ItineraryItem(
                    date = t2,
                    title = "Batu Caves Tour",
                    locationName = "Batu Caves",
                    startTime = "10:30",
                    description = "Explore the colorful 272 steps and limestone cave temples.",
                    type = "ACTIVITY",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Batu+Caves",
                    imageUrl = "file:///android_asset/images/img_7.jpg",
                    isDemo = true
                ),
                ItineraryItem(
                    date = t2,
                    title = "Dinner at Jalan Alor Night Market",
                    locationName = "Jalan Alor, Bukit Bintang",
                    startTime = "20:00",
                    description = "Atmospheric street food market with grilled satay and seafood.",
                    type = "MEAL",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Jalan+Alor",
                    imageUrl = "file:///android_asset/images/img_4.jpg",
                    isDemo = true
                ),

                // Day 3 (T+3): Penang
                ItineraryItem(
                    date = t3,
                    title = "Flight to Penang",
                    locationName = "KLIA Airport -> Penang (PEN)",
                    startTime = "09:00",
                    description = "Morning flight to Georgetown, Penang.",
                    type = "TRANSIT",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Penang+International+Airport",
                    imageUrl = "file:///android_asset/images/img_12.jpg",
                    isDemo = true
                ),
                ItineraryItem(
                    date = t3,
                    title = "Street Art Walk in Georgetown",
                    locationName = "Georgetown Street Art",
                    startTime = "14:00",
                    description = "Discover famous murals and heritage houses.",
                    type = "ACTIVITY",
                    googleMapsUrl = "https://www.google.com/maps/search/?api=1&query=Georgetown+Street+Art",
                    imageUrl = "file:///android_asset/images/img_15.jpg",
                    isDemo = true
                )
            )
            itineraryDao.insertItems(items)
        }

        suspend fun populateDocuments(documentDao: DocumentDao, context: Context, today: java.time.LocalDate) {
            documentDao.deleteAll()
            val t1 = today.plusDays(1).toString()
            val initialDocs = listOf(
                Triple("Sample_Flight_Ticket.pdf", "FLIGHT", t1 to "Kuala Lumpur"),
                Triple("Sample_Hotel_Booking.pdf", "ACCOMMODATION", t1 to "WOLO KL")
            )

            initialDocs.forEach { (fileName, type, dateAndLoc) ->
                val (date, docLoc) = dateAndLoc
                try {
                    val assetFile = "documents/$fileName"
                    val inputStream = context.assets.open(assetFile)
                    val internalFile = java.io.File(context.filesDir, fileName)
                    
                    if (!internalFile.exists() || internalFile.length() == 0L) {
                        val outputStream = java.io.FileOutputStream(internalFile)
                        inputStream.use { input ->
                            outputStream.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    val document = Document(
                        fileName = fileName,
                        filePath = internalFile.absolutePath,
                        type = type,
                        date = date,
                        location = docLoc,
                        itineraryItemId = if (fileName == "Sample_Flight_Ticket.pdf") 1 else null,
                        isDemo = true
                    )
                    documentDao.insertDocument(document)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        suspend fun populateExpenses(expenseDao: ExpenseDao, today: java.time.LocalDate) {
            expenseDao.deleteAll()
            val t1 = today.plusDays(1).toString()
            val t2 = today.plusDays(2).toString()

            val sampleExpenses = listOf(
                Expense(
                    amount = 45.0,
                    currency = "MYR",
                    convertedAmountMYR = 45.0,
                    convertedAmountINR = 810.0,
                    category = "Food",
                    date = t1,
                    description = "Lunch at Madam Kwan's",
                    isDemo = true
                ),
                Expense(
                    amount = 120.0,
                    currency = "MYR",
                    convertedAmountMYR = 120.0,
                    convertedAmountINR = 2160.0,
                    category = "Transport",
                    date = t1,
                    description = "KLIA Express & Grab Taxi",
                    isDemo = true
                ),
                Expense(
                    amount = 35.0,
                    currency = "MYR",
                    convertedAmountMYR = 35.0,
                    convertedAmountINR = 630.0,
                    category = "Food",
                    date = t2,
                    description = "Hainanese Breakfast & Tea",
                    isDemo = true
                )
            )

            sampleExpenses.forEach { expenseDao.insertExpense(it) }
        }
    }
}
}
