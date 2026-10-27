package com.fakecall.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.TelephonyManager
import java.util.Locale

data class PickedContact(val name: String, val number: String, val photoPath: String?)

object ContactReader {

    /** Opens the phone's contact list. No contacts permission is needed for this. */
    fun pickIntent() = Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)

    fun read(context: Context, uri: Uri): PickedContact? {
        val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_URI)
        val row = runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Triple(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty(), cursor.getString(2))
                } else {
                    null
                }
            }
        }.getOrNull() ?: return null
        val photo = row.third?.let { PhotoStore.save(context, Uri.parse(it)) }
        return PickedContact(row.first, row.second, photo)
    }
}

data class Country(val iso: String, val name: String, val code: String) {
    /** Flag emoji built from the two-letter country code. */
    val flag: String
        get() = iso.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

object Countries {
    val all = listOf(
        Country("AE", "United Arab Emirates", "+971"),
        Country("AU", "Australia", "+61"),
        Country("BD", "Bangladesh", "+880"),
        Country("BR", "Brazil", "+55"),
        Country("CA", "Canada", "+1"),
        Country("CN", "China", "+86"),
        Country("DE", "Germany", "+49"),
        Country("EG", "Egypt", "+20"),
        Country("ES", "Spain", "+34"),
        Country("FR", "France", "+33"),
        Country("GB", "United Kingdom", "+44"),
        Country("ID", "Indonesia", "+62"),
        Country("IE", "Ireland", "+353"),
        Country("IN", "India", "+91"),
        Country("IT", "Italy", "+39"),
        Country("JP", "Japan", "+81"),
        Country("KE", "Kenya", "+254"),
        Country("KR", "South Korea", "+82"),
        Country("LK", "Sri Lanka", "+94"),
        Country("MX", "Mexico", "+52"),
        Country("MY", "Malaysia", "+60"),
        Country("NG", "Nigeria", "+234"),
        Country("NL", "Netherlands", "+31"),
        Country("NP", "Nepal", "+977"),
        Country("NZ", "New Zealand", "+64"),
        Country("PH", "Philippines", "+63"),
        Country("PK", "Pakistan", "+92"),
        Country("RU", "Russia", "+7"),
        Country("SA", "Saudi Arabia", "+966"),
        Country("SG", "Singapore", "+65"),
        Country("TR", "Turkey", "+90"),
        Country("US", "United States", "+1"),
        Country("ZA", "South Africa", "+27"),
    )

    fun byIso(iso: String): Country = all.firstOrNull { it.iso == iso } ?: all.first { it.iso == "US" }

    /** Picks the SIM's country, then the network's, then the phone's language region. */
    fun guessIso(context: Context): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
            .map { it.uppercase(Locale.ROOT) }
            .firstOrNull { iso -> all.any { it.iso == iso } }
            ?: "US"
    }
}

object PhoneNumbers {
    /**
     * Returns the number in international form, e.g. "+919876543210".
     * Numbers already starting with + or 00 keep their own country code.
     */
    fun withCountryCode(countryCode: String, input: String): String? {
        val trimmed = input.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 3) return null
        return when {
            trimmed.startsWith("+") -> "+$digits"
            trimmed.startsWith("00") -> "+${digits.drop(2)}"
            else -> countryCode + digits.trimStart('0')
        }
    }
}
