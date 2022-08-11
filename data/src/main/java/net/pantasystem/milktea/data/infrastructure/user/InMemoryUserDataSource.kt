package net.pantasystem.milktea.data.infrastructure.user


import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.model.AddResult
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.model.user.UserDataSource
import net.pantasystem.milktea.model.user.UserNotFoundException
import net.pantasystem.milktea.model.user.UsersState
import net.pantasystem.milktea.model.user.nickname.UserNickname
import net.pantasystem.milktea.model.user.nickname.UserNicknameRepository
import javax.inject.Inject

class InMemoryUserDataSource @Inject constructor(
    loggerFactory: Logger.Factory?,
    private val userNicknameRepository: UserNicknameRepository,
) : UserDataSource {
    private val logger = loggerFactory?.create("InMemoryUserDataSource")

    private var userMap = mapOf<User.Id, User>()

    private val usersLock = Mutex()

    private val _state = MutableStateFlow(UsersState())
    val state: StateFlow<UsersState>
        get() = _state


    private var listeners = setOf<UserDataSource.Listener>()

    override fun addEventListener(listener: UserDataSource.Listener) {
        this.listeners = listeners.toMutableSet().apply {
            add(listener)
        }
    }

    override fun removeEventListener(listener: UserDataSource.Listener) {
        this.listeners = listeners.toMutableSet().apply {
            remove(listener)
        }
    }

    override suspend fun add(user: User): AddResult {
        return createOrUpdate(user).also {
            if (it == AddResult.Created) {
                publish(UserDataSource.Event.Created(user.id, user))
            } else if (it == AddResult.Updated) {
                publish(UserDataSource.Event.Updated(user.id, user))
            }
        }

    }

    override suspend fun addAll(users: List<User>): List<AddResult> {
        return users.map {
            add(it)
        }
    }

    override suspend fun get(userId: User.Id): User {
        return usersLock.withLock {
            userMap[userId]
        } ?: throw UserNotFoundException(userId)
    }

    override suspend fun getIn(accountId: Long, serverIds: List<String>): List<User> {
        val userIds = serverIds.map {
            User.Id(accountId, it)
        }
        return usersLock.withLock {
            userIds.mapNotNull {
                userMap[it]
            }
        }
    }

    override suspend fun get(accountId: Long, userName: String, host: String?): User {
        return usersLock.withLock {
            userMap.filterKeys {
                it.accountId == accountId
            }.map {
                it.value
            }.firstOrNull {
                it.userName == userName && (it.host == host || host.isNullOrBlank())
            } ?: throw UserNotFoundException(null)
        }
    }

    override suspend fun remove(user: User): Boolean {
        return usersLock.withLock {
            val map = userMap.toMutableMap()
            val result = map.remove(user.id)
            userMap = map
            result
        }?.also {
            publish(UserDataSource.Event.Removed(user.id))
        } != null


    }

    private suspend fun createOrUpdate(argUser: User): AddResult {
        val nickname = runCatching {
            userNicknameRepository.findOne(
                UserNickname.Id(argUser.userName, argUser.host)
            )
        }.getOrNull()
        val user = when (argUser) {
            is User.Detail -> argUser.copy(nickname = nickname)
            is User.Simple -> argUser.copy(nickname = nickname)
        }
        usersLock.withLock {
            val u = userMap[user.id]
            if (u == null) {
                userMap = userMap.toMutableMap().also { map ->
                    map[user.id] = user
                }
                return AddResult.Created
            }
            when {
                user is User.Detail -> {
                    userMap = userMap.toMutableMap().also { map ->
                        map[user.id] = user
                    }
                }
                u is User.Detail -> {
                    // RepositoryのUserがDetailで与えられたUserがSimpleの時Simpleと一致する部分のみ更新する
                    userMap = userMap.toMutableMap().also { map ->
                        map[user.id] = u.copy(
                            name = user.name,
                            userName = user.userName,
                            avatarUrl = user.avatarUrl,
                            emojis = user.emojis,
                            isCat = user.isCat,
                            isBot = user.isBot,
                            host = user.host
                        )
                    }
                }
                else -> {
                    userMap = userMap.toMutableMap().also { map ->
                        map[user.id] = user
                    }
                }
            }

            return AddResult.Updated
        }
    }

    override suspend fun all(): List<User> {
        return userMap.values.toList()
    }

    override fun observe(userId: User.Id): Flow<User> {
        return _state.map {
            it.get(userId)
        }.filterNotNull()
    }

    override fun observeIn(accountId: Long, serverIds: List<String>): Flow<List<User>> {
        val userIds = serverIds.map {
            User.Id(accountId, it)
        }
        return _state.map { state ->
            userIds.mapNotNull {
                state.get(it)
            }
        }
    }

    override fun observe(acct: String): Flow<User> {
        return _state.mapNotNull {
            it.get(acct)
        }
    }

    override fun observe(userName: String, host: String?, accountId: Long?): Flow<User?> {
        return state.map { state ->
            state.usersMap.values.filter { user ->
                accountId == null || accountId == user.id.accountId
            }.firstOrNull {
                it.userName == userName && it.host == host
            }
        }
    }

    private fun publish(e: UserDataSource.Event) {
        _state.value = _state.value.copy(
            usersMap = userMap
        )
        logger?.debug("publish events:$e")
        listeners.forEach { listener ->
            listener.on(e)
        }
    }
}