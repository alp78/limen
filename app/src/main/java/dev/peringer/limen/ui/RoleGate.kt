package dev.peringer.limen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.CountryDirectory
import dev.peringer.limen.data.InternationalPhone
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.SessionIdentity
import dev.peringer.limen.data.SwissAddressCandidate
import dev.peringer.limen.data.SwissAddressLookup
import dev.peringer.limen.data.hasRequiredFields
import dev.peringer.limen.theme.Limen
import kotlinx.coroutines.delay

/**
 * The three actors of the form. On paper the protocol still has two sides: the agency is
 * the landlord side acting through the representative it sends, so only [role] is stored.
 */
private enum class Actor { Tenant, Landlord, Agency }

private val Actor.role: Role get() = if (this == Actor.Tenant) Role.Tenant else Role.Landlord

/** Identity and handover type are chosen together, immediately before a new session starts. */
@Composable
fun NewSessionScreen(
  vm: LimenViewModel,
  s: Str,
  entryToSeed: Protocol? = null,
  onBack: () -> Unit,
  onSaved: () -> Unit = onBack,
) {
  val savedName = vm.profileName.trim()
  var firstName by rememberSaveable { mutableStateOf(savedName.substringBefore(' ', savedName)) }
  var lastName by rememberSaveable { mutableStateOf(savedName.substringAfter(' ', "")) }
  var street by rememberSaveable { mutableStateOf(vm.profileIdentity.street) }
  var flatNumber by rememberSaveable { mutableStateOf(vm.profileIdentity.flatNumber) }
  var postalCode by rememberSaveable { mutableStateOf(vm.profileIdentity.postalCode) }
  var city by rememberSaveable { mutableStateOf(vm.profileIdentity.city) }
  var phone by rememberSaveable { mutableStateOf(vm.profileIdentity.phone) }
  var usesSwissAddress by rememberSaveable { mutableStateOf(vm.profileIdentity.usesSwissAddress) }
  var addressCountryCode by rememberSaveable { mutableStateOf(vm.profileIdentity.addressCountry) }
  var phoneRegionCode by rememberSaveable { mutableStateOf(vm.profileIdentity.phoneRegion) }
  var company by rememberSaveable { mutableStateOf(vm.profileCompany) }
  var actor by rememberSaveable {
    mutableStateOf(
      when {
        vm.myRole == Role.Landlord && vm.profileCompany.isNotBlank() -> Actor.Agency
        vm.myRole == Role.Landlord -> Actor.Landlord
        else -> Actor.Tenant
      }
    )
  }
  var kind by rememberSaveable { mutableStateOf(if (entryToSeed != null) Kind.Exit else Kind.Entry) }
  var addressVerified by rememberSaveable { mutableStateOf(false) }
  var addressSearching by remember { mutableStateOf(false) }
  var lookupFailed by remember { mutableStateOf(false) }
  var addressMatches by remember { mutableStateOf(emptyList<SwissAddressCandidate>()) }
  var focusedAddressFields by remember { mutableStateOf(emptySet<String>()) }
  val phoneCountries = remember(s.lang) { InternationalPhone.countries.sortedBy { it.label(java.util.Locale.forLanguageTag(s.lang.name.lowercase())) } }
  val addressCountries = remember(s.lang) { CountryDirectory.addressCountries.sortedBy { it.label(java.util.Locale.forLanguageTag(s.lang.name.lowercase())) } }
  val phoneCountry = phoneCountries.firstOrNull { it.regionCode == phoneRegionCode }
    ?: phoneCountries.first { it.regionCode == "CH" }
  val addressCountry = addressCountries.firstOrNull { it.regionCode == addressCountryCode }
  val sessionKind = if (entryToSeed != null) Kind.Exit else kind
  val role = actor.role
  val identity = SessionIdentity(
    firstName = firstName,
    lastName = lastName,
    company = if (actor == Actor.Agency) company else "",
    street = street,
    flatNumber = flatNumber,
    postalCode = postalCode,
    city = city,
    addressCountry = if (usesSwissAddress) "CH" else addressCountryCode,
    usesSwissAddress = usesSwissAddress,
    phone = phone,
    phoneRegion = phoneRegionCode,
  )
  // Not gated on the swisstopo round trip. The lookup returns an empty list for no network,
  // a timeout and an unknown address alike, and an inspector standing in an empty flat with no
  // signal could not start a session at all, with nothing on screen saying why. The address
  // shape is still checked by hasRequiredFields; the lookup is now a confirmation, not a gate.
  val canStart = identity.hasRequiredFields() &&
    (actor != Actor.Agency || company.isNotBlank())
  val addressQuery = listOf(street, flatNumber, postalCode, city)
    .filter { it.isNotBlank() }
    .joinToString(" ")

  val addressFieldsFocused = focusedAddressFields.isNotEmpty()

  LaunchedEffect(usesSwissAddress, addressQuery, addressVerified, addressFieldsFocused) {
    if (!usesSwissAddress || !addressFieldsFocused || addressVerified || addressQuery.trim().length < 3) {
      addressMatches = emptyList()
      addressSearching = false
      return@LaunchedEffect
    }
    delay(350)
    addressSearching = true
    val result = SwissAddressLookup.searchResult(addressQuery)
    addressMatches = result.matches
    lookupFailed = result.failed
    addressSearching = false
  }

  fun changeAddress(change: () -> Unit) {
    addressVerified = false
    addressMatches = emptyList()
    addressSearching = false
    change()
  }

  fun setAddressFieldFocused(field: String, focused: Boolean) {
    focusedAddressFields = if (focused) focusedAddressFields + field else focusedAddressFields - field
  }

  fun selectAddressMode(swiss: Boolean) {
    usesSwissAddress = swiss
    addressVerified = false
    addressMatches = emptyList()
    focusedAddressFields = emptySet()
    addressCountryCode = if (swiss) "CH" else ""
  }

  fun selectAddress(candidate: SwissAddressCandidate) {
    street = candidate.street
    flatNumber = candidate.flatNumber
    postalCode = candidate.postalCode
    city = candidate.city
    addressMatches = emptyList()
    addressVerified = true
  }

  Box(
    Modifier.fillMaxSize().background(Limen.colors.plate),
    contentAlignment = Alignment.TopCenter,
  ) {
  Column(
    Modifier
      .readable()
      .fillMaxHeight()
      .safeDrawingPadding()
      .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconKey(ButtonGlyph.Back, s.back, Modifier.width(48.dp), onPress = onBack)
      Spacer(Modifier.weight(1f))
      LangMenu(vm.lang, vm::selectLang)
      Spacer(Modifier.width(4.dp))
      ThemeToggle(vm.darkTheme, s.themeMode, vm::toggleTheme)
    }
    Column(
      Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        LimenMark(size = 94.dp)
      }
      Text(
        s.startNewSession,
        style = MaterialTheme.typography.titleMedium,
        color = Limen.colors.ink,
      )
      if (vm.saveState == "failed") Text(s.saveFailed, color = Limen.colors.dangerOutline)
      Panel(label = s.userType) {
        EqualChoiceRow(
          options = listOf(
            Actor.Tenant to s.role(Role.Tenant),
            Actor.Landlord to s.actorLandlordPrivate,
            Actor.Agency to s.actorAgency,
          ),
          current = actor,
          onSelect = { actor = it },
        )
      }
      Panel(label = s.sessionKind) {
        EqualChoiceRow(
          options = listOf(Kind.Entry to s.entry, Kind.Exit to s.exit),
          current = sessionKind,
          locked = entryToSeed != null,
        ) { kind = it }
        if (entryToSeed != null) {
          Text(
            "${s.exit} · ${entryToSeed.address.ifBlank { s.noAddress }}",
            style = MaterialTheme.typography.bodyMedium,
            color = Limen.colors.ink,
          )
        }
      }
      Panel(label = s.sessionIdentity) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (actor == Actor.Agency) {
            Field(s.companyName, company) { company = it }
          }
          Field(s.firstName, firstName) { firstName = it }
          Field(s.lastName, lastName) { lastName = it }
          Text(s.identityAddress, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          Text(s.addressType, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          EqualChoiceRow(
            options = listOf(true to s.swissAddress, false to s.foreignAddress),
            current = usesSwissAddress,
            onSelect = ::selectAddressMode,
          )
          if (!usesSwissAddress) {
            CountryDropDown(
              label = s.addressCountry,
              selectedLabel = addressCountry?.label() ?: s.selectCountry,
            options = addressCountries,
            optionKey = { it.regionCode },
            optionLabel = { it.label() },
            emptyLabel = s.countryNoMatches,
            ) { selected ->
              addressCountryCode = selected.regionCode
            }
            Text(s.foreignAddressHint, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          }
          // The suggestions sit ABOVE the fields they fill. Below them they were the first
          // thing the keyboard covered, which made the official lookup look like it had
          // returned nothing at exactly the moment it had returned everything.
          if (usesSwissAddress && addressFieldsFocused && addressSearching) {
            Text(s.addressSearching, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          }
          if (lookupFailed) Text(s.lookupFailed, color = Limen.colors.inkDim)
          if (usesSwissAddress && addressFieldsFocused) AddressMatches(addressMatches, ::selectAddress)
          if (usesSwissAddress && addressVerified) {
            Text(
              s.addressVerified,
              style = MaterialTheme.typography.labelSmall,
              color = Limen.colors.seal,
            )
          }
          Field(
            s.street,
            street,
            onFocusChanged = { setAddressFieldFocused("street", it) },
          ) {
            changeAddress { street = it }
          }
          Field(s.flatNumber, flatNumber, onFocusChanged = { setAddressFieldFocused("flat", it) }) {
            changeAddress { flatNumber = it }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
              s.postalCode,
              postalCode,
              Modifier.weight(0.35f),
              keyboardType = if (usesSwissAddress) KeyboardType.Number else KeyboardType.Text,
              onFocusChanged = { setAddressFieldFocused("postal", it) },
            ) {
              changeAddress {
                postalCode = if (usesSwissAddress) it.filter(Char::isDigit).take(4) else it
              }
            }
            Field(
              s.city,
              city,
              Modifier.weight(0.65f),
              onFocusChanged = { setAddressFieldFocused("city", it) },
            ) { changeAddress { city = it } }
          }
          if (actor == Actor.Tenant && sessionKind == Kind.Exit) {
            Text(s.tenantExitAddressHint, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          }
          CountryDropDown(
            label = s.phoneCountry,
            selectedLabel = phoneCountry.label(),
            options = phoneCountries,
            optionKey = { it.regionCode },
            optionLabel = { it.label() },
            emptyLabel = s.countryNoMatches,
          ) { selected ->
            phoneRegionCode = selected.regionCode
            phone = ""
          }
          val phoneIsValid = InternationalPhone.isValid(phone, phoneRegionCode)
          val phoneExample = InternationalPhone.example(phoneRegionCode)
          Field(
            s.phoneNumber,
            phone,
            hint = s.phoneExample(phoneExample),
            keyboardType = KeyboardType.Phone,
            isError = phone.isNotBlank() && !phoneIsValid,
          ) { phone = it }
          if (phone.isNotBlank()) {
            Text(
              if (phoneIsValid) s.validPhoneNumber else s.invalidPhoneNumber,
              style = MaterialTheme.typography.labelSmall,
              color = if (phoneIsValid) Limen.colors.positive else Limen.colors.dangerOutline,
            )
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      val start = {
        entryToSeed?.let { vm.seedExitFrom(it, role, identity) }
          ?: vm.startNewSession(role, identity, sessionKind)
      }
      // Save keeps the draft in the library and returns to it; Validate opens the record.
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionKey(ButtonGlyph.Save, s.draft, active = false, Modifier.weight(1f), enabled = canStart) {
          start()
          vm.closeFlow()
          onSaved()
        }
        ActionKey(ButtonGlyph.Confirm, s.validate, active = true, Modifier.weight(1f), enabled = canStart, onPress = start)
      }
      Spacer(Modifier.height(20.dp))
    }
  }
  }
}

@Composable
private fun AddressMatches(
  matches: List<SwissAddressCandidate>,
  onSelect: (SwissAddressCandidate) -> Unit,
) {
  if (matches.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    matches.forEach { candidate ->
      KeySurface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        onPress = { onSelect(candidate) },
      ) {
        Text(
          candidate.label,
          style = MaterialTheme.typography.bodyMedium,
          color = Limen.colors.ink,
          maxLines = 2,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
      }
    }
  }
}

/** Searchable dropdown: every supported country is available without a country-code wall of text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CountryDropDown(
  label: String,
  selectedLabel: String,
  options: List<T>,
  optionKey: (T) -> String,
  optionLabel: (T) -> String,
  emptyLabel: String,
  onSelect: (T) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val play = rememberTick()
  var query by rememberSaveable { mutableStateOf("") }
  val visibleOptions = if (query.isBlank()) {
    options
  } else {
    options.filter { option ->
      optionLabel(option).contains(query, ignoreCase = true) ||
        optionKey(option).contains(query, ignoreCase = true)
    }
  }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { next ->
      expanded = next
      if (next) query = ""
    },
  ) {
    OutlinedTextField(
      value = if (expanded) query else selectedLabel,
      onValueChange = {
        query = it
        expanded = true
      },
      readOnly = !expanded,
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
      shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
      label = { Text(label, style = MaterialTheme.typography.labelSmall) },
      singleLine = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = Limen.colors.ink),
      colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Limen.colors.actionOutline,
        unfocusedBorderColor = Limen.colors.actionOutline,
        focusedLabelColor = Limen.colors.actionOutline,
        unfocusedLabelColor = Limen.colors.inkDim,
        cursorColor = Limen.colors.actionOutline,
        focusedContainerColor = Limen.colors.input,
        unfocusedContainerColor = Limen.colors.input,
      ),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.heightIn(max = 320.dp),
    ) {
      if (visibleOptions.isEmpty()) {
        DropdownMenuItem(
          text = { Text(emptyLabel) },
          onClick = {},
          enabled = false,
        )
      } else {
        visibleOptions.forEach { option ->
          DropdownMenuItem(
            text = { Text(optionLabel(option), maxLines = 1) },
            onClick = {
              play(Tick.Key)
              onSelect(option)
              expanded = false
              query = ""
            },
          )
        }
      }
    }
  }
}

@Composable
private fun <T> EqualChoiceRow(
  options: List<Pair<T, String>>,
  current: T,
  locked: Boolean = false,
  onSelect: (T) -> Unit,
) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { (value, label) ->
      Chip(
        label = label,
        active = value == current,
        modifier = Modifier.weight(1f),
        enabled = !locked && value != current,
        selectionState = value == current,
        onPress = { onSelect(value) },
      )
    }
  }
}
