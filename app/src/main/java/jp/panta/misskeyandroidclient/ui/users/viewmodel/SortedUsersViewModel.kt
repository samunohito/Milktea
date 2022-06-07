package jp.panta.misskeyandroidclient.ui.users.viewmodel

import androidx.lifecycle.*
import jp.panta.misskeyandroidclient.di.module.getNoteDataSourceAdder
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.pantasystem.milktea.api.misskey.users.RequestUser
import net.pantasystem.milktea.data.infrastructure.toUser
import net.pantasystem.milktea.model.user.User
import java.io.Serializable

@Suppress("UNCHECKED_CAST")
@FlowPreview
@ExperimentalCoroutinesApi
class SortedUsersViewModel(
    val miCore: MiCore,
    type: Type?,
    orderBy: UserRequestConditions?
) : ViewModel() {
    private val orderBy: UserRequestConditions = type?.conditions ?: orderBy!!

    val logger = miCore.loggerFactory.create("SortedUsersViewModel")


    private val noteDataSourceAdder = miCore.getNoteDataSourceAdder()

    class Factory(
        val miCore: MiCore,
        val type: Type?,
        private val orderBy: UserRequestConditions?
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SortedUsersViewModel(
                miCore,
                type,
                orderBy
            ) as T
        }
    }

    data class UserRequestConditions(
        val origin: RequestUser.Origin?,
        val sort: String?,
        val state: RequestUser.State?
    ) : Serializable {
        fun toRequestUser(i: String): RequestUser {
            return RequestUser(
                i = i,
                origin = origin?.origin,
                sort = sort,
                state = state?.state
            )
        }
    }

    enum class Type(val conditions: UserRequestConditions) {
        TRENDING_USER(
            UserRequestConditions(
                origin = RequestUser.Origin.LOCAL,
                sort = RequestUser.Sort().follower().asc(),
                state = RequestUser.State.ALIVE
            )
        ),
        USERS_WITH_RECENT_ACTIVITY(
            UserRequestConditions(
                origin = RequestUser.Origin.LOCAL,
                sort = RequestUser.Sort().updatedAt().asc(),
                state = null
            )
        ),
        NEWLY_JOINED_USERS(
            UserRequestConditions(
                origin = RequestUser.Origin.LOCAL,
                sort = RequestUser.Sort().createdAt().asc(),
                state = RequestUser.State.ALIVE
            )
        ),

        REMOTE_TRENDING_USER(
            UserRequestConditions(
                origin = RequestUser.Origin.REMOTE,
                sort = RequestUser.Sort().follower().asc(),
                state = RequestUser.State.ALIVE
            )
        ),
        REMOTE_USERS_WITH_RECENT_ACTIVITY(
            UserRequestConditions(
                origin = RequestUser.Origin.COMBINED,
                sort = RequestUser.Sort().updatedAt().asc(),
                state = RequestUser.State.ALIVE
            )
        ),
        NEWLY_DISCOVERED_USERS(
            UserRequestConditions(
                origin = RequestUser.Origin.COMBINED,
                sort = RequestUser.Sort().createdAt().asc(),
                state = null
            )
        ),

    }

    private val userIds = MutableStateFlow<List<User.Id>>(emptyList())


    val users = miCore.getUserDataSource().state.flatMapLatest { state ->
        userIds.map { list ->
            list.mapNotNull {
                state.get(it) as? User.Detail
            }
        }
    }.flowOn(Dispatchers.IO).asLiveData()


    val isRefreshing = MutableLiveData<Boolean>()

    fun loadUsers() {

        val account = miCore.getAccountStore().currentAccount
        val i = account?.getI(miCore.getEncryption())

        if (i == null) {
            isRefreshing.value = false
            return
        } else {
            isRefreshing.value = true
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                miCore.getMisskeyAPIProvider().get(account).getUsers(orderBy.toRequestUser(i))
                    .body()
            }
                .map {
                    it?.map { dto ->
                        dto.pinnedNotes?.map { noteDTO ->
                            noteDataSourceAdder.addNoteDtoToDataSource(account, noteDTO)
                        }
                        dto.toUser(account, true).also { u ->
                            miCore.getUserDataSource().add(u)
                        }
                    }?.map { u ->
                        u.id
                    } ?: emptyList()
                }.onFailure { t ->
                    logger.error("ユーザーを取得しようとしたところエラーが発生しました", t)
                }.onSuccess {
                    userIds.value = it
                }
            isRefreshing.postValue(false)
        }


    }


}