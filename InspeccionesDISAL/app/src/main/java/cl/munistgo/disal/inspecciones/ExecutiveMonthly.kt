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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

@Composable
fun ExecutiveMonthlyUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var monthText by remember{mutableStateOf(YearMonth.now().toString())}
    val month=runCatching{YearMonth.parse(monthText)}.getOrNull()
    val rows=month?.let{CenterCatalog.rows(context,it,inspections,findings)}.orEmpty()
    val programmed=rows.filter{it.programmed}
    val visited=programmed.count{it.visited}
    val visits=month?.let{Metrics.compute(inspections,findings,it)}
    var message by remember{mutableStateOf("")}
    val ratio=if(programmed.isEmpty())"N/D"
        else String.format("%.0f %%",100.0*visited/programmed.size)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp),
        contentPadding=PaddingValues(top=10.dp,bottom=25.dp)){
        item {
            Text("CONSOLIDADO EJECUTIVO",color=CyberTheme.cyan,
                fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
            Text("Comparativo de cobertura de visitas por establecimiento",
                color=CyberTheme.muted)
        }
        item {
            LabelField("Mes de análisis (AAAA-MM)",monthText,{monthText=it})
        }
        if(month==null) item {
            Text("Introduce un período válido, por ejemplo 2026-10.",
                color=CyberTheme.danger)
        }
        if(month!=null && visits!=null) {
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    DashboardStat("Centros previstos",programmed.size.toString(),
                        CyberTheme.cyan,Modifier.weight(1f))
                    DashboardStat("Con visita",visited.toString(),
                        CyberTheme.mint,Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    DashboardStat("Sin visita",(programmed.size-visited).toString(),
                        CyberTheme.amber,Modifier.weight(1f))
                    DashboardStat("Cobertura",ratio,
                        CyberTheme.cyan,Modifier.weight(1f))
                }
            }
            item {
                Section("RESULTADO DEL PERÍODO") {
                    Text("Informes de inspección realizados: "+visits.inspections)
                    Text("Hallazgos documentados: "+visits.findings)
                    Text("Riesgos críticos: "+visits.critical)
                    Text("Cierre oportuno de casos evaluables: "+
                        (visits.compliance?.let{String.format("%.1f %%",it)}?:"No calculable"))
                    Text("La cobertura de centros y el cierre de hallazgos son dos indicadores distintos.",
                        color=CyberTheme.muted,style=MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text("COMPARATIVO POR ESTABLECIMIENTO",
                    color=CyberTheme.cyan,fontWeight=FontWeight.Bold)
                Text("El estado «Sin visita» solo aplica a los centros incluidos en el catálogo programado.",
                    color=CyberTheme.muted,style=MaterialTheme.typography.bodySmall)
            }
            items(rows,key={it.center+":"+it.programmed}){row->
                Card(shape=RoundedCornerShape(12.dp),
                    colors=CardDefaults.cardColors(containerColor=CyberTheme.panel)){
                    Column(Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text(row.center,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,
                                color=CyberTheme.ink)
                            Spacer(Modifier.width(8.dp))
                            Text(if(row.visited)"REALIZADO" else "SIN VISITA",
                                fontWeight=FontWeight.Bold,
                                color=if(row.visited)CyberTheme.mint else CyberTheme.amber,
                                style=MaterialTheme.typography.labelMedium)
                        }
                        if(!row.programmed)Text("Visita fuera del catálogo programado",
                            color=CyberTheme.magenta,style=MaterialTheme.typography.labelSmall)
                        Text("Informes: "+row.visits.size+" · Hallazgos: "+row.findings.size+
                            " · Abiertos: "+row.pending,color=CyberTheme.muted)
                    }
                }
            }
            item {
                Section("EXPORTACIÓN PARA ALTA DIRECCIÓN") {
                    Text("El PDF contiene indicadores globales, centros visitados y no visitados, y detalle de informes por fecha.",
                        color=CyberTheme.muted,style=MaterialTheme.typography.bodySmall)
                    Button(onClick={
                        scope.launch {
                            try{
                                message="Generando consolidado..."
                                val file=withContext(Dispatchers.IO){
                                    ExecutiveReports.monthly(context,month,inspections,findings)
                                }
                                Documents.share(context,file,"Consolidado mensual ejecutivo DISAL · "+month)
                                message="PDF generado. Revisa el informe antes del envío."
                            }catch(e:Exception){message="No se pudo exportar PDF: "+e.message}
                        }
                    },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("GENERAR PDF EJECUTIVO")
                    }
                    OutlinedButton(onClick={
                        scope.launch{
                            try{
                                val file=withContext(Dispatchers.IO){
                                    ExecutiveReports.monthlyCsv(context,month,inspections,findings)
                                }
                                Documents.share(context,file,"Comparativo mensual de centros · "+month)
                                message="CSV generado."
                            }catch(e:Exception){message="No se pudo exportar CSV: "+e.message}
                        }
                    },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                        Text("Exportar comparativo CSV")
                    }
                    if(message.isNotBlank())Text(message,color=CyberTheme.muted)
                }
            }
        }
    }
}
