package net.pantasystem.milktea.data.infrastructure.instance.db

import androidx.room.*
import net.pantasystem.milktea.model.instance.MastodonInstanceInfo

@Entity(
    tableName = "mastodon_instance_info"
)
data class MastodonInstanceInfoRecord(
    @PrimaryKey(autoGenerate = false) val uri: String,
    val title: String,
    val description: String,
    val email: String,
    val version: String,
    @Embedded(prefix = "urls_") val urls: Urls,
    @Embedded(prefix = "configuration_") val configuration: Configuration? = null,
) {
    companion object;

    data class Configuration(
        @Embedded(prefix = "statuses_") val statuses: Statuses? = null,
        @Embedded(prefix = "polls_")val polls: Polls? = null
    ) {

        data class Statuses(
            val maxCharacters: Int? = null,
            val maxMediaAttachments: Int? = null,
        )

        data class Polls(
            val maxOptions: Int? = null,
            val maxCharactersPerOption: Int? = null,
            val minExpiration: Int? = null,
            val maxExpiration: Int? = null,
        )

    }

    data class Urls(
        val streamingApi: String? = null,
    )
}


@Entity(
    tableName = "mastodon_instance_fedibird_capabilities",
    foreignKeys = [
        ForeignKey(
            parentColumns = ["uri"],
            childColumns = ["uri"],
            entity = MastodonInstanceInfoRecord::class,
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("uri")],
    primaryKeys = ["uri", "type"]
)
data class FedibirdCapabilitiesRecord(
    val type: String,
    val uri: String
)

data class MastodonInstanceInfoRelated(
    @Embedded val info: MastodonInstanceInfoRecord,
    @Relation(
        parentColumn = "uri",
        entityColumn = "uri",
        entity = FedibirdCapabilitiesRecord::class
    )
    val fedibirdCapabilities: List<FedibirdCapabilitiesRecord>?
)

fun MastodonInstanceInfoRecord.Companion.from(model: MastodonInstanceInfo): MastodonInstanceInfoRecord {
    return MastodonInstanceInfoRecord(
        uri = model.uri,
        title = model.title,
        description = model.description,
        email = model.email,
        urls = model.urls.let {
            MastodonInstanceInfoRecord.Urls(
                streamingApi = it.streamingApi
            )
        },
        version = model.version,
        configuration = model.configuration?.let { config ->
            MastodonInstanceInfoRecord.Configuration(
                statuses = config.statuses?.let {
                    MastodonInstanceInfoRecord.Configuration.Statuses(
                        maxCharacters = it.maxCharacters,
                        maxMediaAttachments = it.maxMediaAttachments,
                    )
                },
                polls = config.polls?.let {
                    MastodonInstanceInfoRecord.Configuration.Polls(
                        maxOptions = it.maxOptions,
                        maxCharactersPerOption = it.maxCharactersPerOption,
                        maxExpiration = it.maxExpiration,
                        minExpiration = it.minExpiration,
                    )
                }
            )
        }
    )
}


fun MastodonInstanceInfoRelated.toModel(): MastodonInstanceInfo {
    return MastodonInstanceInfo(
        uri = info.uri,
        title = info.title,
        description = info.description,
        email = info.email,
        urls = info.urls.let {
            MastodonInstanceInfo.Urls(
                streamingApi = it.streamingApi
            )
        },
        version = info.version,
        configuration = info.configuration?.let { config ->
            MastodonInstanceInfo.Configuration(
                statuses = config.statuses?.let {
                    MastodonInstanceInfo.Configuration.Statuses(
                        maxCharacters = it.maxCharacters,
                        maxMediaAttachments = it.maxMediaAttachments,
                    )
                },
                polls = config.polls?.let {
                    MastodonInstanceInfo.Configuration.Polls(
                        maxOptions = it.maxOptions,
                        maxCharactersPerOption = it.maxCharactersPerOption,
                        maxExpiration = it.maxExpiration,
                        minExpiration = it.minExpiration,
                    )
                }
            )
        },
        fedibirdCapabilities = fedibirdCapabilities?.map { it.type }
    )
}