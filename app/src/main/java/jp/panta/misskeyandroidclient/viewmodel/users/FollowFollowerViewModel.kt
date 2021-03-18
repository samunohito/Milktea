package jp.panta.misskeyandroidclient.viewmodel.users

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.panta.misskeyandroidclient.api.users.RequestUser
import jp.panta.misskeyandroidclient.api.users.toUser
import jp.panta.misskeyandroidclient.model.Encryption
import jp.panta.misskeyandroidclient.model.account.Account
import jp.panta.misskeyandroidclient.api.v10.MisskeyAPIV10
import jp.panta.misskeyandroidclient.api.v10.RequestFollowFollower
import jp.panta.misskeyandroidclient.api.v11.MisskeyAPIV11
import jp.panta.misskeyandroidclient.gettters.NoteRelationGetter
import jp.panta.misskeyandroidclient.model.api.MisskeyAPI
import jp.panta.misskeyandroidclient.model.users.User
import jp.panta.misskeyandroidclient.model.users.UserDataSource
import jp.panta.misskeyandroidclient.util.eventbus.EventBus
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class FollowFollowerViewModel(
    val userId: User.Id,
    val type: Type,
    private val miCore: MiCore,
) : ViewModel(), ShowUserDetails{
    @Suppress("UNCHECKED_CAST")
    class Factory(
        val userId: User.Id,
        val type: Type,
        val miCore: MiCore,
    ) : ViewModelProvider.Factory{
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return FollowFollowerViewModel(userId, type, miCore) as T
        }

    }

    enum class Type{
        FOLLOWING,
        FOLLOWER
    }

    interface Paginator {
        suspend fun next(): List<User.Detail>
        suspend fun init()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    class DefaultPaginator(
        val account: Account,
        val misskeyAPI: MisskeyAPIV11,
        val userId: User.Id,
        val type: Type,
        val encryption: Encryption,
        val noteRelationGetter: NoteRelationGetter,
        val userDataSource: UserDataSource

    ) : Paginator{

        private val lock = Mutex()
        private val api = if(type == Type.FOLLOWER) misskeyAPI::followers else misskeyAPI::following
        private var nextId: String? = null

        override suspend fun next(): List<User.Detail> {
            lock.withLock {
                val res = api.invoke(RequestUser(
                    account.getI(encryption),
                    userId = userId.id,
                    untilId = nextId
                )).execute().body()
                    ?: return emptyList()
                nextId = res.last().id
                return res.mapNotNull {
                    it.followee ?: it.follower
                }.map { userDTO ->
                    userDTO.pinnedNotes?.forEach { noteDTO ->
                        noteRelationGetter.get(account, noteDTO)
                    }
                    (userDTO.toUser(account, true) as User.Detail).also {
                        userDataSource.add(it)
                    }
                }
            }
        }
        override suspend fun init() {
            lock.withLock {
                nextId = null
            }
        }
    }


    @Suppress("BlockingMethodInNonBlockingContext")
    class V10Paginator(
        val account: Account,
        val misskeyAPIV10: MisskeyAPIV10,
        val userId: User.Id,
        val type: Type,
        val encryption: Encryption,
        val noteRelationGetter: NoteRelationGetter,
        val userDataSource: UserDataSource
    ) : Paginator{
        private val lock = Mutex()
        private var nextId: String? = null
        private val api = if(type == Type.FOLLOWER) misskeyAPIV10::followers else misskeyAPIV10::following

        override suspend fun next(): List<User.Detail> {
            lock.withLock {
                val res = api.invoke(
                    RequestFollowFollower(
                        i = account.getI(encryption),
                        cursor = nextId,
                        userId = userId.id
                    )
                ).execute().body() ?: return emptyList()
                nextId = res.next
                return res.users.map { userDTO ->
                    userDTO.pinnedNotes?.forEach {
                        noteRelationGetter.get(account, it)
                    }
                    (userDTO.toUser(account, true) as User.Detail).also {
                        userDataSource.add(it)
                    }
                }
            }
        }
        override suspend fun init() {
            lock.withLock {
                nextId = null
            }
        }
    }




    val logger = miCore.loggerFactory.create("FollowFollowerViewModel")

    val accountRepository = miCore.getAccountRepository()
    val userRepository = miCore.getUserRepository()
    private val misskeyAPIProvider = miCore.getMisskeyAPIProvider()
    private val encryption = miCore.getEncryption()
    private val noteRelationGetter = miCore.getGetters().noteRelationGetter
    private val userDataSource: UserDataSource = miCore.getUserDataSource()

    val isInitializing = MutableLiveData<Boolean>(false)


    val users = MutableLiveData<List<UserViewData>>()
    private var mUsers: List<UserViewData> = emptyList()
        set(value) {
            field = value
            users.postValue(value)
        }

    private var mIsLoading: Boolean = false



    fun loadInit() = viewModelScope.launch(Dispatchers.IO) {
        isInitializing.postValue(true)
        getPaginator().init()
        mUsers = emptyList()
        loadOld().join()
        isInitializing.postValue(false)

    }



    fun loadOld() = viewModelScope.launch (Dispatchers.IO){
        if(mIsLoading) return@launch
        mIsLoading = true
        runCatching {
            val list = getPaginator().next().map {
                UserViewData(it, miCore, viewModelScope, Dispatchers.IO)
            }.toMutableList()
            list.addAll(0, mUsers)
            mUsers = list
        }.onFailure {

        }
        mIsLoading = false

        isInitializing.postValue(false)
    }


    val showUserEventBus = EventBus<User.Id>()

    override fun show(userId: User.Id) {
        showUserEventBus.event = userId
    }


    private var mAccount: Account? = null
    private val accountLock = Mutex()
    private var mPaginator: Paginator? = null
    private suspend fun getPaginator(): Paginator {
        accountLock.withLock {
            if(mPaginator != null){
                mPaginator
            }

            if(mAccount == null) {
                mAccount = accountRepository.get(userId.accountId)
            }
            mPaginator = mAccount?.let { account ->
                val api = misskeyAPIProvider.get(account.instanceDomain)
                if(api is MisskeyAPIV10){
                    V10Paginator(account, api, userId, type, encryption, noteRelationGetter, userDataSource)
                }else{
                    DefaultPaginator(account, api as MisskeyAPIV11, userId, type, encryption, noteRelationGetter, userDataSource)
                }
            }
            require(mPaginator != null)
            return mPaginator!!
        }
    }

}