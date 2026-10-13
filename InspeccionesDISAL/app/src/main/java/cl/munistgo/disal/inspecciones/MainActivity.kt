package cl.munistgo.disal.inspecciones

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DisalApp() }
    }
}
private val teal=Color(0xFF126C70)
private val navy=Color(0xFF163743)
private val gray=Color(0xFF647580)
private val dateFormat=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun date(millis:Long):String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)

@Composable
private fun LabelField(label:String,value:String,onValue:(String)->Unit,multiline:Boolean=false) {
    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},
        modifier=Modifier.fillMaxWidth(),singleLine=!multiline,
        minLines=if(multiline)2 else 1)
}

@Composable
private fun Section(title:String,content:@Composable ColumnScope.()->Unit) {
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,fontWeight=FontWeight.Bold,color=teal)
            content()
        }
    }
}

@Composable
fun DisalApp() {
    val context=LocalContext.current
    val dao=remember { InspectionDatabase.get(context).dao() }
    val scope=rememberCoroutineScope()
    val inspections by dao.watchInspections().collectAsState(initial=emptyList())
    val findings by dao.watchAllFindings().collectAsState(initial=emptyList())
    var screen by remember { mutableStateOf("history") }
    var selectedId by remember {mutableStateOf<String?>(null)}
    var selectedFinding by remember {mutableStateOf<String?>(null)}

    MaterialTheme(colorScheme=lightColorScheme(primary=teal,secondary=navy,background=Color(0xFFF0F5F6))) {
        Column(Modifier.fillMaxSize()) {
            Surface(color=navy) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("INSPECCIONES DISAL",fontWeight=FontWeight.Bold,color=Color.White)
                    Text("Higiene y Seguridad Laboral · Piloto sin conexión",color=Color(0xFFE1E9EC))
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Historial" to "history","Inspección" to "detail","Mensual" to "monthly").forEach { (label,page) ->
                    OutlinedButton(onClick={screen=page},enabled=page!="detail"||selectedId!=null) {Text(label)}
                }
            }
            when(screen) {
                "history" -> HistoryScreen(inspections,findings,
                    onOpen={selectedId=it;screen="detail"},
                    onNew={
                        val new=InspectionEntity()
                        scope.launch { dao.saveInspection(new);selectedId=new.id;screen="detail" }
                    })
                "detail" -> {
                    val current=inspections.firstOrNull {it.id==selectedId}
                    if(current!=null) DetailScreen(current,findings.filter{it.inspectionId==current.id},dao,
                        onFinding={selectedFinding=it;screen="finding"},
                        onSign={screen="sign"},
                        onBack={screen="history"})
                    else Text("Selecciona una inspección del historial.",Modifier.padding(16.dp))
                }
                "finding" -> {
                    val current=inspections.firstOrNull {it.id==selectedId}
                    if(current!=null) FindingScreen(current.id,
                        findings.firstOrNull {it.id==selectedFinding},dao,
                        onBack={screen="detail"})
                }
                "sign" -> {
                    val current=inspections.firstOrNull {it.id==selectedId}
                    if(current!=null) SignScreen(current,dao,onBack={screen="detail"})
                }
                "monthly" -> MonthlyScreen(inspections,findings)
            }
        }
    }
}

@Composable
private fun HistoryScreen(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
    onOpen:(String)->Unit,onNew:()->Unit) {
    var centerFilter by remember {mutableStateOf("")}
    var monthFilter by remember {mutableStateOf("")}
    var statusFilter by remember {mutableStateOf("Todos")}
    val filtered=inspections.filter { inspection ->
        val rows=findings.filter {it.inspectionId==inspection.id}
        val pending=rows.any {it.status!="CERRADO"}
        val overdue=rows.any{Metrics.overdue(it)}
        val closed=rows.isNotEmpty()&&rows.all {it.status=="CERRADO"}
        inspection.center.contains(centerFilter,ignoreCase=true) &&
            (monthFilter.isBlank() ||
                Instant.ofEpochMilli(inspection.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString().startsWith(monthFilter)) &&
            (statusFilter=="Todos" || (statusFilter=="Pendientes"&&pending) ||
                (statusFilter=="Vencidos"&&overdue) || (statusFilter=="Cerrados"&&closed))
    }
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Button(onClick=onNew,modifier=Modifier.fillMaxWidth()){Text("+ Nueva inspección")}
        LabelField("Filtrar por centro",centerFilter,{centerFilter=it})
        LabelField("Fecha de visita (AAAA-MM)",monthFilter,{monthFilter=it})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Todos","Pendientes","Vencidos","Cerrados").forEach { option->
                FilterChip(selected=statusFilter==option,onClick={statusFilter=option},label={Text(option)})
            }
        }
        Text("Historial local · "+filtered.size+" inspecciones",fontWeight=FontWeight.Bold)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(filtered,key={it.id}){ ins ->
                val rows=findings.filter {it.inspectionId==ins.id}
                val bad=rows.count(Metrics::overdue)
                Card(Modifier.fillMaxWidth().clickable {onOpen(ins.id)},
                    colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(ins.center.ifBlank {"Centro sin registrar"},fontWeight=FontWeight.Bold,color=navy)
                        Text(ins.title)
                        Text(date(ins.visitedAt),color=gray)
                        Text("Hallazgos: "+rows.size+" · Vencidos: "+bad+" · Pendientes: "+rows.count {it.status!="CERRADO"},color=teal)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(inspection:InspectionEntity,findings:List<FindingEntity>,dao:InspectionDao,
    onFinding:(String?)->Unit,onSign:()->Unit,onBack:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var title by remember(inspection.id){mutableStateOf(inspection.title)}
    var center by remember(inspection.id){mutableStateOf(inspection.center)}
    var area by remember(inspection.id){mutableStateOf(inspection.area)}
    var inspector by remember(inspection.id){mutableStateOf(inspection.inspector)}
    var role by remember(inspection.id){mutableStateOf(inspection.inspectorRole)}
    var conclusions by remember(inspection.id){mutableStateOf(inspection.conclusions)}
    var participantName by remember(inspection.id){mutableStateOf("")}
    var participantRole by remember(inspection.id){mutableStateOf("")}
    var rut by remember(inspection.id){mutableStateOf("")}
    var refresh by remember {mutableIntStateOf(0)}
    var participants by remember(inspection.id){mutableStateOf(emptyList<ParticipantEntity>())}
    LaunchedEffect(inspection.id,refresh) {participants=dao.participants(inspection.id)}
    var message by remember {mutableStateOf("")}
    val current=inspection.copy(title=title,center=center,area=area,inspector=inspector,inspectorRole=role,conclusions=conclusions)

    fun save() {scope.launch {dao.saveInspection(current);message="Datos guardados localmente."}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Section("A. Datos generales") {
            Text("Visita: "+date(inspection.visitedAt),color=gray)
            LabelField("Título / tipo de inspección",title,{title=it})
            LabelField("Centro o establecimiento",center,{center=it})
            LabelField("Área / sector",area,{area=it})
            LabelField("Inspector responsable",inspector,{inspector=it})
            LabelField("Cargo inspector",role,{role=it})
            Button(onClick={save()},modifier=Modifier.fillMaxWidth()) {Text("Guardar encabezado")}
        }
        Section("Participantes / acompañantes") {
            for(p in participants)Text(p.name+" | "+p.role+" | "+p.rut)
            LabelField("Nombre",participantName,{participantName=it})
            LabelField("Cargo",participantRole,{participantRole=it})
            LabelField("RUT",rut,{rut=it})
            Button(onClick={
                if(participantName.isNotBlank()){
                    scope.launch{
                        dao.addParticipant(ParticipantEntity(inspectionId=inspection.id,
                            name=participantName.trim(),role=participantRole.trim(),rut=rut.trim()))
                        participantName="";participantRole="";rut="";refresh++
                    }
                }
            }){Text("Añadir participante")}
        }
        Section("B. Hallazgos · "+findings.size) {
            Button(onClick={save();onFinding(null)},modifier=Modifier.fillMaxWidth()){Text("+ Agregar hallazgo y fotografía")}
            for((index,found) in findings.withIndex()) {
                HorizontalDivider()
                Text("Hallazgo "+(index+1)+" · "+found.risk+" · "+found.status,fontWeight=FontWeight.Bold)
                Text(found.unsafeCondition.ifBlank {"Sin descripción"})
                Text("Plazo: "+found.deadlineIso+" · "+(if(Metrics.overdue(found))"VENCIDO" else "Vigente"),color=gray)
                TextButton(onClick={save();onFinding(found.id)}) {Text("Revisar, cerrar o reabrir")}
            }
        }
        Section("C. Conclusiones y firmas") {
            LabelField("Conclusiones del inspector",conclusions,{conclusions=it},multiline=true)
            Button(onClick={save();onSign()},modifier=Modifier.fillMaxWidth()){Text("Capturar firmas en pantalla")}
            if(inspection.inspectorSignature.isNotBlank())Text("Firma del inspector registrada")
            if(inspection.receiverSignature.isNotBlank())Text("Firma del receptor registrada")
        }
        Section("D. Informe PDF institucional") {
            Button(onClick={
                scope.launch{
                    dao.saveInspection(current)
                    message="Generando PDF…"
                    try{
                        val people=dao.participants(inspection.id)
                        val rows=dao.findings(inspection.id)
                        val file=withContext(Dispatchers.IO){Documents.inspectionPdf(context,current,people,rows)}
                        Documents.share(context,file,"Inspección de Higiene y Seguridad · "+center)
                        message="Informe generado. Revisa el documento antes de enviarlo."
                    }catch(e:Exception){message="No se pudo generar PDF: "+e.message}
                }
            },modifier=Modifier.fillMaxWidth()){Text("Generar PDF y enviar / compartir")}
        }
        if(message.isNotBlank())Text(message,color=teal)
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Volver al historial")}
    }
}

@Composable
private fun FindingScreen(inspectionId:String,existing:FindingEntity?,dao:InspectionDao,onBack:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val original=remember(existing?.id){existing?:FindingEntity(inspectionId=inspectionId)}
    var location by remember(original.id){mutableStateOf(original.location)}
    var unsafe by remember(original.id){mutableStateOf(original.unsafeCondition)}
    var hazard by remember(original.id){mutableStateOf(original.hazard)}
    var risk by remember(original.id){mutableStateOf(original.risk)}
    var regulation by remember(original.id){mutableStateOf(original.regulation)}
    var action by remember(original.id){mutableStateOf(original.correctiveAction)}
    var implementer by remember(original.id){mutableStateOf(original.implementationOwner)}
    var deadline by remember(original.id){mutableStateOf(original.deadlineIso)}
    var verifier by remember(original.id){mutableStateOf(original.verificationOwner)}
    var status by remember(original.id){mutableStateOf(original.status)}
    var photoPath by remember(original.id){mutableStateOf(original.photoPath)}
    var note by remember(original.id){mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var capturePath by remember {mutableStateOf("")}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if(success)photoPath=capturePath
        else error="La captura fue cancelada."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Section("B. Registro del hallazgo") {
            Button(onClick={
                try {
                    val dir=File(context.filesDir,"photos").apply {mkdirs()}
                    val file=File(dir,UUID.randomUUID().toString()+".jpg")
                    capturePath=file.absolutePath
                    val uri=FileProvider.getUriForFile(context,"cl.munistgo.disal.inspecciones.files",file)
                    camera.launch(uri)
                }catch(e:Exception){error="Cámara no disponible: "+e.message}
            },modifier=Modifier.fillMaxWidth()){Text(if(photoPath.isBlank())"Tomar fotografía" else "Reemplazar fotografía")}
            if(photoPath.isNotBlank())Text("Foto registrada en almacenamiento privado",color=teal)
            LabelField("Ubicación dentro del recinto",location,{location=it})
            LabelField("Descripción de condición insegura",unsafe,{unsafe=it},multiline=true)
            LabelField("Peligro identificado",hazard,{hazard=it},multiline=true)
            Text("Evaluación del riesgo",fontWeight=FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                listOf("LEVE","MODERADO","CRITICO").forEach {value->
                    FilterChip(selected=risk==value,onClick={risk=value},label={Text(value)})
                }
            }
            LabelField("Normativa legal aplicable (citar artículo)",regulation,{regulation=it})
            LabelField("Medida correctiva / solución propuesta",action,{action=it},multiline=true)
            LabelField("Responsable implementación",implementer,{implementer=it})
            LabelField("Fecha límite (AAAA-MM-DD)",deadline,{deadline=it})
            LabelField("Responsable verificación y control",verifier,{verifier=it})
            Text("Estado de avance",fontWeight=FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("PENDIENTE","PROCESO","CERRADO").forEach {value->
                    FilterChip(selected=status==value,onClick={status=value},label={Text(value)})
                }
            }
            if(existing!=null) {
                LabelField("Observación de seguimiento / verificación",note,{note=it},multiline=true)
                Text("Cambios de estado registrados en bitácora.",color=gray)
            }
            if(error.isNotBlank())Text(error,color=Color.Red)
            Button(onClick={
                val valid=deadline.isBlank()||runCatching{LocalDate.parse(deadline)}.isSuccess
                if(!valid)error="La fecha debe usar AAAA-MM-DD."
                else if(unsafe.isBlank())error="Ingresa la descripción de la condición insegura."
                else {
                    scope.launch{
                        val changed=status!=original.status
                        val closed=when {
                            status=="CERRADO" && original.status!="CERRADO" -> System.currentTimeMillis()
                            status!="CERRADO" -> null
                            else -> original.closedAt
                        }
                        dao.saveFinding(original.copy(location=location,photoPath=photoPath,
                            unsafeCondition=unsafe,hazard=hazard,risk=risk,regulation=regulation,
                            correctiveAction=action,implementationOwner=implementer,
                            deadlineIso=deadline,verificationOwner=verifier,
                            status=status,closedAt=closed))
                        if(changed)dao.addEvent(FindingEventEntity(findingId=original.id,
                            previousStatus=original.status,newStatus=status,note=note))
                        onBack()
                    }
                }
            },modifier=Modifier.fillMaxWidth()) {Text("Guardar hallazgo / seguimiento")}
        }
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Volver")}
    }
}

@Composable
private fun SignScreen(inspection:InspectionEntity,dao:InspectionDao,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    var recipient by remember(inspection.id){mutableStateOf(inspection.receiverName)}
    var inspectorPad by remember {mutableStateOf<SignaturePad?>(null)}
    var receiverPad by remember {mutableStateOf<SignaturePad?>(null)}
    var message by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Firmas manuscritas (no son firma electrónica avanzada).",color=gray)
        Section("Firma del inspector") {
            Text(inspection.inspector.ifBlank{"Nombre del inspector pendiente"})
            AndroidView(factory={SignaturePad(it).also{pad->inspectorPad=pad}},
                modifier=Modifier.fillMaxWidth().height(150.dp).border(1.dp,teal))
            TextButton(onClick={inspectorPad?.clear()}){Text("Borrar trazo del inspector")}
        }
        Section("Firma del receptor") {
            LabelField("Nombre del encargado receptor",recipient,{recipient=it})
            AndroidView(factory={SignaturePad(it).also{pad->receiverPad=pad}},
                modifier=Modifier.fillMaxWidth().height(150.dp).border(1.dp,teal))
            TextButton(onClick={receiverPad?.clear()}){Text("Borrar trazo del receptor")}
        }
        Button(onClick={
            scope.launch {
                val a=inspectorPad?.save().orEmpty()
                val b=receiverPad?.save().orEmpty()
                dao.saveInspection(inspection.copy(
                    inspectorSignature=a.ifBlank{inspection.inspectorSignature},
                    receiverSignature=b.ifBlank{inspection.receiverSignature},
                    receiverName=recipient))
                message="Firmas guardadas."
                onBack()
            }
        },modifier=Modifier.fillMaxWidth()){Text("Guardar firmas")}
        if(message.isNotBlank())Text(message)
        OutlinedButton(onClick=onBack){Text("Volver")}
    }
}

@Composable
private fun MonthlyScreen(inspections:List<InspectionEntity>,findings:List<FindingEntity>) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var periodText by remember {mutableStateOf(YearMonth.now().toString())}
    val month=runCatching{YearMonth.parse(periodText)}.getOrNull()
    val stats=month?.let{Metrics.compute(inspections,findings,it)}
    var message by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Section("F. Reporte mensual de cumplimiento"){
            LabelField("Mes del informe (AAAA-MM)",periodText,{periodText=it})
            if(stats==null) {
                Text("Introduce un mes válido. Ejemplo: 2026-10",color=Color.Red)
            }else {
                Text("Inspecciones realizadas: "+stats.inspections)
                Text("Hallazgos detectados: "+stats.findings)
                Text("Leves / moderados / críticos: "+stats.mild+" / "+stats.moderate+" / "+stats.critical)
                HorizontalDivider()
                Text("Cerrados dentro del plazo: "+stats.onTime)
                Text("Vencidos (abiertos o cerrados fuera de plazo): "+stats.overdue)
                Text("De los vencidos, cerrados tardíamente: "+stats.closedLate)
                Text("Pendientes dentro del plazo: "+stats.stillCurrent)
                Text("Sin fecha de compromiso: "+stats.withoutDeadline)
                Text("Cumplimiento sobre casos evaluables: "+
                    (stats.compliance?.let {String.format(java.util.Locale("es","CL"),"%.1f %%",it)}?:"No calculable"),
                    fontWeight=FontWeight.Bold,color=teal)
                Text("Cohorte: inspecciones efectuadas en el mes. Denominador: cierres oportunos y vencidos; los plazos vigentes y sin fecha se reportan aparte.",color=gray)
                Button(onClick={
                    scope.launch {
                        try{
                            val file=withContext(Dispatchers.IO){Documents.monthlyPdf(context,stats,inspections,findings)}
                            Documents.share(context,file,"Consolidado mensual DISAL "+periodText)
                        }catch(e:Exception){message=e.message.orEmpty()}
                    }
                },modifier=Modifier.fillMaxWidth()){Text("Generar y compartir PDF mensual")}
                OutlinedButton(onClick={
                    scope.launch{
                        try {
                            val file=withContext(Dispatchers.IO){Documents.monthlyCsv(context,month,inspections,findings)}
                            Documents.share(context,file,"Hallazgos mensuales DISAL "+periodText)
                        }catch(e:Exception){message=e.message.orEmpty()}
                    }
                },modifier=Modifier.fillMaxWidth()){Text("Exportar CSV mensual")}
                if(message.isNotBlank())Text(message,color=gray)
            }
        }
    }
}
