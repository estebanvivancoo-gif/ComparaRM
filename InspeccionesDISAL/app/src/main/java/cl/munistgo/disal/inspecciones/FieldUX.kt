package cl.munistgo.disal.inspecciones

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val blue=Color(0xFF0D568A)
private val blueDark=Color(0xFF123C60)
private val hintColor=Color(0xFF687681)
private val red=Color(0xFFC83A3A)

@Composable
fun networkConnected():State<Boolean> {
    val context=LocalContext.current
    val available=remember {mutableStateOf(false)}
    DisposableEffect(context){
        val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun current():Boolean {
            val active=cm.activeNetwork ?: return false
            return cm.getNetworkCapabilities(active)?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        }
        available.value=current()
        val callback=object:ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network:Network){available.value=current()}
            override fun onLost(network:Network){available.value=current()}
            override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities){
                available.value=current()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose {cm.unregisterNetworkCallback(callback)}
    }
    return available
}

@Composable
fun StorageStatusBanner(connected:Boolean){
    val background=if(connected)Color(0xFFE7F0F8) else Color(0xFFFFEBC1)
    val ink=if(connected)blueDark else Color(0xFF8B5607)
    val text=if(connected)
        "ALMACENAMIENTO LOCAL · Los datos no se sincronizan"
        else "SIN CONEXIÓN · Las visitas guardadas permanecen en este teléfono"
    Surface(color=background) {
        Text(text,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=9.dp),
            color=ink,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun HomeUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
           onNew:()->Unit,onOpen:(String)->Unit,onHistory:()->Unit) {
    val month=Metrics.compute(inspections,findings,YearMonth.now())
    val pending=findings.count{it.status!="CERRADO"}
    val overdue=findings.count(Metrics::overdue)
    Scaffold(containerColor=Color(0xFFF8F9FA),
        floatingActionButton={
            ExtendedFloatingActionButton(
                onClick=onNew,
                containerColor=blue,contentColor=Color.White,
                icon={Text("+",style=MaterialTheme.typography.titleLarge)},
                text={Text("Nueva inspección")}
            )
        }
    ){inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp),
            contentPadding=PaddingValues(top=12.dp,bottom=98.dp)){
            item {
                Text("Inicio / Visitas",fontWeight=FontWeight.Bold,color=blueDark,
                    style=MaterialTheme.typography.titleLarge)
                Text("Indicadores del mes · "+YearMonth.now(),color=hintColor)
            }
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    DashboardStat("Visitas",month.inspections.toString(),blue,Modifier.weight(1f))
                    DashboardStat("Cumplimiento",month.compliance?.let{
                        String.format(Locale("es","CL"),"%.0f %%",it)
                    } ?: "N/D",blue,Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    DashboardStat("Pendientes",pending.toString(),Color(0xFFA36B08),Modifier.weight(1f))
                    DashboardStat("Vencidos",overdue.toString(),red,Modifier.weight(1f))
                }
            }
            item {Text("Visitas recientes",fontWeight=FontWeight.Bold,color=blueDark)}
            items(inspections.take(5),key={it.id}){inspection ->
                val related=findings.filter{it.inspectionId==inspection.id}
                Card(modifier=Modifier.fillMaxWidth().clickable{onOpen(inspection.id)},
                    shape=RoundedCornerShape(14.dp),
                    colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(inspection.center.ifBlank{"Centro sin registrar"},
                            fontWeight=FontWeight.Bold,color=blueDark)
                        Text(inspection.title)
                        Text(Instant.ofEpochMilli(inspection.visitedAt)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("es","CL"))),
                            color=hintColor,style=MaterialTheme.typography.bodySmall)
                        Text("Hallazgos: "+related.size+" · Pendientes: "+
                            related.count{it.status!="CERRADO"},color=blue)
                        Text("Revisar informe →",fontWeight=FontWeight.SemiBold,color=blue)
                    }
                }
            }
            if(inspections.isEmpty())item {
                Text("No hay visitas registradas. Pulsa + Nueva inspección.",color=hintColor,
                    modifier=Modifier.padding(12.dp))
            }
            item {
                OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth()){
                    Text("Ir al historial y métricas")
                }
            }
        }
    }
}

@Composable
fun HistoryMetricsUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
    onOpen:(String)->Unit,onNew:()->Unit){
    var section by remember{mutableStateOf("history")}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=section=="history",onClick={section="history"},
                label={Text("Historial")})
            FilterChip(selected=section=="monthly",onClick={section="monthly"},
                label={Text("Reporte mensual")})
        }
        Box(Modifier.fillMaxWidth().weight(1f)){
            if(section=="history")HistoryScreen(inspections,findings,onOpen,onNew)
            else MonthlyScreen(inspections,findings)
        }
    }
}

@Composable
fun ProfileUX(){
    val context=LocalContext.current
    val preferences=remember{context.getSharedPreferences("inspector_profile",Context.MODE_PRIVATE)}
    var name by remember{mutableStateOf(preferences.getString("name","").orEmpty())}
    var role by remember{mutableStateOf(
        preferences.getString("role","Ingeniero en Prevención de Riesgos").orEmpty())}
    var saved by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Perfil / Configuración",fontWeight=FontWeight.Bold,color=blueDark,
            style=MaterialTheme.typography.titleLarge)
        Section("Datos predeterminados del inspector"){
            LabelField("Nombre completo",name,{name=it;saved=false})
            LabelField("Cargo",role,{role=it;saved=false})
            Button(onClick={
                preferences.edit().putString("name",name.trim())
                    .putString("role",role.trim()).apply()
                saved=true
            },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){
                Text("Guardar perfil en este teléfono")
            }
            if(saved)Text("Guardado localmente para nuevas inspecciones.",
                color=Color(0xFF237A4E))
        }
        Section("Trabajo sin conexión"){
            Text("Esta aplicación trabaja sin Internet. Los datos de inspecciones, fotografías y firmas se guardan en el teléfono, incluso si hay señal.")
            Text("No existe sincronización automática ni respaldo institucional en esta versión.",
                color=hintColor)
        }
        Section("Protección de la información"){
            Text("Versión piloto: utilizar datos ficticios hasta aprobar los controles de privacidad y acceso institucional.")
            Text("Las firmas manuscritas no constituyen firma electrónica avanzada.",
                color=hintColor)
        }
    }
}
