package cl.munistgo.disal.inspecciones

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val blue=Color(0xFF0D568A)
private val red=Color(0xFFC83A3A)
private val orange=Color(0xFF986617)
private val green=Color(0xFF237A4E)

@Composable
fun FollowupUX(
    inspections:List<InspectionEntity>,
    findings:List<FindingEntity>,
    dao:InspectionDao,
    onOpen:(String,String)->Unit
) {
    val scope=rememberCoroutineScope()
    var tab by remember {mutableStateOf("Por vencer")}
    var closing by remember {mutableStateOf<FindingEntity?>(null)}
    var note by remember {mutableStateOf("")}
    val byId=inspections.associateBy {it.id}
    val today=LocalDate.now()
    val open=findings.filter {it.status!="CERRADO"}
    val near=open.filter{
        val due=runCatching{LocalDate.parse(it.deadlineIso)}.getOrNull()
        due!=null&&!due.isBefore(today)&&!due.isAfter(today.plusDays(15))
    }
    val late=open.filter{Metrics.overdue(it)}
    val processing=open.filter{it.status=="PROCESO"}
    val closed=findings.filter{it.status=="CERRADO"}
    val rows=(when(tab){
        "Por vencer"->near
        "Vencidos"->late
        "En proceso"->processing
        "Cerrados"->closed
        else->open
    }).sortedWith(compareBy<FindingEntity>{
        runCatching {LocalDate.parse(it.deadlineIso)}.getOrNull() ?: LocalDate.MAX
    }.thenBy{it.createdAt})
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Seguimiento y cierre de actividades",fontWeight=FontWeight.Bold,
            color=blue,style=MaterialTheme.typography.titleLarge)
        Text("Plazos próximos (15 días): "+near.size+" · Vencidos: "+late.size+
            " · En proceso: "+processing.size,style=MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("Por vencer","Vencidos","En proceso","Todos","Cerrados").forEach {mode ->
                FilterChip(selected=tab==mode,onClick={tab=mode},label={Text(mode)})
            }
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp),contentPadding=PaddingValues(bottom=30.dp)) {
            items(rows,key={it.id}){row->
                val due=runCatching{LocalDate.parse(row.deadlineIso)}.getOrNull()
                val remaining=due?.let{ChronoUnit.DAYS.between(today,it)}
                val urgency=when{
                    Metrics.overdue(row)->red
                    remaining!=null && remaining<=15->orange
                    row.status=="CERRADO"->green
                    else->blue
                }
                Card(colors=CardDefaults.cardColors(containerColor=Color.White),
                    shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(byId[row.inspectionId]?.center?.ifBlank{"Centro sin registrar"}
                            ?: "Centro sin registrar",
                            fontWeight=FontWeight.Bold,color=blue)
                        Text(row.unsafeCondition.ifBlank{"Hallazgo sin descripción"})
                        Text("Riesgo: "+row.risk+" · Estado: "+row.status)
                        Text("Responsable: "+row.implementationOwner.ifBlank{"Por asignar"})
                        Text("Vence: "+row.deadlineIso.ifBlank{"Sin plazo"}+
                            (remaining?.let {" · "+(if(it<0)"Vencido hace "+(-it)+" días" else if(it==0L)"Vence hoy" else "Faltan "+it+" días")} ?: ""),
                            color=urgency,fontWeight=FontWeight.Bold)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedButton(onClick={onOpen(row.inspectionId,row.id)}){
                                Text("Revisar")
                            }
                            if(row.status!="CERRADO"){
                                Button(onClick={closing=row;note=""},colors=ButtonDefaults.buttonColors(
                                    containerColor=green)){Text("Registrar cierre")}
                            }
                        }
                    }
                }
            }
            if(rows.isEmpty()) item {
                Text("No hay hallazgos en esta categoría.",color=Color.Gray,
                    modifier=Modifier.padding(12.dp))
            }
        }
    }
    closing?.let{item ->
        AlertDialog(
            onDismissRequest={closing=null},
            title={Text("Verificación y cierre del hallazgo")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(item.unsafeCondition)
                    Text("El cierre quedará registrado con la fecha y hora actuales. Si fue una verificación anterior, edita la información en el hallazgo.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=note,onValueChange={note=it},
                        label={Text("Evidencia / observación de cierre")},
                        minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())
                }
            },
            confirmButton={
                Button(onClick={
                    scope.launch {
                        dao.saveFinding(item.copy(status="CERRADO",closedAt=System.currentTimeMillis()))
                        dao.addEvent(FindingEventEntity(findingId=item.id,previousStatus=item.status,
                            newStatus="CERRADO",note=note.trim()))
                        closing=null
                    }
                },enabled=note.isNotBlank()){Text("Confirmar cierre")}
            },
            dismissButton={TextButton(onClick={closing=null}){Text("Cancelar")}}
        )
    }
}
