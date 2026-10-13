from pathlib import Path

root=Path("InspeccionesDISAL")
base=root/"app/src/main/java/cl/munistgo/disal/inspecciones"
main=base/"MainActivity.kt"
field=base/"FieldUX.kt"
monthly=base/"ExecutiveMonthly.kt"
reports=base/"ExecutiveReports.kt"
gradle=root/"app/build.gradle.kts"

def replace_once(txt,old,new,label):
    if old not in txt:raise RuntimeError("Ancla v0.6 ausente "+label+": "+old[:90])
    return txt.replace(old,new,1)

s=main.read_text()
s=replace_once(s,
    "    val findings by dao.watchAllFindings().collectAsState(initial=emptyList())",
    "    val findings by dao.watchAllFindings().collectAsState(initial=emptyList())\n"+
    "    val additionalVisits by dao.watchAdditionalVisits().collectAsState(initial=emptyList())","Room flow")
s=replace_once(s,
    '    var selectedFinding by remember {mutableStateOf<String?>(null)}',
    '''    var selectedFinding by remember {mutableStateOf<String?>(null)}
    var selectedAdditionalId by remember {mutableStateOf<String?>(null)}
    var showNewActivityChooser by remember {mutableStateOf(false)}''',
    "App states")

anchor="    MaterialTheme(colorScheme=darkColorScheme("
code=r'''    fun newAdditional(kind:String) {
        val prefs=context.getSharedPreferences("inspector_profile",android.content.Context.MODE_PRIVATE)
        val item=AdditionalVisitEntity(
            kind=kind,
            responsible=prefs.getString("name","").orEmpty()
        )
        scope.launch {
            dao.saveAdditionalVisit(item)
            selectedAdditionalId=item.id
            screen="additionalDetail"
        }
    }

'''
s=replace_once(s,anchor,code+anchor,"newAdditional")
part='''        Scaffold(
            containerColor=CyberTheme.background'''
replacement='''        if(showNewActivityChooser){
            NewActivityChooser(
                onInspection={showNewActivityChooser=false;newInspection()},
                onVisit={kind->showNewActivityChooser=false;newAdditional(kind)},
                onCancel={showNewActivityChooser=false}
            )
        }
        Scaffold(
            containerColor=CyberTheme.background'''
s=replace_once(s,part,replacement,"chooser")
s=replace_once(s,
    '''"home" -> HomeUX(inspections,findings,
                            onNew={newInspection()},onOpen={selectedId=it;screen="detail"},
                            onHistory={tab="records";screen="records"})''',
    '''"home" -> HomeUX(inspections,findings,additionalVisits,
                            onNew={showNewActivityChooser=true},
                            onOpen={selectedId=it;screen="detail"},
                            onOpenAdditional={selectedAdditionalId=it;screen="additionalDetail"},
                            onHistory={tab="records";screen="records"})''',
    "Home wiring")
s=replace_once(s,
    '''"records" -> HistoryMetricsUX(inspections,findings,
                            onOpen={selectedId=it;screen="detail"},onNew={newInspection()})''',
    '''"records" -> HistoryMetricsUX(inspections,findings,additionalVisits,
                            onOpen={selectedId=it;screen="detail"},
                            onOpenAdditional={selectedAdditionalId=it;screen="additionalDetail"},
                            onNew={showNewActivityChooser=true})''',
    "History wiring")
s=replace_once(s,
    '''                        "detail" -> {
                            val ins=inspections.firstOrNull{it.id==selectedId}''',
    '''                        "additionalDetail" -> {
                            val record=additionalVisits.firstOrNull{it.id==selectedAdditionalId}
                            if(record==null)Text("Cargando registro...",Modifier.padding(16.dp))
                            else AdditionalVisitForm(record,dao,
                                onBack={screen=if(tab=="records")"records" else "home"})
                        }
                        "detail" -> {
                            val ins=inspections.firstOrNull{it.id==selectedId}''',
    "detail switch")
main.write_text(s)

s=field.read_text()
s=replace_once(s,
    '''fun HomeUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
           onNew:()->Unit,onOpen:(String)->Unit,onHistory:()->Unit)''',
    '''fun HomeUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
           additionalVisits:List<AdditionalVisitEntity>,
           onNew:()->Unit,onOpen:(String)->Unit,
           onOpenAdditional:(String)->Unit,onHistory:()->Unit)''',
    "Home signature")
s=replace_once(s,'text={Text("Nueva inspección")}', 'text={Text("Nueva actividad")}',"FAB")
s=replace_once(s,
    'DashboardStat("Visitas",month.inspections.toString(),blue,Modifier.weight(1f))',
    'DashboardStat("Inspecciones",month.inspections.toString(),blue,Modifier.weight(1f))',
    "dashboard inspection KPI")
s=replace_once(s,
    '''            item {
                OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth()){
                    Text("Ir al historial y métricas")
                }
            }''',
    '''            item {
                Text("OTRAS VISITAS Y REUNIONES",color=CyberTheme.cyan,
                    fontWeight=FontWeight.Bold)
                Text("Registros complementarios separados de las inspecciones de seguridad.",
                    color=CyberTheme.muted,style=MaterialTheme.typography.bodySmall)
            }
            items(additionalVisits.take(5),key={it.id}){visit->
                Card(
                    modifier=Modifier.fillMaxWidth().clickable{onOpenAdditional(visit.id)},
                    colors=CardDefaults.cardColors(containerColor=CyberTheme.panel)
                ){
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(VisitCategories.name(visit.kind),color=CyberTheme.cyan,
                            fontWeight=FontWeight.Bold)
                        Text(visit.title.ifBlank{"Registrar asunto de la actividad"})
                        Text(visit.center.ifBlank{"Establecimiento pendiente"},
                            color=CyberTheme.muted)
                        Text("Abrir registro →",color=CyberTheme.cyan)
                    }
                }
            }
            item {
                OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth()){
                    Text("Ir al historial y métricas")
                }
            }''',
    "Home additional visits cards")
s=replace_once(s,
    '''fun HistoryMetricsUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
    onOpen:(String)->Unit,onNew:()->Unit)''',
    '''fun HistoryMetricsUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,
    additionalVisits:List<AdditionalVisitEntity>,
    onOpen:(String)->Unit,onOpenAdditional:(String)->Unit,onNew:()->Unit)''',
    "History signature")
s=replace_once(s,
    '''            FilterChip(selected=section=="monthly",onClick={section="monthly"},
                label={Text("Reporte mensual")})''',
    '''            FilterChip(selected=section=="other",onClick={section="other"},
                label={Text("Otras visitas")})
            FilterChip(selected=section=="monthly",onClick={section="monthly"},
                label={Text("Reporte mensual")})''',
    "History tab")
s=replace_once(s,
    '''            if(section=="history")HistoryScreen(inspections,findings,onOpen,onNew)
            else ExecutiveMonthlyUX(inspections,findings)''',
    '''            when(section) {
                "history" -> HistoryScreen(inspections,findings,onOpen,onNew)
                "other" -> AdditionalVisitHistory(additionalVisits,onOpenAdditional)
                else -> ExecutiveMonthlyUX(inspections,findings,additionalVisits)
            }''',
    "History body")
field.write_text(s)

s=monthly.read_text()
s=replace_once(s,
    'fun ExecutiveMonthlyUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>){',
    'fun ExecutiveMonthlyUX(inspections:List<InspectionEntity>,findings:List<FindingEntity>,otherVisits:List<AdditionalVisitEntity>){',
    "monthly signature")
s=replace_once(s,
    '''    val visits=month?.let{Metrics.compute(inspections,findings,it)}
    var message''',
    '''    val visits=month?.let{Metrics.compute(inspections,findings,it)}
    val complementary=otherVisits.filter{row->
        month!=null && row.center.isNotBlank() && row.title.isNotBlank() &&
            (row.summary.isNotBlank() || row.purpose.isNotBlank()) &&
            java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(row.visitedAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate())==month
    }
    var message''',
    "monthly complementary state")
s=replace_once(s,
    '''            item {
                Text("COMPARATIVO POR ESTABLECIMIENTO"''',
    '''            item {
                Section("ACTIVIDADES COMPLEMENTARIAS") {
                    Text("Total de reuniones y visitas técnicas: "+complementary.size,
                        fontWeight=FontWeight.Bold,color=CyberTheme.cyan)
                    VisitCategories.options.forEach{(code,name)->
                        val amount=complementary.count{it.kind==code}
                        if(amount>0)Text(name+": "+amount)
                    }
                    Text("Estas actividades se informan por separado y no se cuentan como inspecciones de seguridad.",
                        color=CyberTheme.muted,style=MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text("COMPARATIVO POR ESTABLECIMIENTO"''',
    "monthly supplemental box")
s=replace_once(s,
    '''ExecutiveReports.monthly(context,month,inspections,findings)''',
    '''ExecutiveReports.monthly(context,month,inspections,findings,complementary)''',
    "monthly pdf call")
s=replace_once(s,
    '''ExecutiveReports.monthlyCsv(context,month,inspections,findings)''',
    '''ExecutiveReports.monthlyCsv(context,month,inspections,findings,complementary)''',
    "monthly csv call")
monthly.write_text(s)

s=reports.read_text()
s=replace_once(s,
    '''fun monthly(context:Context,period:YearMonth,inspections:List<InspectionEntity>,
        findings:List<FindingEntity>):File{''',
    '''fun monthly(context:Context,period:YearMonth,inspections:List<InspectionEntity>,
        findings:List<FindingEntity>,otherVisits:List<AdditionalVisitEntity> = emptyList()):File{''',
    "exec monthly signature")
s=replace_once(s,
    '''            w.section("4. Criterios de lectura")
            w.paragraph("«Realizada»''',
    '''            w.section("4. Reuniones y visitas complementarias")
            w.paragraph("Actividades registradas en el mes: "+otherVisits.size+
                ". Se muestran aparte de las inspecciones de seguridad y no integran la cobertura del programa de inspecciones.")
            if(otherVisits.isNotEmpty()){
                w.table(listOf("Fecha","Centro","Tipo","Asunto"),
                    listOf(76f,144f,117f,174f),
                    otherVisits.sortedBy{it.visitedAt}.map{v->
                        listOf(date(v.visitedAt,false),v.center,
                            VisitCategories.name(v.kind),v.title)
                    })
            }else w.paragraph("Sin actividades complementarias registradas.")
            w.section("5. Criterios de lectura")
            w.paragraph("«Realizada»''',
    "exec monthly section")
s=replace_once(s,
    '''fun monthlyCsv(context:Context,period:YearMonth,
        inspections:List<InspectionEntity>,findings:List<FindingEntity>):File{''',
    '''fun monthlyCsv(context:Context,period:YearMonth,
        inspections:List<InspectionEntity>,findings:List<FindingEntity>,
        otherVisits:List<AdditionalVisitEntity> = emptyList()):File{''',
    "exec csv signature")
s=replace_once(s,
    '''            visits.forEach{ins->
                val related=findings.filter{it.inspectionId==ins.id}
                appendLine(listOf(date(ins.visitedAt),ins.center,ins.title,
                    related.size.toString(),related.count{it.status!="CERRADO"}.toString())
                    .joinToString(","){cell(it)})
            }
        }
        return File(exportDir(context),"comparativo_centros_"''',
    '''            visits.forEach{ins->
                val related=findings.filter{it.inspectionId==ins.id}
                appendLine(listOf(date(ins.visitedAt),ins.center,ins.title,
                    related.size.toString(),related.count{it.status!="CERRADO"}.toString())
                    .joinToString(","){cell(it)})
            }
            appendLine()
            appendLine("REUNIONES Y VISITAS COMPLEMENTARIAS")
            appendLine(listOf("Fecha","Centro","Tipo","Asunto","Acuerdos","Estado")
                .joinToString(","){cell(it)})
            otherVisits.sortedBy{it.visitedAt}.forEach{row->
                appendLine(listOf(date(row.visitedAt),row.center,
                    VisitCategories.name(row.kind),row.title,
                    row.agreements,row.agreementStatus).joinToString(","){cell(it)})
            }
        }
        return File(exportDir(context),"comparativo_centros_"''',
    "csv sections")
reports.write_text(s)

b=gradle.read_text()
if 'versionCode = 5' not in b:raise RuntimeError("Esperaba versión 0.5")
b=b.replace("versionCode = 5","versionCode = 6")
b=b.replace('versionName = "0.5.0"','versionName = "0.6.0"')
gradle.write_text(b)
print("Inspecciones DISAL 0.6 listo: CPHS, emergencia, visitas técnicas, formularios breves, PDFs y métricas separadas.")
