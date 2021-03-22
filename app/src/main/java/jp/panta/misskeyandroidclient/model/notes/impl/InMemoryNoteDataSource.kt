package jp.panta.misskeyandroidclient.model.notes.impl

import jp.panta.misskeyandroidclient.Logger
import jp.panta.misskeyandroidclient.model.AddResult
import jp.panta.misskeyandroidclient.model.notes.Note
import jp.panta.misskeyandroidclient.model.notes.NoteNotFoundException
import jp.panta.misskeyandroidclient.model.notes.NoteDataSource
import jp.panta.misskeyandroidclient.model.users.User
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryNoteDataSource(
    loggerFactory: Logger.Factory
): NoteDataSource{

    val logger = loggerFactory.create("InMemoryNoteRepository")

    private val notes = HashMap<Note.Id, Note>()

    private val mutex = Mutex()
    private val listenersLock = Mutex()

    private val listeners = mutableSetOf<NoteDataSource.Listener>()

    override fun addEventListener(listener: NoteDataSource.Listener): Unit = runBlocking {
        listenersLock.withLock {
            listeners.add(listener)
        }
    }


    override suspend fun get(noteId: Note.Id): Note {
        mutex.withLock{
            return notes[noteId]
                ?: throw NoteNotFoundException(noteId)
        }
    }

    /**
     * @param note 追加するノート
     * @return ノートが新たに追加されるとtrue、上書きされた場合はfalseが返されます。
     */
    override suspend fun add(note: Note): AddResult {
        mutex.withLock{
            val n = this.notes[note.id]
            if(n != null && n.instanceUpdatedAt != note.instanceUpdatedAt){
                return AddResult.CANCEL
            }
            this.notes[note.id] = note
            note.updated()

            return if(n == null){
                publish(NoteDataSource.Event.Created(note.id, note))
                AddResult.CREATED
            } else {
                publish(NoteDataSource.Event.Updated(note.id, note))
                AddResult.UPDATED
            }
        }
    }

    override suspend fun addAll(notes: List<Note>): List<AddResult> {
        return notes.map{
            this.add(it)
        }
    }

    /**
     * @param noteId 削除するNoteのid
     * @return 実際に削除されるとtrue、そもそも存在していなかった場合にはfalseが返されます
     */
    override suspend fun remove(noteId: Note.Id): Boolean {
        mutex.withLock{
            val n = this.notes[noteId]
            this.notes.remove(noteId)
            if(n == null){
                return false
            }
            publish(NoteDataSource.Event.Deleted(noteId = noteId))
            return true
        }
    }

    override suspend fun removeByUserId(userId: User.Id): Int {
        return mutex.withLock {
            notes.values.filter {
                it.userId == userId
            }.count {
                this.remove(it.id)
            }
        }
    }

    private fun publish(ev: NoteDataSource.Event) = runBlocking<Unit> {
        listenersLock.withLock {
            listeners.forEach {
                it.on(ev)
            }
        }
    }

}