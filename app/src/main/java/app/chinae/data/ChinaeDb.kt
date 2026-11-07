package app.chinae.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Database(entities = [Worry::class, NoteToSelf::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ChinaeDb : RoomDatabase() {
    abstract fun worries(): WorryDao
    abstract fun notes(): NoteDao

    companion object {
        @Volatile private var instance: ChinaeDb? = null

        /** SQLCipher로 암호화한 DB. 키는 Keystore 키로 감싸 보관 (masterplan 9장) */
        fun get(context: Context): ChinaeDb = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val app = context.applicationContext
                Room.databaseBuilder(app, ChinaeDb::class.java, "chinae.db")
                    .openHelperFactory(SupportOpenHelperFactory(DbKey.passphrase(app)))
                    .build().also { instance = it }
            }
        }
    }
}

/** DB 암호. 무작위 32바이트를 Android Keystore AES 키로 암호화해 SharedPreferences에 둠 */
private object DbKey {
    private const val ALIAS = "chinae_db"
    private const val PREF = "db_key"

    fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)
        prefs.getString(PREF, null)?.let { stored ->
            val (iv, ct) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            return cipher(Cipher.DECRYPT_MODE, GCMParameterSpec(128, iv)).doFinal(ct)
        }
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val c = cipher(Cipher.ENCRYPT_MODE, null)
        val enc = listOf(c.iv, c.doFinal(key)).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
        // commit: 키를 잃으면 DB를 영영 못 열므로 디스크에 쓰인 뒤 진행
        check(prefs.edit().putString(PREF, enc).commit()) { "DB 키 저장 실패" }
        return key
    }

    private fun cipher(mode: Int, spec: GCMParameterSpec?) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (spec == null) init(mode, keystoreKey()) else init(mode, keystoreKey(), spec)
        }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
