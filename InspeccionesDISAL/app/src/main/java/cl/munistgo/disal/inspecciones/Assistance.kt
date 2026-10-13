package cl.munistgo.disal.inspecciones

import android.content.Context
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Advice(val hazard:String,val norm:String,val action:String,val sourceUrl:String,val article:String="",val origin:String="Sugerencia técnica local")

object SafetyAdvice {
    const val ds6="https://www.bcn.cl/leychile/navegar?idNorma=1008725"
    const val ds148="https://www.bcn.cl/leychile/navegar?idNorma=226458"
    const val ds594="https://www.bcn.cl/leychile/navegar?idNorma=167766"
    const val ds44="https://www.bcn.cl/leychile/navegar?idNorma=1205298"
    const val law16744="https://www.bcn.cl/leychile/navegar?idNorma=28650"
    private data class Rule(val words:List<String>,val advice:Advice)
    private val rules=listOf(
        Rule(listOf("segreg","mezcl","clasific","separac","residuos peligrosos","reas"),
            Advice("Segregación inadecuada de residuos de atención de salud",
                "D.S. 6/2009 (REAS), art. 8: separación en origen y mantenimiento de la segregación.",
                "Identificar categoría del residuo, segregar en origen, disponer contenedores rotulados y reforzar capacitación del personal.",ds6,"8")),
        Rule(listOf("cortopunz","aguja","jering","bistur","puncion","punción","punzante"),
            Advice("Exposición a lesiones por elementos cortopunzantes potencialmente contaminados",
                "D.S. 6/2009 (REAS), arts. 12 y 16: contenedores rígidos resistentes y retiro/cierre al alcanzar 3/4.",
                "Instalar recipientes rígidos resistentes a punción próximos al punto de uso; cerrar, sellar y retirar al llegar a 3/4 de capacidad.",ds6,"16")),
        Rule(listOf("contenedor","basurero","recipiente","tapa","rotulad","bolsa","color amarillo"),
            Advice("Contacto con residuos o riesgo de derrame por contenedor inadecuado",
                "D.S. 6/2009 (REAS), arts. 12–14: condiciones, identificación y uso de contenedores y bolsas.",
                "Reponer contenedores apropiados, rotular la dependencia, utilizar bolsas resistentes y revisar condiciones de cierre.",ds6,"12")),
        Rule(listOf("3/4","tres cuartos","sobrellen","sobre llen","rebals","lleno","al tope"),
            Advice("Derrame, contacto o punción por sobrellenado de contenedores REAS",
                "D.S. 6/2009 (REAS), art. 16: retiro de contenedores especiales y cierre de cortopunzantes al alcanzar 3/4.",
                "Cerrar y retirar oportunamente recipientes sobrellenados; ajustar frecuencia de retiro e instruir al personal.",ds6,"16")),
        Rule(listOf("traslado","transporte interno","carro reas","ruta de residuos","retiro"),
            Advice("Exposición ocupacional y del público durante traslado interno de residuos",
                "D.S. 6/2009 (REAS), arts. 15–19: procedimientos seguros de retiro y traslado interno.",
                "Definir recorridos y horarios seguros, controlar cierre de contenedores y utilizar carros aptos, lavables y estables.",ds6,"15")),
        Rule(listOf("sala reas","sala de residuos","almacenamiento","bodega de residuos","área reas","area reas"),
            Advice("Exposición por condiciones de almacenamiento y segregación insuficientes",
                "D.S. 6/2009 (REAS), arts. 21–23: área o sala de almacenamiento y condiciones de operación.",
                "Separar y señalizar categorías; revisar autorización sanitaria, accesos, superficies, ventilación, limpieza y registros.",ds6,"22")),
        Rule(listOf("pilas","batería","bateria","solvente","citotóxic","citotoxic","químic","quimic","farmaco vencido","medicamento vencido"),
            Advice("Exposición a residuos con peligrosidad química y mezcla entre categorías",
                "D.S. 6/2009 (REAS), art. 3 y 8; D.S. 148/2003 (residuos peligrosos), según clasificación.",
                "Separar residuos peligrosos de especiales, identificar peligrosidad, rotular y disponer mediante gestor autorizado.",ds148)),
        Rule(listOf("piso","derrame","resbal","tropez","humedad","desnivel"),
            Advice("Caída al mismo nivel por superficies defectuosas o resbaladizas",
                "D.S. 594, art. 5 (condiciones de pisos); verificar pertinencia.",
                "Demarcar el sector, eliminar derrames y obstáculos, reparar superficies y comprobar seguridad de tránsito.",ds594,"5")),
        Rule(listOf("epp","guant","mascarill","respirador","antiparra","protección personal"),
            Advice("Exposición laboral por equipo de protección inadecuado",
                "D.S. 594, art. 53; evaluar su aplicación según peligro.",
                "Evaluar exposición, priorizar controles colectivos y proveer EPP adecuado con capacitación y reposición.",ds594,"53")),
        Rule(listOf("electric","eléctri","enchufe","cable pelado","cortocircuit"),
            Advice("Contacto eléctrico directo o indirecto e incendio",
                "D.S. 44, arts. 7 y 12 (gestión preventiva); validar norma técnica complementaria.",
                "Aislar el equipo, impedir su uso y requerir revisión y reparación por personal autorizado.",ds44,"7")),
        Rule(listOf("salida emergencia","evacuación","extintor","incendio","vía de escape"),
            Advice("Demora en evacuación o insuficiencia de respuesta ante incendio",
                "D.S. 594 y D.S. 44, según condiciones y plan de emergencia.",
                "Despejar accesos y vías, revisar señalización y medios de control e incorporar verificación periódica.",ds594))
    )
    fun local(condition:String):List<Advice> {
        val value=condition.lowercase()
        if(value.isBlank())return emptyList()
        val hits=rules.filter{rule->rule.words.any{value.contains(it)}}.map{it.advice}.distinctBy{it.hazard}
        return (if(hits.isEmpty())listOf(Advice(
            "Peligro por determinar según actividad y exposición",
            "D.S. 44: identificación de peligros y evaluación de riesgos; verificar regulación específica.",
            "Evaluar el riesgo en terreno, aplicar controles según jerarquía preventiva y comprobar efectividad.",ds44)) else hits).take(5)
    }
    // Consulta de disponibilidad de la norma oficial, NO verificación jurídica del artículo.
    fun checkBcn(advice:Advice):String {
        var connection:HttpURLConnection?=null
        return try {
            val url=URL(advice.sourceUrl)
            require(url.protocol=="https" && url.host=="www.bcn.cl")
            connection=(url.openConnection() as HttpURLConnection).apply{
                connectTimeout=10000;readTimeout=10000
                setRequestProperty("User-Agent","InspeccionesDISAL/0.3")
                instanceFollowRedirects=true
            }
            val status=connection.responseCode
            if(status!=200)"BCN no disponible (HTTP "+status+")."
            else {
                val buffer=connection.inputStream.bufferedReader().use{it.readText().take(200000)}
                if(buffer.isEmpty())"BCN respondió sin contenido legible."
                else "BCN respondió. Abre la fuente para revisar texto vigente y artículo exacto; consulta no acredita pertinencia."
            }
        }catch(e:Exception){"No se pudo consultar BCN ("+e.javaClass.simpleName+"). Utiliza el enlace cuando tengas señal."}
        finally{connection?.disconnect()}
    }
}

/** La nube es optativa: exige credencial del usuario, autorización de la organización y pulsar 'Consultar IA'. */
object AiAdvisor {
    fun suggest(key:String,condition:String):Advice {
        require(key.isNotBlank()){"Falta clave API autorizada"}
        require(condition.trim().length>=10){"Describe la condición antes de consultar"}
        var conn:HttpURLConnection?=null
        try{
            conn=URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod="POST";conn.doOutput=true
            conn.connectTimeout=10000;conn.readTimeout=20000
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            conn.setRequestProperty("Authorization","Bearer "+key)
            val instructions="Actúa como profesional asesor de prevención de riesgos en APS Chile. " +
                "Analiza condición y devuelve SOLO objeto JSON con claves hazard,norm,action. " +
                "Considera especialmente D.S. 6/2009 REAS, D.S. 148/2003, D.S. 594, D.S. 44 y Ley 16.744. " +
                "Nunca inventes artículos o citas literales; aclara que se debe revisar BCN. " +
                "Sugiere medidas técnicas concretas; no afirmes que estás verificando normativa oficial."
            val payload=JSONObject()
                .put("model","gpt-4.1-mini").put("store",false)
                .put("response_format",JSONObject().put("type","json_object"))
                .put("messages",JSONArray()
                    .put(JSONObject().put("role","system").put("content",instructions))
                    .put(JSONObject().put("role","user")
                        .put("content","Condición observada: "+condition.take(900))))
            conn.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
            if(conn.responseCode!=200)throw IllegalStateException("Error de servicio HTTP "+conn.responseCode)
            val answer=JSONObject(conn.inputStream.bufferedReader().use{it.readText()})
                .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            val obj=JSONObject(answer)
            return Advice(
                obj.optString("hazard","").take(500),
                obj.optString("norm","").take(500),
                obj.optString("action","").take(700),
                "https://www.bcn.cl/leychile","",
                "IA online · propuesta no verificada"
            )
        }finally{conn?.disconnect()}
    }
}
