package com.morimil.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the memory-organ Room database through SQLCipher. Existing plaintext
 * organ databases are exported through the same verified, non-destructive
 * migration path used by the canonical memory database.
 */
internal object MemoryOrganDatabaseEncryption {
    const val DATABASE_NAME = "morimil_memory_organs.db"

    fun open(context: Context): MemoryOrganDatabase {
        val appContext = context.applicationContext
        val passphrase = MemoryOrganDatabaseKeyStore(appContext).getOrCreatePassphrase()
        return try {
            openWithPassphrase(
                context = appContext,
                databaseName = DATABASE_NAME,
                passphrase = passphrase
            )
        } finally {
            passphrase.fill(0)
        }
    }

    internal fun openWithPassphrase(
        context: Context,
        databaseName: String,
        passphrase: ByteArray
    ): MemoryOrganDatabase {
        require(databaseName.isNotBlank()) { "Memory-organ database name must not be blank." }
        require(passphrase.isNotEmpty()) { "Memory-organ database passphrase must not be empty." }

        val appContext = context.applicationContext
        val databaseFile = MorimilDatabaseEncryption.prepareDatabaseFile(
            context = appContext,
            databaseName = databaseName,
            passphrase = passphrase
        )
        val factoryPassphrase = passphrase.copyOf()
        return Room.databaseBuilder(
            appContext,
            MemoryOrganDatabase::class.java,
            databaseFile.absolutePath
        )
            .openHelperFactory(SupportOpenHelperFactory(factoryPassphrase))
            .addMigrations(
                MemoryOrganDatabase.MIGRATION_1_2,
                MemoryOrganDatabase.MIGRATION_2_3,
                MemoryOrganDatabase.MIGRATION_3_4,
                MemoryOrganDatabase.MIGRATION_4_5,
                MemoryOrganDatabase.MIGRATION_5_6,
                MemoryOrganDatabase.MIGRATION_6_7
            )
            .build()
    }
}

/**
 * Owns a SQLCipher passphrase dedicated to the replaceable organ database.
 * The passphrase is wrapped by a distinct Android Keystore AES-GCM key and is
 * never regenerated when persisted key metadata cannot be decrypted.
 */
private class MemoryOrganDatabaseKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getOrCreatePassphrase(): ByteArray {
        val encodedCiphertext = preferences.getString(CIPHERTEXT_KEY, null)
        val encodedIv = preferences.getString(IV_KEY, null)
        check((encodedCiphertext == null) == (encodedIv == null)) {
            "Memory-organ database key metadata is incomplete."
        }

        if (encodedCiphertext != null && encodedIv != null) {
            return decrypt(
                ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP),
                iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            )
        }

        val randomKey = ByteArray(DATABASE_KEY_BYTES).also(SecureRandom()::nextBytes)
        val passphrase = Base64.encode(randomKey, Base64.NO_WRAP)
        randomKey.fill(0)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(requireExisting = false))
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(passphrase)
        val committed = preferences.edit()
            .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            passphrase.fill(0)
            error("Could not persist the encrypted memory-organ database key.")
        }
        return passphrase
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateWrappingKey(requireExisting = true),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        cipher.updateAAD(AAD)
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(requireExisting: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        check(!requireExisting) {
            "Android Keystore memory-organ wrapping key is missing. Refusing key regeneration."
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "morimil_memory_organs_database_wrap_v1"
        const val PREFERENCES_NAME = "morimil_memory_organs_database_security"
        const val CIPHERTEXT_KEY = "database_key_ciphertext"
        const val IV_KEY = "database_key_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val DATABASE_KEY_BYTES = 32
        val AAD: ByteArray =
            "morimil.memory.organs.database.key.v1".toByteArray(Charsets.UTF_8)
    }
}
