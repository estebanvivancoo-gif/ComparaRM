from pathlib import Path

root=Path("InspeccionesDISAL")
src=root/"app/src/main/java/cl/munistgo/disal/inspecciones/MainActivity.kt"
manifest=root/"app/src/main/AndroidManifest.xml"
build=root/"app/build.gradle.kts"
s=src.read_text()

for name in ("LabelField","Section","DashboardStat","HistoryScreen","MonthlyScreen"):
    old="private fun "+name+"("
    if old not in s: raise ValueError("No se encontró: "+old)
    s=s.replace(old,"internal fun "+name+"(",1)

start=s.index("@Composable\nfun DisalApp() {")
end=s.index("@Composable\ninternal fun DashboardStat",start)
app=r'''@Composable
fun DisalApp() {
    val context=LocalContext.current
    val dao=remember {InspectionDatabase.get(context).dao()}
    val scope=rememberCoroutineScope()
    val inspections by dao.watchInspections().collectAsState(initial=emptyList())
    val findings by dao.watchAllFindings().collectAsState(initial=emptyList())
    val connected by networkConnected()
    var tab by remember {mutableStateOf("home")}
    var screen by remember {mutableStateOf("home")}
    var selectedId by remember {mutableStateOf<String?>(null)}
    var selectedFinding by remember {mutableStateOf<String?>(null)}

    fun newInspection() {
        val prefs=context.getSharedPreferences("inspector_profile",android.content.Context.MODE_PRIVATE)
        val item=InspectionEntity(
            inspector=prefs.getString("name","").orEmpty(),
            inspectorRole=prefs.getString("role","Ingeniero en Prevención de Riesgos")
                ?: "Ingeniero en Prevención de Riesgos"
        )
        scope.launch {dao.saveInspection(item);selectedId=item.id;screen="detail"}
    }

    MaterialTheme(colorScheme=lightColorScheme(
        primary=teal,secondary=navy,background=Color(0xFFF8F9FA))){
        Scaffold(
            containerColor=Color(0xFFF8F9FA),
            bottomBar={
                NavigationBar(containerColor=Color.White,tonalElevation=8.dp) {
                    listOf(
                        Triple("home","Inicio / Visitas","⌂"),
                        Triple("records","Historial / Métricas","▤"),
                        Triple("profile","Perfil","⚙")
                    ).forEach{(destination,label,symbol)->
                        NavigationBarItem(
                            selected=tab==destination,
                            onClick={tab=destination;screen=destination},
                            label={Text(label,maxLines=1,style=MaterialTheme.typography.labelSmall)},
                            icon={Text(symbol,style=MaterialTheme.typography.titleLarge)},
                            colors=NavigationBarItemDefaults.colors(
                                selectedIconColor=teal,selectedTextColor=teal,
                                indicatorColor=Color(0xFFE3EDF6))
                        )
                    }
                }
            }
        ){inner ->
            Column(Modifier.fillMaxSize().padding(inner)){
                Surface(color=navy) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=11.dp)) {
                        Text("INSPECCIONES DISAL",fontWeight=FontWeight.Bold,color=Color.White)
                        Text("Higiene y Seguridad Laboral · Uso en terreno",
                            color=Color(0xFFDDEAF4),style=MaterialTheme.typography.bodySmall)
                    }
                }
                StorageStatusBanner(connected)
                Box(Modifier.fillMaxWidth().weight(1f)){
                    when(screen) {
                        "home" -> HomeUX(inspections,findings,
                            onNew={newInspection()},onOpen={selectedId=it;screen="detail"},
                            onHistory={tab="records";screen="records"})
                        "records" -> HistoryMetricsUX(inspections,findings,
                            onOpen={selectedId=it;screen="detail"},onNew={newInspection()})
                        "profile" -> ProfileUX()
                        "detail" -> {
                            val ins=inspections.firstOrNull{it.id==selectedId}
                            if(ins==null)Text("Selecciona una inspección.",Modifier.padding(16.dp))
                            else DetailScreen(ins,findings.filter{it.inspectionId==ins.id},dao,
                                onFinding={selectedFinding=it;screen="finding"},
                                onSign={screen="sign"},
                                onBack={screen=if(tab=="records")"records" else "home"})
                        }
                        "finding" -> {
                            val ins=inspections.firstOrNull{it.id==selectedId}
                            if(ins!=null)FindingScreen(ins.id,
                                findings.firstOrNull{it.id==selectedFinding},dao,
                                onBack={screen="detail"})
                        }
                        "sign" -> {
                            val ins=inspections.firstOrNull{it.id==selectedId}
                            if(ins!=null)SignScreen(ins,dao,onBack={screen="detail"})
                        }
                    }
                }
            }
        }
    }
}

'''
s=s[:start]+app+s[end:]

def replace_once(old,new):
    global s
    if old not in s:raise ValueError("Ancla ausente: "+old[:120])
    s=s.replace(old,new,1)

opening='''    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Formulario de terreno · Paso'''
replacement='''    Scaffold(containerColor=Color(0xFFF8F9FA),bottomBar={
        Surface(color=Color.White,shadowElevation=8.dp){
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedButton(onClick={save();if(step>0)step-- else onBack()},
                    modifier=Modifier.weight(1f).heightIn(min=50.dp)){
                    Text(if(step==0)"Volver" else "Anterior")
                }
                Button(onClick={save();if(step<2)step++ else onBack()},
                    modifier=Modifier.weight(1.4f).heightIn(min=50.dp)){
                    Text(if(step<2)"Guardar y seguir →" else "Guardar y salir")
                }
            }
        }
    }){inner ->
    Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Formulario de terreno · Paso'''
replace_once(opening,replacement)
footer='''        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            OutlinedButton(onClick={if(step>0)step-- else onBack()}){Text(if(step==0)"Volver" else "Anterior")}
            if(step<2)Button(onClick={save();step++}){Text("Guardar y continuar →")}
        }
        if(message.isNotBlank())Text(message,color=teal)
        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Volver al historial")}
    }
}

@Composable
private fun FindingScreen'''
newfooter='''        if(message.isNotBlank())Text(message,color=teal)
        Text("Usa los botones inferiores para guardar o avanzar.",
            color=gray,style=MaterialTheme.typography.bodySmall)
    }
    }
}

@Composable
private fun FindingScreen'''
replace_once(footer,newfooter)

# Move finding save action from the bottom of the scrollable form into
# a fixed control row reachable with one hand.
start=s.index("private fun FindingScreen(")
end=s.index("@Composable\nprivate fun SignScreen",start)
part=s[start:end]
begin_mark="            Button(onClick={\n                val valid=deadline.isBlank()"
end_mark='''            },modifier=Modifier.fillMaxWidth()) {Text("Guardar hallazgo / seguimiento")}'''
if begin_mark not in part or end_mark not in part:
    raise ValueError("No se encontró el botón del hallazgo")
begin=part.index(begin_mark)
finish=part.index(end_mark,begin)+len(end_mark)
inner_save=part[begin+len("            Button(onClick={\n"):part.index(end_mark,begin)]
part=part[:begin]+'''            Text("Pulsa Guardar hallazgo en la barra inferior.",
                color=gray,style=MaterialTheme.typography.bodySmall)'''+part[finish:]
form_open='''    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Section("B. Registro del hallazgo")'''
new_open='''    val saveFinding:()->Unit = {
'''+inner_save+'''    }
    Scaffold(containerColor=Color(0xFFF8F9FA),bottomBar={
        Surface(color=Color.White,shadowElevation=8.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),
                horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedButton(onClick=onBack,
                    modifier=Modifier.weight(1f).heightIn(min=50.dp)){Text("Volver")}
                Button(onClick=saveFinding,
                    modifier=Modifier.weight(2f).heightIn(min=50.dp)){Text("Guardar hallazgo")}
            }
        }
    }){inner ->
    Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Section("B. Registro del hallazgo")'''
if form_open not in part:raise ValueError("Falta formulario de hallazgos")
part=part.replace(form_open,new_open,1)
tail='''        OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("Volver")}
    }
}

'''
if tail not in part:raise ValueError("Falta cierre del hallazgo")
part=part.replace(tail,'''    }
    }
}

''',1)
start=s.index("private fun FindingScreen(")
end=s.index("@Composable\nprivate fun SignScreen",start)
s=s[:start]+part+s[end:]

# Green=closed, amber=pending, blue=in progress
replace_once('''colors=FilterChipDefaults.filterChipColors(selectedContainerColor=teal,
                            selectedLabelColor=Color.White))''','''colors=FilterChipDefaults.filterChipColors(
                            selectedContainerColor=when(value) {
                                "PENDIENTE"->Color(0xFFF1C96E)
                                "PROCESO"->teal
                                else->riskLow
                            },
                            selectedLabelColor=if(value=="PENDIENTE")Color.Black else Color.White
                        ))''')
src.write_text(s)
m=manifest.read_text()
a='<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
if a not in m:raise ValueError("Manifest inesperado")
m=m.replace(a,a+'\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>',1)
manifest.write_text(m)
b=build.read_text()
if "versionCode = 1" not in b:raise ValueError("Versión incompatible")
b=b.replace("versionCode = 1","versionCode = 2")
b=b.replace('versionName = "0.1.0"','versionName = "0.2.0"')
build.write_text(b)
print("UI UX v0.2 preparada: tres pestañas fijas, señales offline, perfil y acciones inferiores")
