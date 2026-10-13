package cl.munistgo.disal.inspecciones

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Documents {
    private val fmt=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("es","CL"))
    private fun date(ms:Long):String=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fmt)
    private fun csvCell(v:Any?):String="\""+(v?.toString() ?: "").replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\""
    private fun exportDir(context:Context)=File(context.cacheDir,"exports").apply { mkdirs() }

    private class PageWriter(private val pdf:PdfDocument, private val title:String) {
        private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(29,51,64); textSize=10f }
        private val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(11,86,90); textSize=15f; isFakeBoldText=true }
        private val linePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.LTGRAY; strokeWidth=0.7f }
        private var page:PdfDocument.Page?=null
        var canvas:Canvas?=null
        private var y=0f
        private var number=0
        init { newPage() }
        private fun newPage() {
            if(page!=null) finish()
            number++
            page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,number).create())
            canvas=page!!.canvas
            y=42f
            canvas!!.drawText("DIRECCIÓN DE SALUD · ILUSTRE MUNICIPALIDAD DE SANTIAGO",32f,28f,titlePaint.apply { textSize=11f })
            titlePaint.textSize=15f
            canvas!!.drawLine(32f,34f,563f,34f,linePaint)
            y=56f
            heading(title)
        }
        fun ensure(height:Float=28f) { if(y+height>790f)newPage() }
        fun heading(value:String) {
            ensure(25f)
            canvas!!.drawText(value.take(80),32f,y,titlePaint)
            y+=22f
        }
        fun line(key:String,value:String) { paragraph(key+": "+value) }
        fun paragraph(value:String) {
            val lines=value.replace("\r","").split("\n")
            for(para in lines) {
                if(para.isBlank()){ensure(13f);y+=8;continue}
                val words=para.split(Regex("\\s+"))
                var row=""
                for(word in words) {
                    val candidate=if(row.isBlank())word else row+" "+word
                    if(textPaint.measureText(candidate)>520f && row.isNotBlank()) {
                        drawLine(row);row=word
                    }else row=candidate
                }
                if(row.isNotEmpty())drawLine(row)
            }
            y+=4f
        }
        private fun drawLine(value:String) {
            ensure(15f)
            canvas!!.drawText(value.take(190),32f,y,textPaint)
            y+=14f
        }
        fun horizontal() { ensure(6f);canvas!!.drawLine(32f,y,563f,y,linePaint);y+=10f }
        fun picture(path:String,label:String,height:Float=150f) {
            if(path.isEmpty())return
            val file=File(path);if(!file.exists())return
            ensure(height+32f)
            line("Evidencia",label)
            val opts=BitmapFactory.Options().apply { inSampleSize=2 }
            val bmp=BitmapFactory.decodeFile(path,opts) ?: return
            val w=520f; val ratio=bmp.width.toFloat()/bmp.height.coerceAtLeast(1)
            val renderedHeight=minOf(height,w/ratio)
            val renderedWidth=renderedHeight*ratio
            val dst=RectF(32f,y,32f+renderedWidth,y+renderedHeight)
            canvas!!.drawBitmap(bmp,null,dst,null)
            y+=renderedHeight+12f
            bmp.recycle()
        }
        private fun finish(){
            val c=canvas ?:return
            textPaint.color=Color.DKGRAY
            c.drawLine(32f,806f,563f,806f,linePaint)
            c.drawText("Borrador interno de inspección | Página "+number,32f,819f,textPaint.apply{textSize=9f})
            textPaint.color=Color.rgb(29,51,64);textPaint.textSize=10f
            pdf.finishPage(page!!)
            page=null;canvas=null
        }
        fun close(){finish()}
    }

    fun inspectionPdf(context:Context, inspection:InspectionEntity, participants:List<ParticipantEntity>, findings:List<FindingEntity>):File {
        val pdf=PdfDocument();try {
            val w=PageWriter(pdf,"Informe de inspección")
            w.line("Tipo de inspección",inspection.title)
            w.line("Centro / establecimiento",inspection.center)
            w.line("Área / sector",inspection.area)
            w.line("Fecha y hora de visita",date(inspection.visitedAt))
            w.line("Inspector responsable",inspection.inspector+" | "+inspection.inspectorRole)
            w.heading("Participantes / acompañantes")
            if(participants.isEmpty())w.paragraph("Sin participantes registrados.")
            for(p in participants)w.paragraph(p.name+" | "+p.role+" | RUT: "+p.rut)
            w.heading("Hallazgos y medidas de control")
            if(findings.isEmpty())w.paragraph("Sin hallazgos registrados.")
            for((i,f) in findings.withIndex()){
                w.ensure(110f);w.horizontal()
                w.heading("Hallazgo "+(i+1)+" · "+f.risk)
                w.line("Ubicación",f.location)
                w.line("Condición insegura",f.unsafeCondition)
                w.line("Peligro",f.hazard)
                w.line("Normativa informada por inspector",f.regulation)
                w.line("Acción correctiva",f.correctiveAction)
                w.line("Responsable implementación",f.implementationOwner)
                w.line("Fecha compromiso",f.deadlineIso)
                w.line("Responsable verificación",f.verificationOwner)
                w.line("Estado",f.status)
                f.closedAt?.let { w.line("Cierre registrado",date(it)) }
                w.picture(f.photoPath,"Fotografía de situación "+(i+1))
            }
            w.heading("Conclusiones del inspector")
            w.paragraph(inspection.conclusions.ifBlank {"Sin conclusiones ingresadas."})
            w.heading("Firmas manuscritas")
            w.picture(inspection.inspectorSignature,"Inspector · "+inspection.inspector,95f)
            w.picture(inspection.receiverSignature,"Receptor · "+inspection.receiverName,95f)
            w.paragraph("Las firmas insertadas son imágenes manuscritas; este PDF no posee firma electrónica avanzada.")
            w.close()
            return File(exportDir(context),"inspeccion_"+inspection.id.take(8)+".pdf").also {
                FileOutputStream(it).use(pdf::writeTo)
            }
        } finally {pdf.close()}
    }

    fun monthlyPdf(context:Context, stats:MonthlyMetrics, inspections:List<InspectionEntity>, findings:List<FindingEntity>):File {
        val pdf=PdfDocument();try {
            val w=PageWriter(pdf,"Resumen mensual de cumplimiento")
            w.line("Período",stats.period.toString())
            w.line("Inspecciones realizadas",stats.inspections.toString())
            w.line("Hallazgos detectados",stats.findings.toString())
            w.line("Riesgos leves / moderados / críticos",""+stats.mild+" / "+stats.moderate+" / "+stats.critical)
            w.line("Cerrados dentro del plazo",stats.onTime.toString())
            w.line("Vencidos o cerrados fuera de plazo",stats.overdue.toString())
            w.line("De los anteriores, cerrados tardíamente",stats.closedLate.toString())
            w.line("Pendientes con plazo vigente",stats.stillCurrent.toString())
            w.line("Sin fecha compromiso",stats.withoutDeadline.toString())
            val pct=stats.compliance?.let { String.format(Locale("es","CL"),"%.1f %%",it) } ?: "No calculable (sin casos evaluables)"
            w.line("Cumplimiento de casos evaluables",pct)
            w.paragraph("Fórmula: cierre oportuno / (cierres oportunos + hallazgos vencidos) × 100. Se consideran hallazgos de inspecciones realizadas en el mes. Los aún vigentes o sin fecha no integran el denominador.")
            w.heading("Hallazgos del período")
            for((inspection,finding) in Metrics.monthlyRows(inspections,findings,stats.period)) {
                w.horizontal()
                w.paragraph(inspection.center+" · "+finding.risk+" · "+finding.status)
                w.paragraph("Hallazgo: "+finding.unsafeCondition)
                w.paragraph("Responsable: "+finding.implementationOwner+" · Plazo: "+finding.deadlineIso)
            }
            w.close()
            return File(exportDir(context),"resumen_"+stats.period.toString()+".pdf").also {
                FileOutputStream(it).use(pdf::writeTo)
            }
        }finally{pdf.close()}
    }

    fun monthlyCsv(context:Context,period:YearMonth,inspections:List<InspectionEntity>,findings:List<FindingEntity>):File {
        val rows=Metrics.monthlyRows(inspections,findings,period)
        val header=listOf("ID inspección","Centro","Fecha visita","Ubicación","Condición","Peligro","Riesgo","Norma","Medida","Responsable","Plazo","Control","Estado","Fecha cierre","Vencido")
        val contents=buildString{
            appendLine(header.joinToString(","){csvCell(it)})
            for((ins,f) in rows) {
                val cols=listOf(ins.id,ins.center,date(ins.visitedAt),f.location,f.unsafeCondition,f.hazard,
                    f.risk,f.regulation,f.correctiveAction,f.implementationOwner,f.deadlineIso,f.verificationOwner,
                    f.status,f.closedAt?.let(::date)?:"",if(Metrics.overdue(f))"Sí" else "No")
                appendLine(cols.joinToString(","){csvCell(it)})
            }
        }
        return File(exportDir(context),"resumen_"+period.toString()+".csv").apply {writeText("\uFEFF"+contents,Charsets.UTF_8)}
    }

    fun share(context:Context,file:File,title:String) {
        val uri=FileProvider.getUriForFile(context,"cl.munistgo.disal.inspecciones.files",file)
        val type=if(file.extension=="pdf")"application/pdf" else "text/csv"
        val intent=Intent(Intent.ACTION_SEND).apply {
            this.type=type
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_SUBJECT,title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,"Compartir documento (revisar destinatarios)"))
    }
}
