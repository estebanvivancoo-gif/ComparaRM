from pathlib import Path

root=Path("InspeccionesDISAL")
base=root/"app/src/main/java/cl/munistgo/disal/inspecciones"
main=base/"MainActivity.kt"
field=base/"FieldUX.kt"
follow=base/"Followup.kt"
sign=base/"SignaturePad.kt"
manifest=root/"app/src/main/AndroidManifest.xml"
gradle=root/"app/build.gradle.kts"
s=main.read_text()

def change(old,new):
    global s
    if old not in s: raise RuntimeError("Falta ancla UI: "+old[:120])
    s=s.replace(old,new,1)

change("import androidx.compose.ui.unit.dp","import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties")
change("private val teal=Color(0xFF0D568A)","private val teal=CyberTheme.cyan")
change("private val navy=Color(0xFF123C60)","private val navy=CyberTheme.ink")
change("private val gray=Color(0xFF687681)","private val gray=CyberTheme.muted")
change("private val riskLow=Color(0xFF237A4E)","private val riskLow=CyberTheme.mint")
change("private val riskMedium=Color(0xFFD39B1B)","private val riskMedium=CyberTheme.amber")
change("private val riskCritical=Color(0xFFC83A3A)","private val riskCritical=CyberTheme.danger")
change("    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},",
'''    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},
        shape=RoundedCornerShape(12.dp),''')
change("    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {",
'''    Card(modifier=Modifier.fillMaxWidth().border(1.dp,CyberTheme.cyan.copy(alpha=.33f),
        RoundedCornerShape(16.dp)),shape=RoundedCornerShape(16.dp),
        colors=CardDefaults.cardColors(containerColor=CyberTheme.panel)) {''')
change('''        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,fontWeight=FontWeight.Bold,color=teal)''',
'''        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(),fontWeight=FontWeight.Bold,color=teal,letterSpacing=1.2.sp)''')
change('''    MaterialTheme(colorScheme=lightColorScheme(
        primary=teal,secondary=navy,background=Color(0xFFF8F9FA)))''',
'''    MaterialTheme(colorScheme=darkColorScheme(
        primary=teal,secondary=CyberTheme.magenta,
        background=CyberTheme.background,surface=CyberTheme.panel,
        onSurface=CyberTheme.ink,onBackground=CyberTheme.ink))''')
s=s.replace("Color(0xFFF8F9FA)","CyberTheme.background")
s=s.replace("containerColor=Color.White","containerColor=CyberTheme.panel")
s=s.replace("Surface(color=Color.White","Surface(color=CyberTheme.panel")
s=s.replace("color=Color.White","color=CyberTheme.ink")
s=s.replace("contentColor=Color.White","contentColor=CyberTheme.background")
s=s.replace("selectedLabelColor=Color.White","selectedLabelColor=CyberTheme.background")
s=s.replace("Color(0xFFE3EDF6)","CyberTheme.cyan.copy(alpha=.20f)")
s=s.replace("Surface(color=navy)","Surface(color=CyberTheme.panel)")
s=s.replace('''Text("INSPECCIONES DISAL",fontWeight=FontWeight.Bold,color=CyberTheme.ink)''',
'''Text("INSPECCIONES DISAL  /  H&S",fontWeight=FontWeight.Bold,
                            color=CyberTheme.cyan,letterSpacing=1.2.sp)''')
s=s.replace("Color(0xFFDDEAF4)","CyberTheme.muted")
s=s.replace("Color(0xFFF1C96E)","CyberTheme.amber")
s=s.replace("Color.Black","CyberTheme.background")

# Floating labels and review risk chips are kept as genuine one-tap controls.
# Insert the full-screen CameraX view as a modal, retaining unsaved editing state.
anchor='''    var photoPath by remember(original.id){mutableStateOf(original.photoPath)}'''
change(anchor,anchor+'''
    var showCamera by remember(original.id){mutableStateOf(false)}''')
old='''            Button(onClick={
                try {
                    val dir=File(context.filesDir,"photos").apply {mkdirs()}
                    val file=File(dir,UUID.randomUUID().toString()+".jpg")
                    capturePath=file.absolutePath
                    val uri=FileProvider.getUriForFile(context,"cl.munistgo.disal.inspecciones.files",file)
                    camera.launch(uri)
                }catch(e:Exception){error="Cámara no disponible: "+e.message}
            },modifier=Modifier.fillMaxWidth()){Text(if(photoPath.isBlank())"Tomar fotografía" else "Reemplazar fotografía")}'''
new='''            Button(onClick={showCamera=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
                Text(if(photoPath.isBlank())"◉  CAPTURAR EVIDENCIA / HUD" else "◉  REEMPLAZAR FOTOGRAFÍA")
            }'''
change(old,new)
findingStart=s.index("private fun FindingScreen(")
dialogAt=s.index("    Scaffold(",findingStart)
s=s[:dialogAt]+'''    if(showCamera){
        Dialog(onDismissRequest={showCamera=false},
            properties=DialogProperties(usePlatformDefaultWidth=false)){
            CameraHud(onCaptured={photoPath=it;showCamera=false},
                onCancel={showCamera=false})
        }
    }
'''+s[dialogAt:]
# A single source-of-truth status indicator; no fictitious sensors or biometrics.
main.write_text(s)

f=field.read_text()
f=f.replace("private val blue=Color(0xFF0D568A)","private val blue=CyberTheme.cyan")
f=f.replace("private val blueDark=Color(0xFF123C60)","private val blueDark=CyberTheme.ink")
f=f.replace("private val hintColor=Color(0xFF687681)","private val hintColor=CyberTheme.muted")
f=f.replace("private val red=Color(0xFFC83A3A)","private val red=CyberTheme.danger")
f=f.replace("Color(0xFFF8F9FA)","CyberTheme.background")
f=f.replace("containerColor=Color.White","containerColor=CyberTheme.panel")
f=f.replace("contentColor=Color.White","contentColor=CyberTheme.background")
f=f.replace("color=Color.White","color=CyberTheme.ink")
f=f.replace("if(connected)Color(0xFFE7F0F8) else Color(0xFFFFEBC1)","if(connected)CyberTheme.panel else Color(0xFF443216)")
f=f.replace("if(connected)blueDark else Color(0xFF8B5607)","if(connected)CyberTheme.cyan else CyberTheme.amber")
f=f.replace("Color(0xFFA36B08)","CyberTheme.amber")
f=f.replace("Color(0xFF237A4E)","CyberTheme.mint")
f=f.replace("shape=RoundedCornerShape(14.dp)","shape=RoundedCornerShape(16.dp)")
f=f.replace('''Text("Inicio / Visitas",fontWeight=FontWeight.Bold,color=blueDark,''',
'''Text("CENTRO DE CONTROL / VISITAS",fontWeight=FontWeight.Bold,color=blue,''')
f=f.replace("import androidx.compose.ui.unit.dp","import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp")
f=f.replace("Text(\"Indicadores del mes · \"+YearMonth.now(),color=hintColor)",
'''Text("INDICADORES EN TERRENO · "+YearMonth.now(),color=hintColor,letterSpacing=1.sp)''')
f=f.replace("Text(\"Visitas recientes\",fontWeight=FontWeight.Bold,color=blueDark)",
'''Text("REGISTROS RECIENTES",fontWeight=FontWeight.Bold,color=blue,letterSpacing=1.2.sp)''')
field.write_text(f)

fl=follow.read_text()
fl=fl.replace("private val blue=Color(0xFF0D568A)","private val blue=CyberTheme.cyan")
fl=fl.replace("private val red=Color(0xFFC83A3A)","private val red=CyberTheme.danger")
fl=fl.replace("private val orange=Color(0xFF986617)","private val orange=CyberTheme.amber")
fl=fl.replace("private val green=Color(0xFF237A4E)","private val green=CyberTheme.mint")
fl=fl.replace("containerColor=Color.White","containerColor=CyberTheme.panel")
fl=fl.replace("Color.White","CyberTheme.ink")
follow.write_text(fl)

sig=sign.read_text()
sig=sig.replace("color=Color.rgb(23,57,81)","color=Color.rgb(0,233,245)")
sig=sig.replace("strokeWidth=4f","strokeWidth=5f")
sig=sig.replace("init { setBackgroundColor(Color.WHITE) }",
'''init {
        pen.setShadowLayer(6f,0f,0f,Color.rgb(0,180,235))
        setLayerType(View.LAYER_TYPE_SOFTWARE,null)
        setBackgroundColor(Color.rgb(10,15,29))
    }''')
sig=sig.replace("canvas.drawColor(Color.WHITE)","canvas.drawColor(Color.rgb(10,15,29))")
sign.write_text(sig)

m=manifest.read_text()
target='<uses-permission android:name="android.permission.INTERNET"/>'
if target not in m:raise RuntimeError("Manifest v03 no tiene INTERNET")
m=m.replace(target,target+'\n    <uses-permission android:name="android.permission.CAMERA"/>')
manifest.write_text(m)

b=gradle.read_text()
needle='    implementation("androidx.activity:activity-compose:1.9.3")'
if needle not in b:raise RuntimeError("Gradle inesperado")
b=b.replace(needle,needle+'''
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")''')
if "versionCode = 3" not in b:raise RuntimeError("Versión actual no es 0.3")
b=b.replace("versionCode = 3","versionCode = 4").replace('versionName = "0.3.0"','versionName = "0.4.0"')
gradle.write_text(b)
print("Cyber HUD v0.4 applied; offline/Room/report models preserved.")
