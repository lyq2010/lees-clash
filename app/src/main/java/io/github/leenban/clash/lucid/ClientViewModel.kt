package io.github.leenban.clash.lucid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.leenban.clash.core.model.*
import io.github.leenban.clash.remote.Broadcasts
import io.github.leenban.clash.remote.Remote
import io.github.leenban.clash.service.remote.ILogObserver
import io.github.leenban.clash.util.withClash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import java.util.UUID

class ClientViewModel(application:Application):AndroidViewModel(application),Broadcasts.Observer {
    val repository=ClientRepository(application)
    private val mutable=MutableStateFlow(ClientState())
    val state=mutable.asStateFlow()
    private val refreshMutex=Mutex()
    private var route="home"
    private var query=""
    private var logJob:Job?=null
    private val json=DiagnosticJson.format

    fun clearError(){mutable.update{it.copy(error=null)}}
    fun error(message:String){mutable.update{it.copy(error=message,starting=false)}}
    fun starting(){mutable.update{it.copy(starting=true,error=null)}}
    fun navigate(destination:String){route=destination;query="";task { loadPage() }}
    fun search(value:String){query=value;task { refreshPage() }}

    fun task(done:()->Unit={},operation:suspend()->Unit){viewModelScope.launch{
        mutable.update{it.copy(busy=true,error=null)}
        try{operation();done()}catch(e:CancellationException){throw e}catch(e:Exception){error(e.message?:"操作失败，请重试")}finally{mutable.update{it.copy(busy=false)}}
    }}

    suspend fun refresh(){refreshMutex.withLock{
        try{
            val running=Remote.broadcasts.clashRunning
            val active=repository.active()
            val mode=repository.mode()
            val traffic=if(running)json.decodeFromString(TrafficStats.serializer(),repository.diagnostic("traffic"))else TrafficStats()
            mutable.update{it.copy(running=running,active=active,mode=mode,traffic=traffic)}
            if(route.startsWith("connections")||route.startsWith("connection/")||route.startsWith("stats"))refreshPage()
        }catch(e:CancellationException){throw e}catch(e:Exception){error(e.message?:"无法连接服务")}
    }}

    private suspend fun loadPage(){
        logJob?.cancel();logJob=null
        withClash { setLogObserver(null) }
        refreshPage()
        if(route=="logs") {
            logJob=viewModelScope.launch{withClash{setLogObserver(object:ILogObserver{
                override fun newItem(message:LogMessage){mutable.update{it.copy(logs=(it.logs+message).takeLast(1000))}}
            })}}
        }
    }

    private suspend fun refreshPage(){
        val screen=route.substringBefore('/')
        val argument=android.net.Uri.decode(route.substringAfter('/',""))
        when(screen){
            "profiles"->mutable.update{it.copy(profiles=repository.profiles())}
            "profile"->mutable.update{it.copy(editingProfile=repository.profile(argument))}
            "groups"->mutable.update{it.copy(groups=repository.groups())}
            "nodes"->mutable.update{it.copy(selectedGroup=repository.group(argument))}
            "connections"->mutable.update{it.copy(connections=json.decodeFromString(DiagnosticPage.serializer(ConnectionInfo.serializer()),repository.diagnostic("connections",query)))}
            "connection"->try{val connection=json.decodeFromString(ConnectionInfo.serializer(),repository.diagnostic("connection",id=argument));mutable.update{it.copy(connection=connection,connectionEnded=false)}}catch(e:CancellationException){throw e}catch(e:IllegalStateException){mutable.update{it.copy(connectionEnded=true)}}
            "rules"->mutable.update{it.copy(rules=json.decodeFromString(DiagnosticPage.serializer(RuleInfo.serializer()),repository.diagnostic("rules",query)))}
            "providers"->mutable.update{it.copy(providers=repository.providers())}
            "apps"->mutable.update{it.copy(apps=repository.appChoices())}
            "override","dns-settings"->mutable.update{it.copy(overrideJson=repository.overrideText())}
            "files"->mutable.update{it.copy(files=repository.files.list(argument))}
            "about"->mutable.update{it.copy(coreVersion=repository.coreVersion())}
        }
    }

    fun moreConnections(){task{val current=mutable.value.connections;if(current.nextOffset<0)return@task;val next=json.decodeFromString(DiagnosticPage.serializer(ConnectionInfo.serializer()),repository.diagnostic("connections",query,current.nextOffset));mutable.update{it.copy(connections=next.copy(items=(current.items+next.items).distinctBy{c->c.id}))}}}
    fun moreRules(){task{val current=mutable.value.rules;if(current.nextOffset<0)return@task;val next=json.decodeFromString(DiagnosticPage.serializer(RuleInfo.serializer()),repository.diagnostic("rules",query,current.nextOffset));mutable.update{it.copy(rules=next.copy(items=current.items+next.items))}}}
    fun dns(domain:String,type:String){task{val result=json.decodeFromString(DnsResult.serializer(),repository.diagnostic("dns",domain=domain,recordType=type));mutable.update{it.copy(dns=result)}}}
    fun close(id:String){task{repository.diagnostic("close",id=id);refreshPage()}}
    fun closeAll(){task{repository.diagnostic("closeAll");refreshPage()}}
    fun selectGroup(name:String,proxy:String){task{repository.select(name,proxy);refreshPage()}}
    fun health(name:String){task{repository.health(name);refreshPage()}}
    fun setMode(mode:TunnelState.Mode){task{repository.setMode(mode);refresh()}}
    fun changed(){task{refresh();refreshPage()}}
    override fun onServiceRecreated()=changed()
    override fun onStarted(){mutable.update{it.copy(starting=false)};changed()}
    override fun onStopped(cause:String?){mutable.update{it.copy(starting=false,running=false,error=cause)};changed()}
    override fun onProfileChanged()=changed()
    override fun onProfileUpdateCompleted(uuid:UUID?)=changed()
    override fun onProfileUpdateFailed(uuid:UUID?,reason:String?)=error(reason?:"配置更新失败")
    override fun onProfileLoaded()=changed()
    suspend fun detach(){logJob?.cancel();withClash{setLogObserver(null)}}
}
