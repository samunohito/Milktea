package jp.panta.misskeyandroidclient.model.notes

import jp.panta.misskeyandroidclient.model.AddResult
import jp.panta.misskeyandroidclient.model.account.Account
import jp.panta.misskeyandroidclient.model.users.User
import kotlinx.coroutines.flow.Flow

/**
 * キャッシュやデータベースの実装の差をなくすためのRepository
 * 要するに抽象化したいだけで意味はない
 * ※またAPIを抽象化するためのものではない
 */
interface NoteRepository {

    interface Factory{
        fun create(account: Account) : NoteRepository
    }

    interface Listener {
        fun on(e: Event)
    }


    sealed class Event{
        data class Deleted(val noteId: Note.Id) : Event()
        data class Updated(val note: Note.Id): Event()
        data class Created(val note: Note.Id): Event()
    }

    var listener: Listener


    suspend fun get(noteId: Note.Id) : Note?

    suspend fun remove(noteId: Note.Id) : Boolean

    suspend fun add(note: Note) : AddResult

    suspend fun addAll(notes: List<Note>) : List<AddResult>

    /**
     * 投稿者のuserIdに基づいて削除をします
     * @param userId 対称のUser#id
     * @return 削除されたNote数
     */
    suspend fun removeByUserId(userId: User.Id) : Int

}