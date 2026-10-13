from pathlib import Path

root=Path("InspeccionesDISAL")
base=root/"app/src/main/java/cl/munistgo/disal/inspecciones"
src=base/"MainActivity.kt"
signature=base/"SignaturePad.kt"
docs=base/"Documents.kt"
profile=base/"FieldUX.kt"
manifest=root/"app/src/main/AndroidManifest.xml"
gradle=root/"app/build.gradle.kts"
s=src.read_text()

def replace(old,new):
    global s
    if old not in s:
        raise RuntimeError("Falta ancla Kotlin: "+old[:130])
    s=s.replace(old,new,1)

# 4th bottom navigation tab (fixed) and open findings directly from due-date dashboard.
replace('''Triple("records","Historial / Métricas","▤"),
                        Triple("profile","Perfil","⚙")''',
'''Triple("records","Historial","▤"),
                        Triple("followup","Seguimiento","✓"),
                        Triple("profile","Perfil","⚙")''')
replace('''                        "profile" -> ProfileUX()
                        "detail" ->''',
'''                        "profile" -> ProfileUX()
                        "followup" -> FollowupUX(inspections,findings,dao,
                            onOpen={inspectionId,findingId ->
                                selectedId=inspectionId
                                selectedFinding=findingId
                                screen="finding"
                            })
                        "detail" ->''')

# Editable visit date and time; keep current time as default for new reports.
replace('''    var title by remember(inspection.id){mutableStateOf(inspection.title)}''',
'''    var visitTime by remember(inspection.id){mutableLongStateOf(inspection.visitedAt)}
    var title by remember(inspection.id){mutableStateOf(inspection.title)}''')
replace('''    val current=inspection.copy(title=title,center=center,area=area,inspector=inspector,inspectorRole=role,conclusions=conclusions)''',
'''    val current=inspection.copy(visitedAt=visitTime,title=title,center=center,area=area,
        inspector=inspector,inspectorRole=role,conclusions=conclusions)''')
replace('''            Text("Visita: "+date(inspection.visitedAt),color=gray)''',
'''            Text("Fecha y hora de la visita",fontWeight=FontWeight.Bold,color=navy)
            OutlinedButton(onClick={
                val local=java.time.Instant.ofEpochMilli(visitTime)
                    .atZone(java.time.ZoneId.systemDefault())
                android.app.DatePickerDialog(context,{_,year,month,day->
                    android.app.TimePickerDialog(context,{_,hour,minute->
                        visitTime=java.time.LocalDate.of(year,month+1,day)
                            .atTime(hour,minute)
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    },local.hour,local.minute,true).show()
                },local.year,local.monthValue-1,local.dayOfMonth).show()
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text(date(visitTime)+"  ·  Editar")
            }
            Text("La fecha se completa automáticamente, pero puede corregirse para visitas anteriores.",
                style=MaterialTheme.typography.bodySmall,color=gray)''')

# A human reviewer accepts suggested technical content; do not treat reference URLs as verified legal advice.
replace('''    var unsafe by remember(original.id){mutableStateOf(original.unsafeCondition)}''',
'''    var unsafe by remember(original.id){mutableStateOf(original.unsafeCondition)}
    var lawMessage by remember {mutableStateOf("")}
    var aiKey by remember {mutableStateOf("")}
    var aiMessage by remember {mutableStateOf("")}''')
replace('''            LabelField("Descripción de condición insegura",unsafe,{unsafe=it},multiline=true)
            LabelField("Peligro identificado",hazard,{hazard=it},multiline=true)''',
'''            LabelField("Descripción de condición insegura",unsafe,{unsafe=it},multiline=true)
            if(unsafe.length>=5) {
                Section("Sugerencias técnicas y normativas automáticas") {
                    Text("Se generan en el dispositivo según el texto descrito. No son dictámenes ni citas legales verificadas.",
                        color=gray,style=MaterialTheme.typography.bodySmall)
                    val options=remember(unsafe){SafetyAdvice.local(unsafe)}
                    options.forEachIndexed{index,item->
                        Text("Opción "+(index+1)+": "+item.hazard,
                            fontWeight=FontWeight.SemiBold,color=navy)
                        Text(item.norm,style=MaterialTheme.typography.bodySmall)
                        Text(item.action,style=MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(onClick={hazard=item.hazard}){Text("Usar peligro")}
                            OutlinedButton(onClick={regulation=item.norm}){Text("Usar norma")}
                            OutlinedButton(onClick={action=item.action}){Text("Usar medida")}
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)){
                            TextButton(onClick={
                                context.startActivity(android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(item.sourceUrl)))
                            }){Text("Ver norma en BCN ↗")}
                            TextButton(onClick={
                                scope.launch{
                                    lawMessage="Consultando fuente oficial..."
                                    lawMessage=withContext(Dispatchers.IO){
                                        SafetyAdvice.checkBcn(item)
                                    }
                                }
                            }){Text("Comprobar acceso BCN")}
                        }
                        HorizontalDivider()
                    }
                    if(lawMessage.isNotBlank())Text(lawMessage,color=teal,
                        style=MaterialTheme.typography.bodySmall)
                    Text("IA online optativa. Solo el texto de la condición será enviado, al pulsar el botón, a un proveedor externo; no envíes datos personales ni clínicos.",
                        color=gray,style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=aiKey,onValueChange={aiKey=it},
                        label={Text("Clave API autorizada (no se guarda)")},
                        visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedButton(
                        enabled=aiKey.isNotBlank()&&unsafe.length>=10,
                        onClick={
                            scope.launch{
                                aiMessage="Solicitando sugerencias a IA..."
                                try {
                                    val result=withContext(Dispatchers.IO){
                                        AiAdvisor.suggest(aiKey,unsafe)
                                    }
                                    hazard=result.hazard
                                    regulation=result.norm
                                    action=result.action
                                    aiMessage="Sugerencia aplicada para revisión. Comprueba la normativa en BCN antes de incorporarla al informe."
                                }catch(e:Exception){
                                    aiMessage="IA no disponible: "+e.message
                                }
                            }
                        },
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ){Text("Solicitar sugerencia de IA")}
                    if(aiMessage.isNotBlank())Text(aiMessage,color=teal,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            LabelField("Peligro identificado",hazard,{hazard=it},multiline=true)''')
replace('''            LabelField("Fecha límite (AAAA-MM-DD)",deadline,{deadline=it})''',
'''            LabelField("Fecha de verificación / plazo (AAAA-MM-DD)",deadline,{deadline=it})
            Text("Plazos rápidos desde hoy",color=navy,fontWeight=FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf(
                    "15 días" to java.time.LocalDate.now().plusDays(15),
                    "1 mes" to java.time.LocalDate.now().plusMonths(1),
                    "3 meses" to java.time.LocalDate.now().plusMonths(3),
                    "6 meses" to java.time.LocalDate.now().plusMonths(6)
                ).forEach{(label,value)->
                    FilterChip(selected=deadline==value.toString(),
                        onClick={deadline=value.toString()},label={Text(label)})
                }
            }''')

# Printed labels only: maintain old SQLite property names for schema compatibility.
s=s.replace('"Inspector responsable"','"Responsable de la visita"')
s=s.replace('"Cargo inspector"','"Cargo del responsable"')
s=s.replace('"Conclusiones del inspector"','"Conclusiones del responsable"')
s=s.replace('"Firma del inspector"','"Firma del responsable"')
s=s.replace('"Borrar trazo del inspector"','"Borrar firma del responsable"')
s=s.replace('"Nombre del inspector pendiente"','"Nombre del responsable pendiente"')
s=s.replace('"Inspector: "','"Responsable: "')
src.write_text(s)

# Prevent vertical ScrollView from claiming signature gestures.
pad=signature.read_text()
if "MotionEvent.ACTION_DOWN -> {path.moveTo(event.x,event.y)" not in pad:
    raise RuntimeError("Formato SignaturePad inesperado")
pad=pad.replace(
    "MotionEvent.ACTION_DOWN -> {path.moveTo(event.x,event.y);touched=true;invalidate();return true}",
    "MotionEvent.ACTION_DOWN -> {parent?.requestDisallowInterceptTouchEvent(true);path.moveTo(event.x,event.y);touched=true;invalidate();return true}"
)
pad=pad.replace(
    "MotionEvent.ACTION_UP -> {path.lineTo(event.x,event.y);invalidate();performClick();return true}",
    "MotionEvent.ACTION_UP -> {path.lineTo(event.x,event.y);invalidate();parent?.requestDisallowInterceptTouchEvent(false);performClick();return true}\n            MotionEvent.ACTION_CANCEL -> {parent?.requestDisallowInterceptTouchEvent(false);return true}"
)
signature.write_text(pad)

doc=docs.read_text()
doc=doc.replace('"Inspector responsable"','"Responsable de la visita"')
doc=doc.replace('"Conclusiones del inspector"','"Conclusiones del responsable"')
doc=doc.replace('"Inspector: "',' "Responsable: "')
doc=doc.replace('"Inspector · "',' "Responsable · "')
doc=doc.replace('"Inspector ·"','"Responsable ·"')
docs.write_text(doc)

ux=profile.read_text()
ux=ux.replace("Datos predeterminados del inspector","Datos predeterminados del responsable")
profile.write_text(ux)

xml=manifest.read_text()
needle='<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>'
if needle not in xml:raise RuntimeError("No fue aplicado patch v02")
xml=xml.replace(needle,needle+'\n    <uses-permission android:name="android.permission.INTERNET"/>')
manifest.write_text(xml)

b=gradle.read_text()
if "versionCode = 2" not in b:raise RuntimeError("Versión base no es 0.2")
b=b.replace("versionCode = 2","versionCode = 3").replace('versionName = "0.2.0"','versionName = "0.3.0"')
gradle.write_text(b)
print("Inspecciones DISAL 0.3: REAS/BCN, sugerencias, 4 pestañas, plazos, fecha editable y firma corregida")
