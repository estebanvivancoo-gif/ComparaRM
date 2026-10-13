package cl.munistgo.disal.inspecciones

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object VisitCategories {
    val options=listOf(
        "CPHS" to "Reunión de Comité Paritario",
        "TECNICA" to "Visita técnica",
        "EMERGENCIA" to "Atención de emergencia",
        "COORDINACION" to "Gestión / coordinación",
        "OTRA" to "Otra actividad"
    )
    fun name(key:String)=options.firstOrNull{it.first==key}?.second ?: "Actividad"
}
private val dark=CyberTheme.background
private val cyan=CyberTheme.cyan
private val muted=CyberTheme.muted

@Composable
fun NewActivityChooser(onInspection:()->Unit,onVisit:(String)->Unit,onCancel:()->Unit) {
    AlertDialog(
        onDismissRequest=onCancel,
        containerColor=CyberTheme.panel,
        title={Text("REGISTRAR ACTIVIDAD",fontWeight=FontWeight.Bold,color=cyan)},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text("Elige un formulario según el objetivo de la visita. Las actividades técnicas no generan hallazgos de inspección.",color=muted)
                Button(onClick=onInspection,modifier=Modifier.fillMaxWidth().heightIn(min=51.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=cyan,contentColor=dark)){
                    Text("INSPECCIÓN DE SEGURIDAD",fontWeight=FontWeight.Bold)
                }
                HorizontalDivider()
                VisitCategories.options.forEach{(code,label)->
                    OutlinedButton(onClick={onVisit(code)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                        Text(label)
                    }
                }
            }
        },
        confirmButton={},
        dismissButton={TextButton(onClick=onCancel){Text("Cancelar")}}
    )
}

private fun stamp(time:Long):String =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

@Composable
fun AdditionalVisitForm(
    visit:AdditionalVisitEntity,
    dao:InspectionDao,
    onBack:()->Unit
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var kind by remember(visit.id){mutableStateOf(visit.kind)}
    var title by remember(visit.id){mutableStateOf(visit.title)}
    var center by remember(visit.id){mutableStateOf(visit.center)}
    var area by remember(visit.id){mutableStateOf(visit.area)}
    var whenVisited by remember(visit.id){mutableLongStateOf(visit.visitedAt)}
    var responsible by remember(visit.id){mutableStateOf(visit.responsible)}
    var participants by remember(visit.id){mutableStateOf(visit.participants)}
    var purpose by remember(visit.id){mutableStateOf(visit.purpose)}
    var summary by remember(visit.id){mutableStateOf(visit.summary)}
    var agreements by remember(visit.id){mutableStateOf(visit.agreements)}
    var owner by remember(visit.id){mutableStateOf(visit.agreementOwner)}
    var due by remember(visit.id){mutableStateOf(visit.agreementDueIso)}
    var state by remember(visit.id){mutableStateOf(visit.agreementStatus)}
    var photo by remember(visit.id){mutableStateOf(visit.photoPath)}
    var message by remember(visit.id){mutableStateOf("")}
    var showCamera by remember {mutableStateOf(false)}

    fun draft()=visit.copy(
        kind=kind,title=title,center=center,area=area,visitedAt=whenVisited,
        responsible=responsible,participants=participants,purpose=purpose,summary=summary,
        agreements=agreements,agreementOwner=owner,agreementDueIso=due,
        agreementStatus=state,photoPath=photo
    )
    fun saveDraft(andLeave:Boolean=false){
        if(center.isBlank()) {message="Indica el establecimiento o lugar de la visita.";return}
        if(title.isBlank()) {message="Indica el motivo o asunto de la visita.";return}
        if(summary.isBlank()&&purpose.isBlank()) {
            message="Indica el objetivo o una breve descripción de lo realizado.";return
        }
        if(due.isNotBlank() && runCatching{LocalDate.parse(due)}.isFailure) {
            message="La fecha de compromiso debe tener el formato AAAA-MM-DD.";return
        }
        scope.launch {
            dao.saveAdditionalVisit(draft())
            message="Visita guardada localmente."
            if(andLeave)onBack()
        }
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null)scope.launch{
            try{
                val destination=withContext(Dispatchers.IO){
                    val type=context.contentResolver.getType(uri).orEmpty()
                    require(type.startsWith("image/")){"Selecciona una imagen"}
                    val folder=File(context.filesDir,"photos").apply{mkdirs()}
                    val output=File(folder,"visit_"+UUID.randomUUID().toString()+".jpg")
                    try{
                        context.contentResolver.openInputStream(uri).use{input->
                            requireNotNull(input){"No se pudo abrir la imagen"}
                            output.outputStream().use{out->
                                var size=0L
                                val buf=ByteArray(8192)
                                while(true) {
                                    val len=input.read(buf)
                                    if(len<0)break
                                    size+=len
                                    if(size>18L*1024*1024)error("Imagen demasiado grande (máx. 18 MB)")
                                    out.write(buf,0,len)
                                }
                                check(size>0){"Archivo vacío"}
                            }
                        }
                        output
                    }catch(e:Exception){output.delete();throw e}
                }
                photo=destination.absolutePath
                message="Fotografía importada."
            }catch(e:Exception){message="Error importando imagen: "+e.message}
        }
    }
    if(showCamera){
        Dialog(onDismissRequest={showCamera=false},
            properties=DialogProperties(usePlatformDefaultWidth=false)){
            CameraHud(onCaptured={photo=it;showCamera=false;message="Fotografía capturada"},
                onCancel={showCamera=false})
        }
    }
    Scaffold(containerColor=dark,
        bottomBar={
            Surface(color=CyberTheme.panel,shadowElevation=9.dp){
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=9.dp),
                    horizontalArrangement=Arrangement.spacedBy(9.dp)){
                    OutlinedButton(onClick=onBack,modifier=Modifier.weight(1f).heightIn(min=50.dp)){
                        Text("Volver")
                    }
                    Button(onClick={saveDraft(true)},modifier=Modifier.weight(2f).heightIn(min=50.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=cyan,contentColor=dark)){
                        Text("GUARDAR VISITA",fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
    ){inside ->
        Column(Modifier.fillMaxSize().padding(inside).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("REGISTRO BREVE DE ACTIVIDAD",color=cyan,fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleLarge)
            Text("Para CPHS, emergencias, asesorías y gestiones técnicas. No exige matriz de riesgos ni firmas.",
                color=muted,style=MaterialTheme.typography.bodySmall)
            Section("1 · TIPO Y ANTECEDENTES") {
                Text("Tipo de visita",fontWeight=FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    VisitCategories.options.forEach{(id,name)->
                        FilterChip(selected=kind==id,onClick={kind=id},label={Text(name)},
                            colors=FilterChipDefaults.filterChipColors(
                                selectedContainerColor=cyan,selectedLabelColor=dark))
                    }
                }
                LabelField("Asunto / motivo de la visita",title,{title=it})
                LabelField("Establecimiento o lugar",center,{center=it})
                LabelField("Sector / unidad (opcional)",area,{area=it})
                OutlinedButton(onClick={
                    val time=Instant.ofEpochMilli(whenVisited).atZone(ZoneId.systemDefault())
                    DatePickerDialog(context,{_,year,month,day->
                        TimePickerDialog(context,{_,hour,minute->
                            whenVisited=java.time.LocalDate.of(year,month+1,day).atTime(hour,minute)
                                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        },time.hour,time.minute,true).show()
                    },time.year,time.monthValue-1,time.dayOfMonth).show()
                },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){
                    Text("FECHA Y HORA  ·  "+stamp(whenVisited)+"  ✎")
                }
                LabelField("Responsable de la visita",responsible,{responsible=it})
                LabelField("Participantes / acompañantes (opcional)",participants,{participants=it},multiline=true)
            }
            Section("2 · DESARROLLO Y RESULTADO"){
                LabelField("Objetivo / requerimiento",purpose,{purpose=it},multiline=true)
                LabelField("Descripción breve de lo realizado",summary,{summary=it},multiline=true)
                LabelField("Acuerdos, tareas y próximas acciones (opcional)",agreements,{agreements=it},multiline=true)
                if(agreements.isNotBlank()){
                    LabelField("Responsable de los acuerdos",owner,{owner=it})
                    LabelField("Fecha de seguimiento (AAAA-MM-DD)",due,{due=it})
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        listOf("15 días" to LocalDate.now().plusDays(15),
                            "1 mes" to LocalDate.now().plusMonths(1),
                            "3 meses" to LocalDate.now().plusMonths(3)).forEach{(label,date)->
                            FilterChip(selected=due==date.toString(),
                                onClick={due=date.toString()},label={Text(label)})
                        }
                    }
                    Text("Estado de acuerdos")
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        listOf("PENDIENTE","EN_PROCESO","CERRADO").forEach{value->
                            FilterChip(selected=state==value,onClick={state=value},label={
                                Text(value.replace("_"," "))})
                        }
                    }
                }else {
                    if(state!="SIN_ACUERDOS")Text("No hay acuerdos descritos. Puedes dejarlos sin seguimiento.",
                        color=muted,style=MaterialTheme.typography.bodySmall)
                    FilterChip(selected=state=="SIN_ACUERDOS",onClick={state="SIN_ACUERDOS"},
                        label={Text("Sin acuerdos pendientes")})
                }
            }
            Section("3 · EVIDENCIA FOTOGRÁFICA (OPCIONAL)"){
                Text("Una sola fotografía es suficiente; también puedes emitir el acta sin imagen.",
                    color=muted,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Button(onClick={showCamera=true},modifier=Modifier.weight(1f)){
                        Text("Cámara HUD")
                    }
                    OutlinedButton(onClick={gallery.launch("image/*")},modifier=Modifier.weight(1f)){
                        Text("Galería")
                    }
                }
                if(photo.isNotBlank()){
                    val bitmap=remember(photo){
                        BitmapFactory.decodeFile(photo,BitmapFactory.Options().apply{inSampleSize=4})
                    }
                    if(bitmap!=null){
                        androidx.compose.foundation.Image(bitmap.asImageBitmap(),
                            contentDescription="Foto adjunta",
                            modifier=Modifier.fillMaxWidth().heightIn(max=180.dp),
                            contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    TextButton(onClick={photo=""}){Text("Quitar fotografía")}
                }else Text("Sin fotografía adjunta",color=muted)
            }
            Section("4 · DOCUMENTO BREVE"){
                Text("Acta técnica formal con datos, descripción y acuerdos. La fotografía solo aparece si se adjuntó.",
                    color=muted,style=MaterialTheme.typography.bodySmall)
                Button(onClick={
                    if(center.isBlank()||title.isBlank()||(summary.isBlank()&&purpose.isBlank())) {
                        message="Completa lugar, asunto y una descripción breve antes de generar el informe."
                    }else scope.launch{
                        try{
                            dao.saveAdditionalVisit(draft())
                            val file=withContext(Dispatchers.IO){
                                SimpleVisitPdf.create(context,draft())
                            }
                            Documents.share(context,file,"Registro de actividad · "+title)
                            message="PDF preparado para revisión y envío."
                        }catch(e:Exception){message="No fue posible generar PDF: "+e.message}
                    }
                },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                    Text("GENERAR PDF BREVE")
                }
            }
            if(message.isNotBlank())Text(message,color=CyberTheme.amber)
        }
    }
}

@Composable
fun AdditionalVisitHistory(
    visits:List<AdditionalVisitEntity>,
    onOpen:(String)->Unit
){
    var filter by remember{mutableStateOf("")}
    var type by remember{mutableStateOf("TODAS")}
    val shown=visits.filter {
        (type=="TODAS"||it.kind==type) &&
        (filter.isBlank()||it.center.contains(filter,true)||
            it.title.contains(filter,true))
    }
    Column(Modifier.fillMaxSize().padding(horizontal=13.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("REUNIONES Y VISITAS TÉCNICAS",color=cyan,
            fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
        LabelField("Buscar por centro o asunto",filter,{filter=it})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=type=="TODAS",onClick={type="TODAS"},label={Text("Todas")})
            VisitCategories.options.forEach{(code,label)->
                FilterChip(selected=type==code,onClick={type=code},label={Text(label)})
            }
        }
        LazyColumn(contentPadding=PaddingValues(bottom=22.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(shown,key={it.id}){visit->
                Card(modifier=Modifier.fillMaxWidth().clickable{onOpen(visit.id)},
                    colors=CardDefaults.cardColors(containerColor=CyberTheme.panel),
                    shape=RoundedCornerShape(12.dp)){
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(VisitCategories.name(visit.kind),color=cyan,fontWeight=FontWeight.Bold)
                        Text(visit.title.ifBlank{"Sin asunto"},color=CyberTheme.ink)
                        Text(visit.center+" · "+stamp(visit.visitedAt),color=muted,
                            style=MaterialTheme.typography.bodySmall)
                        if(visit.agreements.isNotBlank())Text("Acuerdos: "+
                            visit.agreementStatus.replace("_"," "),color=CyberTheme.amber)
                        Text("Abrir registro →",color=cyan)
                    }
                }
            }
            if(shown.isEmpty())item{
                Text("No hay registros en esta categoría.",color=muted,modifier=Modifier.padding(10.dp))
            }
        }
    }
}
