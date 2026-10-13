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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * A4 executive PDFs. Page geometry in PDF points (595x842).
 * Printable typography, repeating headers/footers, dynamic rows,
 * preserved photographic proportions and neutral black-on-white signatures.
 */
object ExecutiveReports {
    private const val WIDTH=595f
    private const val LEFT=42f
    private const val RIGHT=553f
    private const val BODY_BOTTOM=775f
    private const val NAVY=0xFF153B5A.toInt()
    private const val BLUE=0xFF1D5E88.toInt()
    private const val INK=0xFF22323D.toInt()
    private const val MUTED=0xFF5D6B75.toInt()
    private const val LINE=0xFFD9E2E8.toInt()
    private const val PALE=0xFFF1F5F8.toInt()

    private val dateFormat=DateTimeFormatter.ofPattern("dd/MM/yyyy",Locale("es","CL"))
    private val dateTimeFormat=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("es","CL"))
    private fun date(time:Long,withTime:Boolean=true):String {
        val local=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
        return local.format(if(withTime)dateTimeFormat else dateFormat)
    }
    private fun exportDir(context:Context)=File(context.cacheDir,"exports").apply{mkdirs()}
    private fun textPaint(size:Float=10f,color:Int=INK,bold:Boolean=false):Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply{
            this.color=color;textSize=size
            typeface=if(bold)Typeface.create("sans-serif-medium",Typeface.NORMAL)
                else Typeface.create("sans-serif",Typeface.NORMAL)
        }
    private fun wrap(value:String,width:Float,paint:Paint):List<String> {
        val clean=value.replace("\r","").ifBlank{"—"}
        val rows=mutableListOf<String>()
        for(para in clean.split("\n")) {
            if(para.isBlank()){rows.add("");continue}
            var line=""
            for(word in para.trim().split(Regex("\\s+"))) {
                val test=if(line.isEmpty())word else line+" "+word
                if(line.isNotEmpty() && paint.measureText(test)>width){
                    rows.add(line)
                    line=word
                }else line=test
                while(paint.measureText(line)>width && line.length>1){
                    var n=line.length-1
                    while(n>1&&paint.measureText(line.take(n))>width)n--
                    rows.add(line.take(n))
                    line=line.drop(n)
                }
            }
            rows.add(line)
        }
        return rows
    }
    private fun rect(c:Canvas,x:Float,top:Float,w:Float,h:Float,color:Int,radius:Float=0f){
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color}
        if(radius>0f)c.drawRoundRect(RectF(x,top,x+w,top+h),radius,radius,p)
        else c.drawRect(x,top,x+w,top+h,p)
    }

    private class Page(private val pdf:PdfDocument,private val title:String,private val subTitle:String){
        private var current:PdfDocument.Page?=null
        private var pageNo=0
        private var y=169f
        val c:Canvas get()=requireNotNull(current).canvas
        init {start()}
        private fun start(){
            if(current!=null)finish()
            pageNo++
            current=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
            c.drawColor(Color.WHITE)
            rect(c,0f,0f,595f,90f,NAVY)
            rect(c,LEFT,22f,58f,51f,Color.WHITE,3f)
            val mark=textPaint(8f,NAVY,true)
            c.drawText("LOGO",LEFT+17f,42f,mark)
            c.drawText("OFICIAL",LEFT+12f,55f,mark)
            c.drawText("DIRECCIÓN DE SALUD",113f,45f,textPaint(15f,Color.WHITE,true))
            c.drawText("ILUSTRE MUNICIPALIDAD DE SANTIAGO",113f,64f,textPaint(9.6f,0xFFE4EFF8.toInt()))
            c.drawText(title,LEFT,124f,textPaint(18f,NAVY,true))
            c.drawText(subTitle.take(95),LEFT,144f,textPaint(10f,MUTED))
            rect(c,LEFT,153f,RIGHT-LEFT,1.5f,BLUE)
            y=175f
        }
        private fun finish(){
            rect(c,LEFT,802f,RIGHT-LEFT,1f,LINE)
            c.drawText("Documento técnico de trabajo · Validar antes de distribuir",LEFT,818f,
                textPaint(8f,MUTED))
            c.drawText("Página "+pageNo,RIGHT-46f,818f,textPaint(8f,MUTED))
            pdf.finishPage(requireNotNull(current))
            current=null
        }
        fun close(){if(current!=null)finish()}
        fun ensure(height:Float){if(y+height>BODY_BOTTOM)start()}
        fun gap(pts:Float=9f){ensure(pts);y+=pts}
        fun section(title:String){
            ensure(35f)
            rect(c,LEFT,y-2f,RIGHT-LEFT,25f,PALE)
            rect(c,LEFT,y-2f,3f,25f,BLUE)
            c.drawText(title.uppercase(),LEFT+11f,y+15f,textPaint(10.4f,NAVY,true))
            y+=35f
        }
        fun kv(label:String,value:String){
            val lpaint=textPaint(9.4f,MUTED,true)
            val vpaint=textPaint(10.5f,INK)
            val valueLines=wrap(value,354f,vpaint)
            val h=max(28f,valueLines.size*14f+12f)
            ensure(h)
            rect(c,LEFT,y-2f,RIGHT-LEFT,h-1f,if((y.toInt()/28)%2==0)0xFFF8FAFB.toInt() else Color.WHITE)
            c.drawText(label.take(31),LEFT+7f,y+13f,lpaint)
            valueLines.forEachIndexed{i,row->
                c.drawText(row,LEFT+157f,y+13f+i*14f,vpaint)
            }
            rect(c,LEFT,y+h-3f,RIGHT-LEFT,.6f,LINE)
            y+=h
        }
        fun paragraph(body:String){
            val p=textPaint(10.5f,INK)
            for(line in wrap(body,RIGHT-LEFT-8f,p)){
                ensure(14f)
                if(line.isNotBlank())c.drawText(line,LEFT+4f,y+10f,p)
                y+=14f
            }
            gap(5f)
        }
        fun statStrip(values:List<Pair<String,String>>){
            if(values.isEmpty())return
            val w=(RIGHT-LEFT-(values.size-1)*9f)/values.size
            ensure(70f)
            values.forEachIndexed{index,item->
                val x=LEFT+index*(w+9f)
                rect(c,x,y,w,65f,PALE,5f)
                rect(c,x,y,3f,65f,BLUE)
                c.drawText(item.second.take(14),x+11f,y+31f,textPaint(21f,NAVY,true))
                c.drawText(item.first.take(25),x+11f,y+49f,textPaint(8.4f,MUTED))
            }
            y+=78f
        }
        fun table(headers:List<String>,widths:List<Float>,rows:List<List<String>>){
            require(headers.size==widths.size)
            val normal=textPaint(9.3f,INK)
            val heading=textPaint(8.9f,NAVY,true)
            fun header(){
                ensure(29f)
                rect(c,LEFT,y,RIGHT-LEFT,27f,0xFFDCEAF3.toInt())
                var x=LEFT
                headers.forEachIndexed{i,label->
                    c.drawText(label.take(35),x+5f,y+17f,heading)
                    x+=widths[i]
                }
                y+=29f
            }
            header()
            for((index,values) in rows.withIndex()){
                val wrapped=widths.indices.map{i->
                    wrap(values.getOrElse(i){""},widths[i]-11f,normal)
                }
                val rowHeight=max(29f,wrapped.maxOf{it.size}*12f+13f)
                if(y+rowHeight>BODY_BOTTOM){start();header()}
                rect(c,LEFT,y,RIGHT-LEFT,rowHeight-1f,
                    if(index%2==0)Color.WHITE else 0xFFF5F8FA.toInt())
                var x=LEFT
                wrapped.forEachIndexed{col,lines->
                    lines.forEachIndexed{idx,line->
                        c.drawText(line,x+5f,y+14f+idx*12f,normal)
                    }
                    x+=widths[col]
                }
                rect(c,LEFT,y+rowHeight-1f,RIGHT-LEFT,.6f,LINE)
                y+=rowHeight
            }
            gap(9f)
        }
        private fun loadPhoto(path:String):Bitmap? {
            val file=File(path)
            if(path.isBlank()||!file.isFile)return null
            return try{
                val opt=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeFile(path,opt)
                var sample=1
                while(opt.outWidth/sample>1400 || opt.outHeight/sample>1400)sample*=2
                val loaded=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample}) ?:return null
                val orientation=ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)
                val matrix=Matrix()
                when(orientation){
                    ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f)
                    else->Unit
                }
                if(matrix.isIdentity)loaded
                else Bitmap.createBitmap(loaded,0,0,loaded.width,loaded.height,matrix,true).also{loaded.recycle()}
            }catch(_:Exception){null}
        }
        private fun photoFrame(path:String,x:Float,top:Float,w:Float,h:Float){
            rect(c,x,top,w,h,0xFFEEF2F5.toInt(),3f)
            val bitmap=loadPhoto(path)
            if(bitmap!=null){
                try{
                    val factor=minOf((w-10f)/bitmap.width,(h-10f)/bitmap.height)
                    val dw=bitmap.width*factor;val dh=bitmap.height*factor
                    val frame=RectF(x+(w-dw)/2,top+(h-dh)/2,
                        x+(w+dw)/2,top+(h+dh)/2)
                    c.drawBitmap(bitmap,null,frame,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                }finally{bitmap.recycle()}
            }else{
                c.drawText("Sin fotografía",x+36f,top+h/2,textPaint(10f,MUTED))
            }
        }
        fun finding(index:Int,total:Int,item:FindingEntity){
            ensure(233f)
            val risk=when(item.risk){
                "CRITICO"->0xFFAF3640.toInt()
                "MODERADO"->0xFF996614.toInt()
                else->0xFF2F765C.toInt()
            }
            rect(c,LEFT,y,RIGHT-LEFT,33f,PALE,4f)
            c.drawText("HALLAZGO "+index.toString().padStart(2,'0')+" / "+
                total.toString().padStart(2,'0'),LEFT+10f,y+21f,textPaint(11f,NAVY,true))
            rect(c,RIGHT-115f,y+5f,105f,23f,risk,4f)
            c.drawText(item.risk,RIGHT-104f,y+21f,textPaint(10f,Color.WHITE,true))
            y+=44f
            photoFrame(item.photoPath,LEFT,y,188f,140f)
            val x=LEFT+199f
            val w=RIGHT-x-2f
            var miniIndex=0
            fun mini(label:String,value:String,limit:Int){
                val maxRows=wrap(value,w-2f,textPaint(9.3f,INK))
                c.drawText(label,x,y+12f+miniIndex*30f,textPaint(8.1f,BLUE,true))
                maxRows.take(limit).forEachIndexed{i,row->
                    c.drawText(row,x,y+25f+miniIndex*30f+i*11f,
                        textPaint(9.3f,INK))
                }
                miniIndex+=if(maxRows.size>1 && limit>1)2 else 1
            }
            mini("UBICACIÓN",item.location.ifBlank{"No indicada"},1)
            mini("RIESGO Y ESTADO",item.risk+" / "+item.status,1)
            mini("RESPONSABLE",item.implementationOwner.ifBlank{"Por asignar"},1)
            mini("FECHA LÍMITE",item.deadlineIso.ifBlank{"Sin plazo"},1)
            y+=152f
            kv("Condición observada",item.unsafeCondition)
            kv("Peligro identificado",item.hazard)
            kv("Normativa referencial",item.regulation)
            kv("Medida correctiva",item.correctiveAction)
            kv("Responsable de ejecución",item.implementationOwner)
            kv("Verificación y control",item.verificationOwner)
            if(item.closedAt!=null)kv("Fecha de cierre",date(item.closedAt))
            gap(18f)
        }
        private fun monoSignature(path:String):Bitmap?{
            if(path.isBlank()||!File(path).exists())return null
            return try{
                val opt=BitmapFactory.Options().apply{inSampleSize=2}
                val loaded=BitmapFactory.decodeFile(path,opt)?:return null
                val w=loaded.width;val h=loaded.height
                val pixels=IntArray(w*h)
                loaded.getPixels(pixels,0,w,0,0,w,h)
                for(i in pixels.indices){
                    val col=pixels[i]
                    val r=Color.red(col);val g=Color.green(col);val b=Color.blue(col)
                    val stroke=(g>75&&b>95&&(g-r)>48&&(b-r)>50)
                    pixels[i]=if(stroke)Color.BLACK else Color.WHITE
                }
                val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels,0,w,0,0,w,h)
                loaded.recycle()
                bitmap
            }catch(_:Exception){null}
        }
        fun signatures(responsible:String,responsibleName:String,receiver:String,receiverName:String){
            ensure(150f)
            val boxWidth=247f
            val boxes=listOf(LEFT to Pair(responsible,responsibleName),
                LEFT+264f to Pair(receiver,receiverName))
            for((x,signature) in boxes){
                rect(c,x,y,boxWidth,103f,Color.WHITE,3f)
                val bitmap=monoSignature(signature.first)
                if(bitmap!=null){
                    try{
                        val scale=minOf((boxWidth-14f)/bitmap.width,81f/bitmap.height)
                        val dw=bitmap.width*scale;val dh=bitmap.height*scale
                        c.drawBitmap(bitmap,null,RectF(x+(boxWidth-dw)/2f,y+3f,
                            x+(boxWidth+dw)/2f,y+3f+dh),null)
                    }finally{bitmap.recycle()}
                }
                rect(c,x+12f,y+101f,boxWidth-24f,.9f,INK)
                c.drawText(if(x==LEFT)"RESPONSABLE" else "RECEPTOR",
                    x+10f,y+117f,textPaint(8.7f,BLUE,true))
                val label=wrap(signature.second.ifBlank{"No informado"},boxWidth-16f,
                    textPaint(9.1f,INK)).firstOrNull().orEmpty()
                c.drawText(label,x+10f,y+132f,textPaint(9.1f,INK))
            }
            y+=148f
        }
    }

    fun individual(context:Context,inspection:InspectionEntity,
        participants:List<ParticipantEntity>,findings:List<FindingEntity>):File{
        val pdf=PdfDocument()
        try{
            val writer=Page(pdf,"INFORME TÉCNICO DE INSPECCIÓN",
                "Visita de higiene y seguridad laboral · Documento individual")
            writer.section("1. Antecedentes de la visita")
            writer.kv("Tipo / materia",inspection.title)
            writer.kv("Establecimiento",inspection.center)
            writer.kv("Área / sector",inspection.area)
            writer.kv("Fecha y hora",date(inspection.visitedAt))
            writer.kv("Responsable",inspection.inspector+" · "+inspection.inspectorRole)
            writer.section("2. Participantes")
            if(participants.isEmpty())writer.paragraph("Sin acompañantes registrados.")
            else participants.forEach{
                writer.paragraph(it.name+" · "+it.role+
                    (if(it.rut.isNotBlank())" · RUT: "+it.rut else ""))
            }
            writer.section("3. Hallazgos y plan de acción")
            writer.statStrip(listOf(
                "Hallazgos" to findings.size.toString(),
                "Críticos" to findings.count{it.risk=="CRITICO"}.toString(),
                "Pendientes" to findings.count{it.status!="CERRADO"}.toString()))
            if(findings.isEmpty())writer.paragraph("Sin hallazgos documentados.")
            else findings.forEachIndexed{index,item->writer.finding(index+1,findings.size,item)}
            writer.section("4. Conclusiones y seguimiento")
            writer.paragraph(inspection.conclusions.ifBlank{"Sin conclusiones consignadas."})
            writer.section("5. Firmas de recepción")
            writer.signatures(inspection.inspectorSignature,inspection.inspector,
                inspection.receiverSignature,inspection.receiverName)
            writer.paragraph("Las firmas manuscritas se incorporan en blanco y negro. "+
                "Este documento no constituye firma electrónica avanzada.")
            writer.close()
            val output=File(exportDir(context),"informe_ejecutivo_"+inspection.id.take(8)+".pdf")
            FileOutputStream(output).use{pdf.writeTo(it)}
            return output
        }finally{pdf.close()}
    }

    fun monthly(context:Context,period:YearMonth,inspections:List<InspectionEntity>,
        findings:List<FindingEntity>):File{
        val rows=CenterCatalog.rows(context,period,inspections,findings)
        val planned=rows.filter{it.programmed}
        val visited=planned.count{it.visited}
        val without=planned.size-visited
        val extras=rows.count{!it.programmed}
        val metric=Metrics.compute(inspections,findings,period)
        val month=period.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale("es","CL")))
        val coverage=if(planned.isEmpty())"N/D"
            else String.format(Locale("es","CL"),"%.0f%%",100.0*visited/planned.size)
        val compliance=metric.compliance?.let{
            String.format(Locale("es","CL"),"%.1f%%",it)
        }?:"N/D"
        val pdf=PdfDocument()
        try{
            val w=Page(pdf,"CONSOLIDADO EJECUTIVO MENSUAL",
                "Cobertura de inspecciones de establecimientos · "+month)
            w.section("1. Indicadores ejecutivos")
            w.statStrip(listOf("Visitas" to metric.inspections.toString(),
                "Centros visitados" to visited.toString(),
                "Sin visita" to without.toString()))
            w.statStrip(listOf("Cobertura de centros" to coverage,
                "Hallazgos" to metric.findings.toString(),
                "Críticos" to metric.critical.toString()))
            w.kv("Cierre oportuno de hallazgos",compliance)
            w.kv("Hallazgos pendientes",findings.count{
                item->item.inspectionId in inspections.filter{
                    ins->YearMonth.from(Instant.ofEpochMilli(ins.visitedAt)
                        .atZone(ZoneId.systemDefault()).toLocalDate())==period
                }.map{it.id}.toSet() && item.status!="CERRADO"
            }.toString())
            w.paragraph("La cobertura se calcula sobre el catálogo de establecimientos "+
                "programados en Configuración. Los centros no incluidos en ese catálogo "+
                "se muestran como visitas adicionales, sin alterar el denominador.")
            w.section("2. Comparativo por establecimiento")
            w.table(listOf("Establecimiento","Visitas","Estado","Hallazgos","Abiertos"),
                listOf(218f,50f,99f,73f,71f),
                rows.map{listOf(it.center+(if(it.programmed)"" else " *"),
                    it.visits.size.toString(),
                    if(it.visited)"Realizada" else "Sin visita",
                    it.findings.size.toString(),it.pending.toString())})
            if(extras>0)w.paragraph("* Centro con visitas no incluido en el catálogo programado.")
            w.section("3. Detalle de informes emitidos en el período")
            val inspected=inspections.filter{
                YearMonth.from(Instant.ofEpochMilli(it.visitedAt)
                    .atZone(ZoneId.systemDefault()).toLocalDate())==period
            }.sortedBy{it.visitedAt}
            if(inspected.isEmpty())w.paragraph("No hay informes registrados para el período.")
            else w.table(listOf("Fecha","Centro","Tipo de inspección","Hall.","Abiertos"),
                listOf(76f,168f,171f,45f,51f),
                inspected.map{ins->
                    val related=findings.filter{it.inspectionId==ins.id}
                    listOf(date(ins.visitedAt,false),ins.center,ins.title,
                        related.size.toString(),related.count{it.status!="CERRADO"}.toString())
                })
            w.section("4. Criterios de lectura")
            w.paragraph("«Realizada» indica al menos una visita registrada durante el mes; "+
                "«Sin visita» indica que no hay un informe asociado al establecimiento programado. "+
                "No equivale a incumplimiento hasta validar la programación oficial.")
            w.paragraph("Cierre oportuno de hallazgos = cierres dentro del plazo / "+
                "(cierres dentro del plazo + vencidos), de los hallazgos de visitas del mes. "+
                "Los pendientes vigentes no integran ese denominador.")
            w.close()
            val output=File(exportDir(context),"consolidado_ejecutivo_"+period+".pdf")
            FileOutputStream(output).use{pdf.writeTo(it)}
            return output
        }finally{pdf.close()}
    }
    private fun cell(value:String)="\""+value.replace("\"","\"\"").replace("\n"," ")+ "\""
    fun monthlyCsv(context:Context,period:YearMonth,
        inspections:List<InspectionEntity>,findings:List<FindingEntity>):File{
        val rows=CenterCatalog.rows(context,period,inspections,findings)
        val contents=buildString{
            append("\uFEFF")
            appendLine(listOf("Período","Establecimiento","Programado","Visita realizada",
                "N.º de visitas","N.º de hallazgos","Críticos","Abiertos")
                .joinToString(","){cell(it)})
            rows.forEach{item->
                val cols=listOf(period.toString(),item.center,
                    if(item.programmed)"Sí" else "No",
                    if(item.visited)"Sí" else "No",
                    item.visits.size.toString(),item.findings.size.toString(),
                    item.critical.toString(),item.pending.toString())
                appendLine(cols.joinToString(","){cell(it)})
            }
            appendLine()
            appendLine("DETALLE DE VISITAS")
            appendLine(listOf("Fecha","Centro","Materia","Hallazgos","Abiertos")
                .joinToString(","){cell(it)})
            val visits=inspections.filter{
                YearMonth.from(Instant.ofEpochMilli(it.visitedAt)
                    .atZone(ZoneId.systemDefault()).toLocalDate())==period
            }.sortedBy{it.visitedAt}
            visits.forEach{ins->
                val related=findings.filter{it.inspectionId==ins.id}
                appendLine(listOf(date(ins.visitedAt),ins.center,ins.title,
                    related.size.toString(),related.count{it.status!="CERRADO"}.toString())
                    .joinToString(","){cell(it)})
            }
        }
        return File(exportDir(context),"comparativo_centros_"+period+".csv").apply{
            writeText(contents,Charsets.UTF_8)
        }
    }
}
