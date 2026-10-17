package com.pusula.service.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.pusula.service.core.GuideReadOnlyGate
import com.pusula.service.data.model.guide.*
import com.pusula.service.ui.admin.*
import com.pusula.service.ui.auth.*
import com.pusula.service.ui.customer.*
import com.pusula.service.ui.finance.FinanceScreen
import com.pusula.service.ui.profile.ProfileScreen
import com.pusula.service.ui.proposal.ProposalScreen
import com.pusula.service.ui.settings.SettingsScreen
import com.pusula.service.ui.support.SupportCenterScreen
import com.pusula.service.ui.technician.*
import java.util.UUID

val LocalGuideTour = staticCompositionLocalOf { false }
val LocalGuideFocus = staticCompositionLocalOf { "" }

/** Only registered production components receive an outline. No estimated rectangles. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.guideTourTarget(key:String):Modifier = composed {
    val focus=LocalGuideFocus.current
    val touring=LocalGuideTour.current
    if(!touring)return@composed this
    val requester=remember{BringIntoViewRequester()}
    var positioned by remember{mutableStateOf(false)}
    LaunchedEffect(focus,positioned) { if(touring&&focus==key&&positioned)requester.bringIntoView() }
    if(touring&&key==focus) this.bringIntoViewRequester(requester).onGloballyPositioned{positioned=true}
        .border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(14.dp)) else this
}

@Composable fun rememberGuideListState(targets:Map<String,Int>):LazyListState {
    val state=rememberLazyListState();val focus=LocalGuideFocus.current;val touring=LocalGuideTour.current
    // layoutInfo changes on every measure/scroll. Observe only item-count
    // changes; observing the full object here creates a remeasure loop.
    val count by remember(state){derivedStateOf{state.layoutInfo.totalItemsCount}}
    LaunchedEffect(focus,targets,touring,count) {
        if(touring&&count>0)state.scrollToItem((targets[focus]?:0).coerceIn(0,count-1))
    }
    return state
}

/** Dialog forms are rendered in the inert screen layer during a tour, never in a separate touchable window. */
@Composable fun TourAwareAlertDialog(onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,
    modifier:Modifier=Modifier,dismissButton:(@Composable ()->Unit)?=null,
    title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null) {
    if(LocalGuideTour.current) Surface(modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            title?.invoke();Box(Modifier.guideTourTarget("customer-fields")){text?.invoke()};Box(Modifier.guideTourTarget("create-save")){confirmButton()};dismissButton?.invoke()
        }
    } else AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,modifier=modifier,dismissButton=dismissButton,title=title,text=text)
}

@Composable fun GuideTourHost(topic:GuideTopic,index:Int,features:Map<String,Boolean>,role:String,
    onNext:()->Unit,onBack:()->Unit,onClose:()->Unit) {
    var lease by remember { mutableStateOf<UUID?>(null) }
    val parent=checkNotNull(LocalViewModelStoreOwner.current)
    // Do not reuse the account screen's business ViewModels or pending submissions.
    val owner=remember {
        object:ViewModelStoreOwner,HasDefaultViewModelProviderFactory {
            override val viewModelStore=ViewModelStore()
            override val defaultViewModelProviderFactory=(parent as HasDefaultViewModelProviderFactory).defaultViewModelProviderFactory
            override val defaultViewModelCreationExtras=(parent as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras
        }
    }
    DisposableEffect(Unit) {
        val id=GuideReadOnlyGate.begin();lease=id
        onDispose { owner.viewModelStore.clear();GuideReadOnlyGate.end(id) }
    }
    if(lease!=null) CompositionLocalProvider(LocalViewModelStoreOwner provides owner,LocalGuideTour provides true) {
        GuideTourContent(topic,index,features,role,onNext,onBack,onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GuideTourContent(topic:GuideTopic,index:Int,features:Map<String,Boolean>,role:String,
    onNext:()->Unit,onBack:()->Unit,onClose:()->Unit) {
    val step=topic.steps[GuideCursor(topic.steps.size,index).boundedIndex]
    val route=step.tour
    val vm:TicketViewModel=hiltViewModel()
    val state by vm.uiState.collectAsState()
    val session by vm.sessionManager.state.collectAsState()
    var selectedTicket by remember { mutableStateOf<Long?>(null) }
    var selectedCustomer by remember { mutableStateOf<com.pusula.service.data.model.CustomerDTO?>(null) }
    val locked=topic.requiredFeature?.let { features[it]!=true }?:false
    val needsTicket=route.needsTicket(role)&&selectedTicket==null
    val needsCustomer=route.needsCustomer(role)&&selectedCustomer==null
    val identity=remember { Pair(session.companyId,session.token) }
    LaunchedEffect(session.companyId,session.token) { if(identity!=Pair(session.companyId,session.token))onClose() }
    LaunchedEffect(route.screen) {
        if(!locked) {
            if(needsTicket)vm.loadTickets()
            if(needsCustomer||route.screen=="ticketCreate")vm.loadCustomers()
        }
    }
    GuideTourChrome(topic,index,locked||needsTicket||needsCustomer,onNext,onBack,onClose) { padding ->
        if(locked) Column(Modifier.padding(padding).padding(24.dp)){Text("Paket veya yetki gerekli");Text("Tur erişim hakkınızı değiştirmez.")}
        else if(needsTicket||needsCustomer) Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Kayıt seçin · yalnızca görüntülenecek",style=MaterialTheme.typography.titleMedium)
            Text("Tur hiçbir kaydı değiştirmez. İncelemek istediğiniz mevcut kaydı siz seçin.")
            if(state.loading||state.customersLoading)CircularProgressIndicator()
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={if(needsTicket)vm.loadTickets() else vm.loadCustomers()}){Text("Tekrar Dene")} }
            if(needsTicket)state.tickets.sortedByDescending{it.id}.forEach { ticket ->
                OutlinedButton(onClick={selectedTicket=ticket.id},modifier=Modifier.fillMaxWidth().testTag("guide.context.ticket.${ticket.id}")){Text("#${ticket.id} · ${ticket.customerName.orEmpty()}")}
            } else state.customers.forEach { customer -> OutlinedButton(onClick={selectedCustomer=customer},modifier=Modifier.fillMaxWidth()){Text(customer.name)} }
            if(!state.loading&&!state.customersLoading&&state.error==null&&(if(needsTicket)state.tickets.isEmpty() else state.customers.isEmpty()))Text("Uygun kayıt bulunamadı. Tur için yeni kayıt oluşturmanız gerekmez; başka bir konu seçebilirsiniz.")
        } else CompositionLocalProvider(LocalGuideFocus provides route.target) {
            Box(Modifier.fillMaxSize().padding(padding).testTag("guide.real.screen.${route.screen}")) {
                Box(Modifier.fillMaxSize().guideTourTarget("content").clearAndSetSemantics{}.pointerInput(Unit) {
                    awaitEachGesture { do { val event=awaitPointerEvent(PointerEventPass.Initial);event.changes.forEach{it.consume()} } while(event.changes.any{it.pressed}) }
                }) {
                    key(route.screen,selectedTicket,selectedCustomer?.id) {
                        GuideProductionScreen(route.screen,role,vm,state,selectedTicket,selectedCustomer)
                    }
                }
            }
        }
    }
    // Registered after child handlers so device Back always returns within the tour.
    BackHandler { if(index>0)onBack() else onClose() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GuideTourChrome(topic:GuideTopic,index:Int,blocked:Boolean,onNext:()->Unit,onBack:()->Unit,onClose:()->Unit,
    content:@Composable (PaddingValues)->Unit) {
    val step=topic.steps[GuideCursor(topic.steps.size,index).boundedIndex]
    Scaffold(modifier=Modifier.fillMaxSize().testTag("guide.screen"),
        topBar={TopAppBar(title={Text("Salt okunur tur",style=MaterialTheme.typography.titleSmall)},actions={TextButton(onClick=onClose,modifier=Modifier.testTag("guide.close")){Text("Kapat")}})},
        bottomBar={Surface(tonalElevation=4.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${index+1} / ${topic.steps.size}",modifier=Modifier.testTag("guide.step.progress"),style=MaterialTheme.typography.labelMedium)
                Text(step.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("guide.step.title"))
                Text(step.body,modifier=Modifier.heightIn(max=140.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    OutlinedButton(onClick=onBack,enabled=index>0,modifier=Modifier.testTag("guide.back")){Text("Geri")}
                    Button(onClick=onNext,enabled=!blocked,modifier=Modifier.testTag("guide.next")){Text(if(index==topic.steps.lastIndex)"Bitir" else "Devam")}
                }
            }
        }},content=content)
}

@Composable private fun GuideProductionScreen(screen:String,role:String,vm:TicketViewModel,state:TicketUiState,
    selectedTicket:Long?,selectedCustomer:com.pusula.service.data.model.CustomerDTO?) {
                        when(screen) {
                            "home" -> if(role=="TECHNICIAN")TicketListScreen(onOpenTicket={}) else AdminDashboardScreen(onLogout={},onOpenRadar={},onOpenOperationFilter={})
                            "customers" -> CustomerScreen()
                            "customerForm" -> CustomerFormDialog(null,false,{}, {_,_,_,_->})
                            "customerDetail" -> TicketListScreen(onOpenTicket={},tourCustomerId=selectedCustomer?.id)
                            "customerContact" -> if(role=="TECHNICIAN"&&selectedTicket!=null)TourTicket(selectedTicket,vm) else CustomerScreen(tourCustomer=selectedCustomer)
                            "tickets" -> TicketListScreen(onOpenTicket={})
                            "ticketCreate" -> CreateTicketFromOperationDialog(state.customers,state.technicians,state.customersLoading,false,false,{}, {}, {_,_,_,_->}, {_,_,_,_,_->})
                            "ticketDetail","reschedule" -> selectedTicket?.let{TourTicket(it,vm)}
                            "collection" -> selectedTicket?.let{CollectionScreen(it,{}, {},vm)}
                            "photos" -> selectedTicket?.let{ServicePhotoScreen(it,{}, {},vm)}
                            "signature" -> selectedTicket?.let{SignatureScreen(it,{}, {},vm)}
                            "photosArchive" -> ServiceQualityScreen(onBack={})
                            "catalog","parts" -> CatalogScreen()
                            "proposals" -> ProposalScreen()
                            "finance","expenses","fixedExpenses" -> FinanceScreen()
                            "accounts" -> FinanceScreen(tourTab="Cari")
                            "organizations" -> selectedTicket?.let{CollectionScreen(it,{}, {},vm)}
                            "reports" -> FinanceScreen(tourTab="Rapor")
                            "profit" -> ProfitAnalysisScreen(onBack={})
                            "team" -> SettingsScreen(vm.sessionManager,{}, {})
                            "vehicles" -> SettingsScreen(vm.sessionManager,{}, {},tourTab="Araçlar")
                            "company" -> SettingsScreen(vm.sessionManager,{}, {},tourTab="Firma")
                            "radar" -> FieldRadarScreen(onBack={})
                            "notifications" -> if(role=="TECHNICIAN")TicketListScreen(onOpenTicket={}) else AdminNotificationsScreen(hiltViewModel(),{})
                            "plans" -> if(role=="TECHNICIAN")ProfileScreen(vm.sessionManager,{}, {}) else PlanUpgradeScreen(onBack={})
                            "login" -> LoginScreen(AuthUiState(),{_,_,_->},{},{})
                            "register" -> RegisterScreen(AuthUiState(),{_,_,_,_->},{},{})
                            "support" -> SupportCenterScreen(onClose={})
                            "supportForm" -> SupportCenterScreen(onClose={},tourComposer=true)
                            else -> ProfileScreen(vm.sessionManager,{}, {})
                        }
}
@Composable private fun TourTicket(id:Long,vm:TicketViewModel) {
    TicketDetailScreen(id,{}, {}, {}, {}, {}, {},vm)
}
