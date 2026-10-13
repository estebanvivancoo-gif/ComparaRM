package cl.munistgo.disal.inspecciones

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una ficha formal y breve para actuaciones que no son inspecciones de seguridad.
 * Nunca representa una reunión CPHS como inspección y no crea hallazgos automáticos.
 */
object SimpleVisitPdf {
    private const val NAVY=0xFF173D59
    private const val BLUE=0xFF256182
    private const val INK=0xFF253641
    private const val GRAY=0xFF62727D
    private const val PALE=0xFFF0F4F7
    private const val X=43f
    private const val END=552f
    private fun paint(size:Float,color:Int,bold:Boolean=false)=Paint(Paint.ANTI_ALIAS_FLAG).apply{
        this.textSize=size;this.color=color
        this.typeface=Typeface.create(if(bold)"sans-serif-medium" else "sans-serif",Typeface.NORMAL)
    }
    private fun breakText(value:String,maxWidth:Float,p:Paint):List<String>{
        val lines=mutableListOf<String>()
        for(para in value.replace("\r","").split("\n")){
            if(para.isBlank()){lines.add("");continue}
            var line=""
            for(word in para.trim().split(Regex("\\s+"))){
                val proposed=if(line.isBlank())word else line+" "+word
                if(line.isNotBlank()&&p.measureText(proposed)>maxWidth){
                    lines.add(line);line=word
                }else line=proposed
                while(p.measureText(line)>maxWidth&&line.length>1){
                    val n=(1 until line.length).lastOrNull{p.measureText(line.take(it))<=maxWidth}?:1
                    lines.add(line.take(n));line=line.drop(n)
                }
            }
            lines.add(line)
        }
        return lines
    }
    private class Writer(private val doc:PdfDocument,private val title:String){
        private var current:PdfDocument.Page?=null
        private var pageNo=0
        var top=162f
        val canvas:Canvas get()=requireNotNull(current).canvas
        init{newPage()}
        fun ensure(height:Float){if(top+height>770f)newPage()}
        private fun newPage(){
            current?.let{finish()}
            pageNo++
            current=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
            canvas.drawColor(Color.WHITE)
            val header=Paint().apply{color=NAVY}
            canvas.drawRect(0f,0f,595f,82f,header)
            val logo=Paint().apply{color=Color.WHITE}
            canvas.drawRoundRect(RectF(X,19f,X+57f,69f),3f,3f,logo)
            canvas.drawText("LOGO",X+15f,40f,paint(8f,NAVY,true))
            canvas.drawText("OFICIAL",X+12f,52f,paint(8f,NAVY,true))
            canvas.drawText("DIRECCIÓN DE SALUD",111f,41f,paint(15f,Color.WHITE,true))
            canvas.drawText("ILUSTRE MUNICIPALIDAD DE SANTIAGO",111f,59f,
                paint(9f,0xFFE1EBF2.toInt()))
            canvas.drawText("REGISTRO DE ACTIVIDAD / VISITA TÉCNICA",X,119f,
                paint(16f,NAVY,true))
            canvas.drawText(title.take(85),X,141f,paint(10f,GRAY))
            canvas.drawRect(X,149f,END,150.5f,Paint().apply{color=BLUE})
            top=165f
        }
        private fun finish(){
            canvas.drawRect(X,804f,END,805f,Paint().apply{color=0xFFD7E1E8.toInt()})
            canvas.drawText("Registro técnico interno · Revisión previa a difusión",X,820f,paint(8f,GRAY))
            canvas.drawText("Página "+pageNo,510f,820f,paint(8f,GRAY))
            doc.finishPage(requireNotNull(current))
            current=null
        }
        fun section(title:String){
            ensure(34f)
            canvas.drawRect(X,top,END,top+24f,Paint().apply{color=PALE})
            canvas.drawRect(X,top,X+3f,top+24f,Paint().apply{color=BLUE})
            canvas.drawText(title.uppercase(),X+11f,top+16f,paint(10.5f,NAVY,true))
            top+=36f
        }
        fun key(label:String,value:String){
            val rows=breakText(value.ifBlank{"—"},345f,paint(10.3f,INK))
            val h=maxOf(25f,rows.size*13.5f+11f)
            ensure(h)
            canvas.drawText(label.take(30),X+5f,top+13f,paint(9.3f,GRAY,true))
            rows.forEachIndexed{index,line->
                canvas.drawText(line,X+160f,top+13f+index*13.5f,paint(10.3f,INK))
            }
            canvas.drawRect(X,top+h-1f,END,top+h-.5f,Paint().apply{color=0xFFE2E9ED.toInt()})
            top+=h
        }
        fun paragraph(text:String){
            val lines=breakText(text.ifBlank{"Sin antecedentes consignados."},END-X-8f,paint(10.5f,INK))
            lines.forEach{line->
                ensure(15f)
                if(line.isNotBlank())canvas.drawText(line,X+4f,top+10f,paint(10.5f,INK))
                top+=14.5f
            }
            top+=9f
        }
        fun photo(path:String) {
            if(path.isBlank())return
            val file=File(path)
            if(!file.isFile)return
            var image:Bitmap?=null
            try{
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeFile(path,bounds)
                var scale=1
                while(bounds.outWidth/scale>1400||bounds.outHeight/scale>1400)scale*=2
                image=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=scale})
                var display=image?:return
                val orientation=ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
                val m=Matrix()
                when(orientation){
                    ExifInterface.ORIENTATION_ROTATE_90->m.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180->m.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270->m.postRotate(270f)
                    else->Unit
                }
                if(!m.isIdentity){
                    val rotated=Bitmap.createBitmap(display,0,0,display.width,display.height,m,true)
                    if(rotated!==display){display.recycle();display=rotated}
                }
                image=display
                val boxWidth=END-X
                val boxHeight=225f
                ensure(boxHeight+22f)
                canvas.drawRect(X,top,X+boxWidth,top+boxHeight,
                    Paint().apply{color=PALE})
                val factor=minOf((boxWidth-14f)/display.width,(boxHeight-12f)/display.height)
                val width=display.width*factor
                val height=display.height*factor
                val left=X+(boxWidth-width)/2
                val upper=top+(boxHeight-height)/2
                canvas.drawBitmap(display,null,RectF(left,upper,left+width,upper+height),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                top+=boxHeight+16f
            }catch(_:Exception){/* invalid image does not prevent generating the report */}
            finally{image?.recycle()}
        }
        fun close(){if(current!=null)finish()}
    }

    fun create(context:Context,visit:AdditionalVisitEntity):File{
        val pdf=PdfDocument()
        try{
            val w=Writer(pdf,VisitCategories.name(visit.kind))
            w.section("1. Antecedentes generales")
            w.key("Tipo de actividad",VisitCategories.name(visit.kind))
            w.key("Asunto",visit.title)
            w.key("Centro o lugar",visit.center)
            if(visit.area.isNotBlank())w.key("Sector",visit.area)
            w.key("Fecha / hora",Instant.ofEpochMilli(visit.visitedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("es","CL"))))
            w.key("Responsable",visit.responsible)
            if(visit.participants.isNotBlank())w.key("Participantes",visit.participants)
            w.section("2. Motivo y desarrollo")
            if(visit.purpose.isNotBlank()){w.key("Objetivo",visit.purpose)}
            w.paragraph(visit.summary)
            if(visit.agreements.isNotBlank()){
                w.section("3. Acuerdos y compromisos")
                w.paragraph(visit.agreements)
                if(visit.agreementOwner.isNotBlank())w.key("Responsable",visit.agreementOwner)
                if(visit.agreementDueIso.isNotBlank())w.key("Fecha de seguimiento",visit.agreementDueIso)
                w.key("Estado",when(visit.agreementStatus){
                    "PENDIENTE"->"Pendiente"
                    "EN_PROCESO"->"En proceso"
                    "CERRADO"->"Cerrado"
                    else->"Sin acuerdos pendientes"
                })
            }
            if(visit.photoPath.isNotBlank()&&File(visit.photoPath).isFile){
                w.section(if(visit.agreements.isNotBlank())"4. Evidencia fotográfica" else "3. Evidencia fotográfica")
                w.photo(visit.photoPath)
            }
            w.close()
            val dir=File(context.cacheDir,"exports").apply{mkdirs()}
            val output=File(dir,"actividad_"+visit.id.take(8)+".pdf")
            FileOutputStream(output).use{pdf.writeTo(it)}
            return output
        }finally{pdf.close()}
    }
}
