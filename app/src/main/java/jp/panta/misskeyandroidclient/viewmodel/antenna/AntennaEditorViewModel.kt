package jp.panta.misskeyandroidclient.viewmodel.antenna

import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jp.panta.misskeyandroidclient.model.I
import jp.panta.misskeyandroidclient.model.account.Account
import jp.panta.misskeyandroidclient.api.groups.GroupDTO
import jp.panta.misskeyandroidclient.model.list.UserList
import jp.panta.misskeyandroidclient.api.v12.MisskeyAPIV12
import jp.panta.misskeyandroidclient.api.v12.antenna.Antenna
import jp.panta.misskeyandroidclient.api.v12.antenna.AntennaQuery
import jp.panta.misskeyandroidclient.api.v12.antenna.AntennaToAdd
import jp.panta.misskeyandroidclient.util.eventbus.EventBus
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import jp.panta.misskeyandroidclient.viewmodel.users.UserViewData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.StringBuilder
import java.util.regex.Pattern

/**
 * @param antenna 新規作成時はnullになる
 */
class AntennaEditorViewModel (
    val account: Account,
    val miCore: MiCore,
    antenna: Antenna?
) : ViewModel(){

    @Suppress("UNCHECKED_CAST")
    class Factory(val ac: Account, val miCore: MiCore, val antenna: Antenna?) : ViewModelProvider.Factory{
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return AntennaEditorViewModel(ac, miCore, antenna) as T
        }
    }

    val antenna = MutableLiveData<Antenna?>(antenna)

    enum class Source(val remote: String){
        HOME("home"), ALL("all"), USERS("users"), LIST("list"), GROUP("group")
    }

    val name = MediatorLiveData<String>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = it?.name?: ""
        }
    }
    val source = MediatorLiveData<Source>().apply{
        addSource(this@AntennaEditorViewModel.antenna){ a ->
            Log.d("AntennaEditorVM", "antenna:$a")
            this.value = Source.values().firstOrNull {
                a?.src ==  it.remote
            }?: Source.ALL
        }
    }
    val keywords = MediatorLiveData<String>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = setupKeywords(it?.keywords)
        }
    }

    val excludeKeywords = MediatorLiveData<String>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = setupKeywords(it?.excludeKeywords)
        }
    }

    val users = MediatorLiveData<List<UserViewData>>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = it?.users?.filter{ str ->
                str.isNotBlank()
            }?.map{ userId ->
                UserViewData(userId)
            }?: emptyList()
        }

        addSource(this){
            val i = account.getI(miCore.getEncryption())?: return@addSource
            it.forEach { uvd ->
                uvd.setApi(i, miCore.getMisskeyAPI(account))
            }
        }
    }



    val userListList = MediatorLiveData<List<UserList>>().apply{
        fun loadUserListList(){

            val i = account.getI(miCore.getEncryption())
                ?: return
            miCore.getMisskeyAPI(account).userList(I(i)).enqueue(object : Callback<List<UserList>>{
                override fun onResponse(
                    call: Call<List<UserList>>,
                    response: Response<List<UserList>>
                ) {
                    this@apply.postValue(response.body())
                }

                override fun onFailure(call: Call<List<UserList>>, t: Throwable) {
                    this@apply.postValue(emptyList())
                    Log.e("AntennaEditorVM", "ユーザーリスト一覧の取得に失敗しました", t)
                }
            })
        }
        addSource(this@AntennaEditorViewModel.antenna){
            loadUserListList()
        }
        addSource(this@AntennaEditorViewModel.source){
            if(it == Source.LIST && this.value.isNullOrEmpty()){
                loadUserListList()
            }
        }
    }

    val userList = MediatorLiveData<UserList?>().apply{
        addSource(userListList){ list ->
            this.value = list.firstOrNull {
                it.id == this@AntennaEditorViewModel.antenna.value?.userListId
            }?: list.firstOrNull()
        }
    }

    val groupList = MediatorLiveData<List<GroupDTO>?>().apply{
        addSource(this@AntennaEditorViewModel.source){
            /*if(it == Source.GROUP && this.value.isNullOrEmpty()){
                miCore.getMisskeyAPI(accountRelation)
            }*/
        }
    }

    val group = MediatorLiveData<GroupDTO>().apply{
        addSource(groupList){
            this.value = it?.firstOrNull { g ->
                g.id == this@AntennaEditorViewModel.antenna.value?.userGroupId
            }?: it?.firstOrNull()
        }
    }

    /**
     * 新しいノートを通知します
     */
    val notify = MediatorLiveData<Boolean?>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = it?.notify?: false
        }
    }

    /**
     * ファイルが添付されたノートのみ
     */
    val withFile =  MediatorLiveData<Boolean?>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = it?.withFile?: false
        }
    }

    /**
     * 大文字と小文字を区別します
     */
    val caseSensitive =  MediatorLiveData<Boolean?>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = it?.caseSensitive?: false
        }
    }

    /**
     * 返信を含める
     */
    val withReplies = MediatorLiveData<Boolean>().apply{
        addSource(this@AntennaEditorViewModel.antenna){
            this.value = it?.withReplies?: false
        }
    }

    val antennaAddedStateEvent = EventBus<Boolean>()
    
    fun addRemote(){

        val i = account.getI(miCore.getEncryption())
            ?: return
        val antenna = this.antenna.value
        val api = miCore.getMisskeyAPI(account) as? MisskeyAPIV12
            ?: return
        val antennaAPI = if(antenna?.id == null){
            api::createAntenna
        }else{
            api::updateAntenna
        }
        val request = AntennaToAdd(
            i,
            antenna?.id,
            name.value?: antenna?.name?: "",
            source.value?.remote!!,
            userList.value?.id,
            null,
            toListKeywords(keywords.value?: ""),
            toListKeywords(excludeKeywords.value?: ""),
            users.value?.map{
                it.userId
            }?: emptyList(),
            caseSensitive.value?: false,
            withFile.value?: false,
            withReplies.value?: false,
            notify.value?: false

        )
        antennaAPI.invoke(request).enqueue(object : Callback<Antenna>{
            override fun onResponse(call: Call<Antenna>, response: Response<Antenna>) {
                if(response.code() in 200 until 300){
                    this@AntennaEditorViewModel.antenna.postValue(response.body())
                    antennaAddedStateEvent.event = true
                }else{
                    Log.d("AntennaViewModel", "add antenna error code:${response.code()}, errorMsg:${response.errorBody()?.string()}")
                    antennaAddedStateEvent.event = false
                }
            }

            override fun onFailure(call: Call<Antenna>, t: Throwable) {
                Log.e("AntennaEditorViewModel", "add antenna error", t)
                antennaAddedStateEvent.event = false
            }
        })
    }

    val antennaRemovedEvent = EventBus<Unit>()

    fun removeRemote(){
        val api = miCore.getMisskeyAPI(account) as? MisskeyAPIV12
        api?.deleteAntenna(
            AntennaQuery(
                i = account.getI(miCore.getEncryption())!!,
                antennaId = antenna.value?.id,
                limit = null
            )
        )?.enqueue(object : Callback<Unit>{
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                if(response.code() in 200 until 300){
                    antennaRemovedEvent.event = Unit
                }
            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Log.e("AntennaEditorViewModel", "delete error", t)
            }
        })
    }
    val selectUserEvent = EventBus<List<String>>()
    fun selectUser(){
        selectUserEvent.event = users.value?.map{
            it.userId
        }?: emptyList()
    }

    
    private fun setupKeywords(keywords: List<List<String>>?): String{
        val builder = StringBuilder()
        keywords?.forEach {  list ->
            list.forEach {  word ->
                builder.append(word)
            }
            builder.append("\n")
        }
        return builder.toString()
    }

    private fun toListKeywords(keywords: String): List<List<String>>{
        return keywords.split('\n').map{
            it.split(Pattern.compile("""[ 　]""")).filter{ str ->
                str.isNotEmpty()

            }
        }.filter{ list ->
            list.isNotEmpty()
        }
    }

    fun setUserIds(userIds: List<String>){
        users.value = userIds.map{
            UserViewData(it)
        }
    }

}