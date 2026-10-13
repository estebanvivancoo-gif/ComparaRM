package cl.munistgo.disal.inspecciones

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MonthlyMetrics(
    val period:YearMonth,
    val inspections:Int,
    val findings:Int,
    val mild:Int,
    val moderate:Int,
    val critical:Int,
    val onTime:Int,
    val overdue:Int,
    val closedLate:Int,
    val stillCurrent:Int,
    val withoutDeadline:Int
) {
    val evaluated:Int get()=onTime+overdue
    val compliance:Double? get()=if(evaluated>0)100.0*onTime/evaluated else null
}

object Metrics {
    private fun visitedMonth(millis:Long):YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())

    fun compute(inspections:List<InspectionEntity>,findings:List<FindingEntity>,period:YearMonth,asOf:LocalDate=LocalDate.now()):MonthlyMetrics {
        val inspected=inspections.filter { visitedMonth(it.visitedAt)==period }
        val ids=inspected.map { it.id }.toSet()
        val rows=findings.filter { it.inspectionId in ids }
        var onTime=0; var overdue=0; var late=0; var current=0; var missing=0
        for(row in rows) {
            val deadline=runCatching { LocalDate.parse(row.deadlineIso) }.getOrNull()
            if(deadline==null) { missing++;continue }
            val closeDate=row.closedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            if(row.status=="CERRADO" && closeDate!=null && !closeDate.isAfter(deadline)) {
                onTime++
            } else if(row.status=="CERRADO" && closeDate!=null && closeDate.isAfter(deadline)) {
                overdue++;late++
            } else if(deadline.isBefore(asOf)) {
                overdue++
            } else {
                current++
            }
        }
        return MonthlyMetrics(
            period,inspected.size,rows.size,
            rows.count { it.risk=="LEVE" },rows.count { it.risk=="MODERADO" },rows.count { it.risk=="CRITICO" },
            onTime,overdue,late,current,missing
        )
    }

    fun overdue(row:FindingEntity,asOf:LocalDate=LocalDate.now()):Boolean {
        val date=runCatching { LocalDate.parse(row.deadlineIso) }.getOrNull() ?: return false
        val closed=row.closedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        return (row.status=="CERRADO" && closed!=null && closed.isAfter(date)) ||
            (row.status!="CERRADO" && date.isBefore(asOf))
    }

    fun monthlyRows(inspections:List<InspectionEntity>,findings:List<FindingEntity>,period:YearMonth):List<Pair<InspectionEntity,FindingEntity>> {
        val byId=inspections.filter { visitedMonth(it.visitedAt)==period }.associateBy { it.id }
        return findings.mapNotNull { row -> byId[row.inspectionId]?.let { it to row } }
    }
}
