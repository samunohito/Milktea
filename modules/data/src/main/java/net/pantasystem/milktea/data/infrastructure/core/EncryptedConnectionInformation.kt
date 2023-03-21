package net.pantasystem.milktea.data.infrastructure.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.util.*

/**
 * @param accountId 接続する対象のUserId
 * @param instanceBaseUrl 接続する対象のインスタンスのURL
 * @param encryptedI encrypt済みのi
 * @param viaName appログイン出ない場合はnull
 */
@Suppress("DEPRECATION")
@Entity(
    tableName = "connection_information",
    foreignKeys = [ForeignKey(childColumns = ["accountId"], parentColumns = ["id"], entity = Account::class, onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.NO_ACTION)],
    primaryKeys = ["accountId", "encryptedI", "instanceBaseUrl"]
)
@Deprecated("model.accountへ移行")
data class EncryptedConnectionInformation(
    @ColumnInfo("accountId")
    val accountId: String,

    @ColumnInfo("instanceBaseUrl")
    val instanceBaseUrl: String,

    @ColumnInfo("encryptedI")
    val encryptedI: String,

    @ColumnInfo("viaName")
    val viaName: String?,

    @ColumnInfo("createdAt")
    val createdAt: Date = Date(),

    @ColumnInfo("isDirect")
    val isDirect: Boolean = false
) {

    @ColumnInfo("updatedAt")
    var updatedAt: Date = Date()

}