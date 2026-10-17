package com.pusula.service.ui.support

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.pusula.service.core.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@HiltViewModel class SupportBadgeViewModel @Inject constructor(private val repository:SupportRepository,private val session:SessionManager):ViewModel(){
    private val mutable=MutableStateFlow(0L);val unread=mutable.asStateFlow()
    init {viewModelScope.launch{session.state.map{it.supportRevision}.distinctUntilChanged().collect{mutable.value=0}}}
    fun refresh(){viewModelScope.launch{val captured=session.state.value;val token=captured.token?:return@launch;try{if(repository.api.contact().enabled){val count=repository.api.unread("Bearer $token")["count"]?:0;if(session.state.value.supportRevision==captured.supportRevision)mutable.value=count}}catch(_:Exception){}}}
}
@Composable fun SupportEntry(onOpen:()->Unit,viewModel:SupportBadgeViewModel=hiltViewModel()) {
    val count by viewModel.unread.collectAsState();val owner=LocalLifecycleOwner.current
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)viewModel.refresh()};owner.lifecycle.addObserver(observer);viewModel.refresh();onDispose{owner.lifecycle.removeObserver(observer)}}
    LaunchedEffect(owner){while(isActive){delay(60000);if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))viewModel.refresh()}}
    TextButton(onClick=onOpen){Text("Yardım ve Destek"+if(count>0)" ($count)" else "")}
}
