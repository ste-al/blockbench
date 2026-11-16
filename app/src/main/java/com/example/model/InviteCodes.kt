package com.example.model

import java.security.SecureRandom
import java.util.Locale
import java.util.Random

/**
 * Block invite codes. Knowing a code is all it takes to join a block (and so to see its feed), and
 * anyone signed in may look a code up, so the code has to be hard to guess: three letters taken
 * from the block's name for recognisability, then five characters from a secure random source.
 * That is ~28 million possibilities per prefix, where the previous "three letters + three digits"
 * scheme left 900 and made collisions between blocks with similar names routine.
 */
object InviteCodes {
  const val MIN_LENGTH = 4
  const val MAX_LENGTH = 12

  private const val PREFIX_LENGTH = 3
  private const val RANDOM_LENGTH = 5

  // No 0/O or 1/I/L: codes are read out over the fence and typed by hand.
  private const val RANDOM_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

  private val secureRandom by lazy { SecureRandom() }

  /** Upper-cases [raw] and drops everything that cannot be part of a code (spaces, dashes, ...). */
  fun normalize(raw: String): String =
    raw.uppercase(Locale.US).filter { it in 'A'..'Z' || it in '0'..'9' }

  /** Matches the `inviteCode` pattern in firestore.rules; older six-character codes still pass. */
  fun isWellFormed(code: String): Boolean =
    code.length in MIN_LENGTH..MAX_LENGTH && code.all { it in 'A'..'Z' || it in '0'..'9' }

  fun generate(circleName: String, random: Random = secureRandom): String {
    val prefix =
      circleName
        .uppercase(Locale.US)
        .filter { it in 'A'..'Z' }
        .take(PREFIX_LENGTH)
        .padEnd(PREFIX_LENGTH, 'B')
    val suffix =
      buildString(RANDOM_LENGTH) {
        repeat(RANDOM_LENGTH) { append(RANDOM_ALPHABET[random.nextInt(RANDOM_ALPHABET.length)]) }
      }
    return prefix + suffix
  }
}
