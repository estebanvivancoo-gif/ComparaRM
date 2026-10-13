package cl.munistgo.disal.inspecciones

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Captura de firma manuscrita, no equivale a firma electrónica avanzada. */
class SignaturePad(context:Context):View(context) {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.rgb(23,57,81)
        strokeWidth=4f
        style=Paint.Style.STROKE
        strokeJoin=Paint.Join.ROUND
        strokeCap=Paint.Cap.ROUND
    }
    private val path=Path()
    private var touched=false
    init { setBackgroundColor(Color.WHITE) }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        canvas.drawPath(path,pen)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN -> {path.moveTo(event.x,event.y);touched=true;invalidate();return true}
            MotionEvent.ACTION_MOVE -> {path.lineTo(event.x,event.y);invalidate();return true}
            MotionEvent.ACTION_UP -> {path.lineTo(event.x,event.y);invalidate();performClick();return true}
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    fun clear() {path.reset();touched=false;invalidate()}
    fun save():String {
        if(!touched) return ""
        val bitmap=Bitmap.createBitmap(width.coerceAtLeast(1),height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);draw(canvas)
        val dir=File(context.filesDir,"signatures").apply {mkdirs()}
        val file=File(dir,UUID.randomUUID().toString()+".png")
        FileOutputStream(file).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
        return file.absolutePath
    }
}
