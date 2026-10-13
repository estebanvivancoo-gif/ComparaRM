package cl.munistgo.disal.inspecciones

import android.content.Context
import java.text.Normalizer
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class CenterCoverage(
    val center:String,
    val programmed:Boolean,
    val visits:List<InspectionEntity>,
    val findings:List<FindingEntity>
) {
    val visited:Boolean get()=visits.isNotEmpty()
    val critical:Int get()=findings.count{it.risk=="CRITICO"}
    val pending:Int get()=findings.count{it.status!="CERRADO"}
    val status:String get()=if(visited)"VISITADO" else "SIN VISITA"
}

object CenterCatalog {
    private val defaultCenters=listOf(
        "CESFAM Ignacio Domeyko",
        "CESFAM Matta Sur",
        "CESFAM Padre Orellana",
        "CESFAM Arauco",
        "COSAM Vicuña Mackenna",
        "Oficina Central DISAL"
    )
    private const val pref="monthly_center_catalog"
    private const val field="expected_centers"

    fun planned(context:Context):List<String> {
        val lines=context.getSharedPreferences(pref,Context.MODE_PRIVATE)
            .getString(field,defaultCenters.joinToString("\n"))
            .orEmpty()
        return lines.lines().map{it.trim()}.filter{it.isNotEmpty()}
            .distinctBy{key(it)}.take(120)
    }
    fun save(context:Context,raw:String) {
        val values=raw.lines().map{it.trim()}.filter{it.isNotEmpty()}
            .distinctBy{key(it)}.take(120)
        context.getSharedPreferences(pref,Context.MODE_PRIVATE).edit()
            .putString(field,values.joinToString("\n")).apply()
    }
    private fun key(s:String):String {
        val plain=Normalizer.normalize(s.lowercase().trim(),Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9]")," ")
            .replace(Regex("\\s+")," ").trim()
        return plain.replace("cesfam ignacio domeyko","cesfam domeyko")
            .replace("direccion de salud","disal")
            .replace("oficina central de la disal","oficina central disal")
    }
    fun rows(context:Context,period:YearMonth,
        inspections:List<InspectionEntity>,allFindings:List<FindingEntity>):List<CenterCoverage> {
        val visits=inspections.filter {
            YearMonth.from(Instant.ofEpochMilli(it.visitedAt)
                .atZone(ZoneId.systemDefault()).toLocalDate())==period
        }
        val names=planned(context)
        val used=mutableSetOf<String>()
        val result=names.map {name ->
            val matched=visits.filter{key(it.center)==key(name)}
            used.addAll(matched.map{it.id})
            val ids=matched.map{it.id}.toSet()
            CenterCoverage(name,true,matched,allFindings.filter{it.inspectionId in ids})
        }.toMutableList()
        val extras=visits.filter{it.id !in used && it.center.isNotBlank()}
            .groupBy{key(it.center)}
        for((_,group) in extras){
            val ids=group.map{it.id}.toSet()
            result.add(CenterCoverage(group.first().center,false,group,
                allFindings.filter{it.inspectionId in ids}))
        }
        return result
    }
}
