package dev.verso.limen.ui

import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.Grade
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.Role
import dev.verso.limen.data.RoomKind
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {

  @Test
  fun everyEnumSpeaksEveryLanguage() {
    Lang.entries.forEach { lang ->
      val s = Str(lang)
      RoomKind.entries.forEach { assertTrue("$lang room $it", s.room(it).isNotBlank()) }
      ItemKey.entries.forEach { assertTrue("$lang item $it", s.item(it).isNotBlank()) }
      Grade.entries.forEach { assertTrue("$lang grade $it", s.grade(it).isNotBlank()) }
      Role.entries.forEach { assertTrue("$lang role $it", s.role(it).isNotBlank()) }
      KeyKind.entries.forEach { assertTrue("$lang key $it", s.keyKind(it).isNotBlank()) }
      DepositKind.entries.forEach { assertTrue("$lang deposit $it", s.depositKind(it).isNotBlank()) }
    }
  }

  @Test
  fun noEszettAnywhereInGerman() {
    // Swiss German writes ss. The whole class is exercised via the enums plus a sample of
    // prose lines; a full sweep lives in the source review, this is the tripwire.
    val s = Str(Lang.De)
    val sample = buildString {
      RoomKind.entries.forEach { append(s.room(it)) }
      ItemKey.entries.forEach { append(s.item(it)) }
      append(s.emptyCase).append(s.infoCharged).append(s.infoOneSided).append(s.tipOneSided())
    }
    assertTrue(!sample.contains('ß'))
  }
}
