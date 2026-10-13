from pathlib import Path

root=Path("InspeccionesDISAL")
base=root/"app/src/main/java/cl/munistgo/disal/inspecciones"
main=base/"MainActivity.kt"
field=base/"FieldUX.kt"
follow=base/"Followup.kt"
gradle=root/"app/build.gradle.kts"
src=main.read_text()

def change(old,new):
    global src
    if old not in src:raise RuntimeError("Falta ancla UI v0.5: "+old[:125])
    src=src.replace(old,new,1)

change('''withContext(Dispatchers.IO){Documents.inspectionPdf(context,current,people,rows)}''',
       '''withContext(Dispatchers.IO){ExecutiveReports.individual(context,current,people,rows)}''')

# Import safely and copy Uri to private directory (no permanent external URI dependency).
needle='''    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if(success)photoPath=capturePath
        else error="La captura fue cancelada."
    }'''
gallery=r'''    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null)scope.launch{
            try{
                error="Importando fotografía..."
                val copied=withContext(Dispatchers.IO){
                    val kind=context.contentResolver.getType(uri).orEmpty()
                    require(kind.startsWith("image/")){"Solo se admiten fotografías"}
                    val dir=File(context.filesDir,"photos").apply{mkdirs()}
                    val output=File(dir,UUID.randomUUID().toString()+".jpg")
                    try{
                        val stream=context.contentResolver.openInputStream(uri)
                            ?:throw java.io.IOException("No se pudo abrir imagen")
                        stream.use{input->
                            output.outputStream().use{out->
                                val buffer=ByteArray(16384)
                                var count=0L
                                while(true){
                                    val read=input.read(buffer)
                                    if(read<0)break
                                    count+=read
                                    if(count>18L*1024*1024)throw java.io.IOException(
                                        "Imagen superior a 18 MB")
                                    out.write(buffer,0,read)
                                }
                                if(count==0L)throw java.io.IOException("Archivo vacío")
                            }
                        }
                        output
                    }catch(e:Exception){output.delete();throw e}
                }
                photoPath=copied.absolutePath
                error=""
            }catch(e:Exception){error="No se pudo importar la fotografía: "+e.message}
        }
    }'''
change(needle,needle+"\n"+gallery)
photoButton='''            Button(onClick={showCamera=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
                Text(if(photoPath.isBlank())"◉  CAPTURAR EVIDENCIA / HUD" else "◉  REEMPLAZAR FOTOGRAFÍA")
            }'''
if photoButton not in src:raise RuntimeError("Cámara HUD ausente")
change(photoButton,photoButton+'''
            OutlinedButton(onClick={gallery.launch("image/*")},
                modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){
                Text("CARGAR FOTOGRAFÍA DE LA GALERÍA")
            }
            Text("Puedes registrar visitas anteriores con imágenes ya existentes.",
                style=MaterialTheme.typography.bodySmall,color=gray)''')
main.write_text(src)

ux=field.read_text()
needle='''            else MonthlyScreen(inspections,findings)'''
if needle not in ux:raise RuntimeError("La pestaña de métricas cambió")
ux=ux.replace(needle,'''            else ExecutiveMonthlyUX(inspections,findings)''')
anchor='''    var saved by remember{mutableStateOf(false)}'''
if anchor not in ux:raise RuntimeError("Perfil inesperado")
ux=ux.replace(anchor,anchor+'''
    var centerText by remember{
        mutableStateOf(CenterCatalog.planned(context).joinToString("\\n"))
    }
    var centersSaved by remember{mutableStateOf(false)}''')
anchor='''        Section("Trabajo sin conexión"){'''
if anchor not in ux:raise RuntimeError("Sección offline no encontrada")
ux=ux.replace(anchor,'''        Section("Establecimientos programados para los informes mensuales"){
            Text("Un establecimiento sin visita se informa únicamente si figura en esta lista. Introduce un centro por línea y ajusta la nómina según el programa de trabajo.",
                style=MaterialTheme.typography.bodySmall,color=hintColor)
            LabelField("Centros que deben figurar en el comparativo",centerText,
                {centerText=it;centersSaved=false},multiline=true)
            Button(onClick={
                CenterCatalog.save(context,centerText)
                centerText=CenterCatalog.planned(context).joinToString("\\n")
                centersSaved=true
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text("Guardar nómina de establecimientos")
            }
            if(centersSaved)Text("Nómina guardada localmente.",color=CyberTheme.mint)
        }
'''+anchor)
field.write_text(ux)

t=follow.read_text()
if 'var tab by remember {mutableStateOf("Por vencer")}' not in t:raise RuntimeError("Seguimiento inesperado")
t=t.replace('var tab by remember {mutableStateOf("Por vencer")}',
            'var tab by remember {mutableStateOf("Pendientes")}',1)
if '''        "Por vencer"->near''' not in t:raise RuntimeError("Seguimiento sin filtros")
t=t.replace('''        "Por vencer"->near''','''        "Pendientes"->open
        "Por vencer"->near''',1)
t=t.replace('''    }).sortedWith(compareBy<FindingEntity>{
        runCatching {LocalDate.parse(it.deadlineIso)}.getOrNull() ?: LocalDate.MAX
    }.thenBy{it.createdAt})''',
'''    }).let{selected->
        if(tab=="Pendientes")selected.sortedByDescending{it.createdAt}
        else selected.sortedWith(compareBy<FindingEntity>{
            runCatching{LocalDate.parse(it.deadlineIso)}.getOrNull() ?: LocalDate.MAX
        }.thenByDescending{it.createdAt})
    }''')
t=t.replace('''listOf("Por vencer","Vencidos","En proceso","Todos","Cerrados")''',
            '''listOf("Pendientes","Por vencer","Vencidos","En proceso","Cerrados")''')
t=t.replace('''Text("Plazos próximos (15 días): "+near.size+" · Vencidos: "+late.size+''',
            '''Text("Abiertos: "+open.size+" · Próximos a vencer: "+near.size+" · Vencidos: "+late.size+''')
follow.write_text(t)

b=gradle.read_text()
if "versionCode = 4" not in b:raise RuntimeError("Version code no es 4")
b=b.replace("versionCode = 4","versionCode = 5")
b=b.replace('versionName = "0.4.0"','versionName = "0.5.0"')
dep='    implementation("androidx.camera:camera-view:1.4.1")'
if dep not in b:raise RuntimeError("Falta CameraX")
b=b.replace(dep,dep+'\n    implementation("androidx.exifinterface:exifinterface:1.3.7")')
gradle.write_text(b)
print("DISAL v0.5: PDF ejecutivo, comparativo mensual, seguimiento pendiente por defecto, galería y catálogo de centros")
