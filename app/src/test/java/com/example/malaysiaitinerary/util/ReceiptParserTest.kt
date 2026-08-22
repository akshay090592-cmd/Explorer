package com.example.malaysiaitinerary.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun parse_standardMalaysianRestaurantReceipt_extractsCorrectFields() {
        val ocrText = """
            JALAN ALOR HAWKER RESTAURANT
            55100 KUALA LUMPUR
            DATE: 2026-03-24 20:30
            RECEIPT #10492
            -----------------------------
            1x CHAR KWAY TEOW     RM 18.00
            1x SATAY 10 PCS       RM 22.50
            2x FRESH COCONUT      RM 16.00
            -----------------------------
            SUBTOTAL:             RM 56.50
            TAX (6%):              RM 3.39
            TOTAL AMOUNT:         RM 59.89
            THANK YOU FOR DINING!
        """.trimIndent()

        val result = ReceiptParser.parse(ocrText)

        assertEquals("MYR", result.currency)
        assertEquals(59.89, result.amount ?: 0.0, 0.001)
        assertEquals("Food", result.category)
        assertEquals("2026-03-24", result.date)
        assertTrue(result.merchant.contains("JALAN ALOR", ignoreCase = true))
    }

    @Test
    fun parse_grabTransportReceipt_extractsTransportCategoryAndCost() {
        val ocrText = """
            GrabCar Receipt
            E-Hailing Services Sdn Bhd
            Date: 25/03/2026 14:15
            Trip from KL Sentral to Batu Caves
            Ride Fare: RM 32.00
            Toll: RM 3.50
            Total Paid: RM 35.50
            Payment Method: Credit Card
        """.trimIndent()

        val result = ReceiptParser.parse(ocrText)

        assertEquals("MYR", result.currency)
        assertEquals(35.50, result.amount ?: 0.0, 0.001)
        assertEquals("Transport", result.category)
        assertEquals("2026-03-25", result.date)
        assertTrue(result.merchant.contains("Grab", ignoreCase = true))
    }

    @Test
    fun parse_hotelInvoiceINR_extractsHotelCategoryAndINR() {
        val ocrText = """
            WOLO HOTEL BUKIT BINTANG
            Tax Invoice
            Date: 2026-03-23
            Guest: John Doe
            Room Charges: INR 14,500.00
            Service Tax: INR 1,200.00
            TOTAL: INR 15700.00
        """.trimIndent()

        val result = ReceiptParser.parse(ocrText)

        assertEquals("INR", result.currency)
        assertEquals(15700.0, result.amount ?: 0.0, 0.001)
        assertEquals("Hotel", result.category)
        assertEquals("2026-03-23", result.date)
        assertTrue(result.merchant.contains("WOLO", ignoreCase = true))
    }

    @Test
    fun parse_shoppingMallReceipt_extractsShoppingCategory() {
        val ocrText = """
            Suria KLCC Shopping Centre
            Uniqlo Malaysia
            Date: 26-03-2026
            Item 1: Airism Shirt RM 79.90
            Item 2: Shorts RM 99.90
            TOTAL DUE: RM 179.80
        """.trimIndent()

        val result = ReceiptParser.parse(ocrText)

        assertEquals("MYR", result.currency)
        assertEquals(179.80, result.amount ?: 0.0, 0.001)
        assertEquals("Shopping", result.category)
        assertEquals("2026-03-26", result.date)
    }

    @Test
    fun parse_emptyOrGibberishText_returnsSensibleDefaults() {
        val ocrText = "random text without any numbers or tags"
        val result = ReceiptParser.parse(ocrText)

        assertEquals("MYR", result.currency)
        assertEquals("Food", result.category)
        assertNotNull(result.merchant)
    }
}
