package cl.munistgo.disal.inspecciones

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

object CyberTheme {
    val background=Color(0xFF0A0F1D)
    val panel=Color(0xFF10243B)
    val panel2=Color(0xFF162D45)
    val cyan=Color(0xFF00E9F5)
    val magenta=Color(0xFFE062F8)
    val mint=Color(0xFF43E9B3)
    val amber=Color(0xFFFFC34A)
    val danger=Color(0xFFFF546B)
    val ink=Color(0xFFF0F9FF)
    val muted=Color(0xFF9AB8C7)
}

/** CameraX real camera preview. The reticle is a visual HUD, not a LiDAR scanner. 
 * The HD file is captured without the visual overlay so evidence remains unaltered.
 */
@Composable
fun CameraHud(onCaptured:(String)->Unit,onCancel:()->Unit) {
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val executor=remember(context){ContextCompat.getMainExecutor(context)}
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        granted=it
    }
    LaunchedEffect(Unit) {
        if(!granted)permission.launch(Manifest.permission.CAMERA)
    }
    var imageCapture by remember {mutableStateOf<ImageCapture?>(null)}
    var provider by remember {mutableStateOf<ProcessCameraProvider?>(null)}
    var failure by remember {mutableStateOf("")}
    var saving by remember {mutableStateOf(false)}
    DisposableEffect(provider) {
        onDispose {provider?.unbindAll()}
    }
    Box(Modifier.fillMaxSize().background(CyberTheme.background)) {
        if(granted) {
            AndroidView(factory={ctx ->
                PreviewView(ctx).apply{
                    scaleType=PreviewView.ScaleType.FILL_CENTER
                }
            },modifier=Modifier.fillMaxSize(),update={view->
                if(imageCapture==null && failure.isBlank()){
                    val future=ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try{
                            val cameraProvider=future.get()
                            val preview=Preview.Builder().build()
                            val capture=ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                            preview.setSurfaceProvider(view.surfaceProvider)
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture
                            )
                            provider=cameraProvider
                            imageCapture=capture
                        }catch(e:Exception){
                            failure="No se pudo iniciar la cámara: "+e.javaClass.simpleName
                        }
                    },executor)
                }
            })
            Canvas(Modifier.fillMaxSize()){
                val w=size.width
                val h=size.height
                val pad=w*.07f
                val left=pad
                val right=w-pad
                val top=h*.23f
                val bottom=h*.74f
                val stroke=3.dp.toPx()
                val segment=52.dp.toPx()
                val color=CyberTheme.cyan
                listOf(
                    Pair(Offset(left,top),Offset(left+segment,top)),
                    Pair(Offset(left,top),Offset(left,top+segment)),
                    Pair(Offset(right,top),Offset(right-segment,top)),
                    Pair(Offset(right,top),Offset(right,top+segment)),
                    Pair(Offset(left,bottom),Offset(left+segment,bottom)),
                    Pair(Offset(left,bottom),Offset(left,bottom-segment)),
                    Pair(Offset(right,bottom),Offset(right-segment,bottom)),
                    Pair(Offset(right,bottom),Offset(right,bottom-segment))
                ).forEach { (start,end)->drawLine(color,start,end,strokeWidth=stroke,cap=StrokeCap.Round)}
                val grid=CyberTheme.cyan.copy(alpha=.19f)
                for(i in 1..3){
                    val gx=left+(right-left)*i/4f
                    drawLine(grid,Offset(gx,top),Offset(gx,bottom),1.dp.toPx())
                }
                for(i in 1..3){
                    val gy=top+(bottom-top)*i/4f
                    drawLine(grid,Offset(left,gy),Offset(right,gy),1.dp.toPx())
                }
                drawLine(color.copy(alpha=.4f),Offset(left,h*.49f),Offset(right,h*.49f),1.5.dp.toPx())
                drawCircle(color.copy(alpha=.75f),radius=9.dp.toPx(),center=Offset(w/2,h*.49f),
                    style=Stroke(1.dp.toPx()))
                drawCircle(color.copy(alpha=.65f),radius=2.dp.toPx(),center=Offset(w/2,h*.49f))
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("Se necesita permiso de cámara",color=CyberTheme.ink)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick={permission.launch(Manifest.permission.CAMERA)}){
                    Text("Autorizar cámara")
                }
            }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)
            .background(CyberTheme.background.copy(alpha=.82f))
            .padding(horizontal=18.dp,vertical=22.dp)){
            Text("REGISTRO DE EVIDENCIA / CÁMARA HD",color=CyberTheme.cyan,
                fontWeight=FontWeight.Bold,fontSize=14.sp,letterSpacing=1.sp)
            Text("MARCO HUD · La retícula es una guía visual y no altera la fotografía original",
                color=CyberTheme.ink,fontSize=11.sp,lineHeight=14.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth().background(CyberTheme.background.copy(alpha=.94f))
            .padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
            if(failure.isNotBlank())Text(failure,color=CyberTheme.danger,textAlign=TextAlign.Center)
            Text("EVIDENCIA LOCAL · Sin análisis biométrico ni LiDAR",color=CyberTheme.muted,fontSize=11.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                enabled=granted&&imageCapture!=null&&!saving,
                onClick={
                    val capture=imageCapture ?:return@Button
                    saving=true
                    val folder=File(context.filesDir,"photos").apply{mkdirs()}
                    val photo=File(folder,UUID.randomUUID().toString()+".jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(photo).build(),executor,
                        object:ImageCapture.OnImageSavedCallback{
                            override fun onImageSaved(output:ImageCapture.OutputFileResults){
                                saving=false
                                onCaptured(photo.absolutePath)
                            }
                            override fun onError(exception:androidx.camera.core.ImageCaptureException){
                                saving=false
                                failure="Falló la fotografía: "+exception.message
                            }
                        }
                    )
                },
                modifier=Modifier.fillMaxWidth().height(58.dp),
                colors=ButtonDefaults.buttonColors(containerColor=CyberTheme.cyan,
                    contentColor=CyberTheme.background),
                shape=RoundedCornerShape(18.dp)
            ){Text(if(saving)"GUARDANDO..." else "◉  CAPTURAR FOTOGRAFÍA",fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(8.dp))
            TextButton(onClick=onCancel){Text("Volver al hallazgo",color=CyberTheme.ink)}
        }
    }
}
