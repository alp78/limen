package dev.peringer.limen.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

/** A phone region shown in the form, complete with its recognised calling code and flag. */
data class PhoneCountry(
  val regionCode: String,
  val callingCode: Int,
) {
  val flag: String get() = CountryDirectory.flag(regionCode)
  fun label(locale: Locale = Locale.getDefault()): String =
    "$flag +$callingCode ${CountryDirectory.name(regionCode, locale)}"
}

/** An address country, kept independently from the telephone country. */
data class AddressCountry(val regionCode: String) {
  val flag: String get() = CountryDirectory.flag(regionCode)
  fun label(locale: Locale = Locale.getDefault()): String =
    "$flag ${CountryDirectory.name(regionCode, locale)}"
}

/**
 * Uses Google's phone-number metadata for each supported region. It is more accurate and more
 * maintainable than a project-owned table of regular expressions and covers shared calling codes.
 */
object InternationalPhone {
  private val util = PhoneNumberUtil.getInstance()

  val countries: List<PhoneCountry> by lazy {
    util.supportedRegions
      .asSequence()
      .filter { it.length == 2 && util.getCountryCodeForRegion(it) > 0 }
      .map { PhoneCountry(it, util.getCountryCodeForRegion(it)) }
      .sortedBy { CountryDirectory.name(it.regionCode, Locale.ENGLISH) }
      .toList()
  }
  private val supportedRegions: Set<String> by lazy { countries.mapTo(mutableSetOf()) { it.regionCode } }

  fun isValid(value: String, regionCode: String): Boolean {
    if (regionCode !in supportedRegions) return false
    val number = runCatching { util.parse(value.trim(), regionCode) }.getOrNull() ?: return false
    return util.isValidNumberForRegion(number, regionCode)
  }

  /** Store contact numbers in the unambiguous E.164 form, while accepting familiar local entry. */
  fun format(value: String, regionCode: String): String {
    val number = runCatching { util.parse(value.trim(), regionCode) }.getOrNull() ?: return value.trim()
    return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
  }

  fun example(regionCode: String): String =
    util.getExampleNumber(regionCode)
      ?.let { util.format(it, PhoneNumberUtil.PhoneNumberFormat.NATIONAL) }
      .orEmpty()
}

/** ISO country lists for address and phone pickers. */
object CountryDirectory {
  val addressCountries: List<AddressCountry> by lazy {
    Locale.getISOCountries()
      .map(::AddressCountry)
      .sortedBy { name(it.regionCode, Locale.ENGLISH) }
  }
  val addressRegions: Set<String> by lazy { addressCountries.mapTo(mutableSetOf()) { it.regionCode } }

  fun name(regionCode: String, locale: Locale = Locale.getDefault()): String =
    Locale.Builder().setRegion(regionCode).build().getDisplayCountry(locale).ifBlank { regionCode }

  /**
   * Region codes in the alphabetical order of their names in [locale]. The collator places
   * Ägypten, Émirats and Österreich with the A, E and O, where a plain string comparison puts
   * every accented initial after Z.
   */
  fun nameOrder(locale: Locale): Comparator<String> {
    val collator = java.text.Collator.getInstance(locale)
    return Comparator { a, b -> collator.compare(name(a, locale), name(b, locale)) }
  }

  fun flag(regionCode: String): String {
    val region = regionCode.uppercase(Locale.ROOT)
    if (region.length != 2 || region.any { it !in 'A'..'Z' }) return "🌐"
    return buildString {
      region.forEach { appendCodePoint(0x1F1E6 + (it.code - 'A'.code)) }
    }
  }
}

data class SwissAddressCandidate(
  val street: String,
  val flatNumber: String,
  val postalCode: String,
  val city: String,
  val canton: SwissCanton? = null,
) {
  val label: String get() = "$street $flatNumber, $postalCode $city"
}

fun SessionIdentity.hasRequiredFields(): Boolean =
  fullName.any(Char::isLetter) &&
    addressCountry in CountryDirectory.addressRegions &&
    (postalCode.isBlank() || !usesSwissAddress || postalCode.matches(Regex("\\d{4}"))) &&
    (phone.isBlank() || InternationalPhone.isValid(phone, phoneRegion))

/**
 * A small, cancellable client for swisstopo's official building-address search. The query is
 * intentionally sent only after three characters and never written to disk outside the protocol.
 */
object SwissAddressLookup {
  data class Result(val matches: List<SwissAddressCandidate> = emptyList(), val failed: Boolean = false)
  private const val endpoint = "https://api3.geo.admin.ch/rest/services/ech/SearchServer"
  private val addressLabel = Regex("^(.+?)\\s+(\\d+[\\p{L}\\-/]*)\\s+(\\d{4})\\s+(.+)$")

  suspend fun search(query: String): List<SwissAddressCandidate> = searchResult(query).matches

  suspend fun searchResult(query: String): Result = withContext(Dispatchers.IO) {
    if (query.trim().length < 3) return@withContext Result()
    runCatching {
      val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
      val url = URL("$endpoint?searchText=$encoded&type=locations&origins=address")
      val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 4_000
        readTimeout = 4_000
        requestMethod = "GET"
        useCaches = false
      }
      try {
        val body = connection.inputStream.use { input ->
          val bytes = java.io.ByteArrayOutputStream()
          val buffer = ByteArray(8192)
          while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= 1024 * 1024)
            bytes.write(buffer, 0, count)
          }
          bytes.toString(Charsets.UTF_8.name())
        }
        Result(parse(body))
      } finally { connection.disconnect() }
    }.getOrElse { Result(failed = true) }
  }

  internal fun parse(body: String): List<SwissAddressCandidate> {
    val results = Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
    return results.mapNotNull { result ->
      val attrs = result.jsonObject["attrs"]?.jsonObject ?: return@mapNotNull null
      if (attrs["origin"]?.jsonPrimitive?.content != "address") return@mapNotNull null
      val label = attrs["label"]?.jsonPrimitive?.content
        ?.replace(Regex("<[^>]*>"), "")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?: return@mapNotNull null
      val match = addressLabel.matchEntire(label) ?: return@mapNotNull null
      val canton = attrs["detail"]?.jsonPrimitive?.content
        ?.trim()
        ?.substringAfterLast(' ')
        ?.let(SwissCanton::fromCode)
      SwissAddressCandidate(
        street = match.groupValues[1],
        flatNumber = match.groupValues[2],
        postalCode = match.groupValues[3],
        city = match.groupValues[4],
        canton = canton,
      )
    }.distinct().take(6)
  }
}
