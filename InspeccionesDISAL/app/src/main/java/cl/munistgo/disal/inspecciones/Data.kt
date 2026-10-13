package cl.munistgo.disal.inspecciones

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName="inspections")
data class InspectionEntity(
    @PrimaryKey val id:String=UUID.randomUUID().toString(),
    val title:String="Inspección de Higiene y Seguridad",
    val center:String="",
    val area:String="",
    val visitedAt:Long=System.currentTimeMillis(),
    val inspector:String="",
    val inspectorRole:String="Ingeniero en Prevención de Riesgos",
    val conclusions:String="",
    val inspectorSignature:String="",
    val receiverSignature:String="",
    val receiverName:String=""
)

@Entity(
    tableName="participants",
    foreignKeys=[ForeignKey(entity=InspectionEntity::class,parentColumns=["id"],childColumns=["inspectionId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("inspectionId")]
)
data class ParticipantEntity(
    @PrimaryKey val id:String=UUID.randomUUID().toString(),
    val inspectionId:String,
    val name:String,
    val role:String,
    val rut:String
)

@Entity(
    tableName="findings",
    foreignKeys=[ForeignKey(entity=InspectionEntity::class,parentColumns=["id"],childColumns=["inspectionId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("inspectionId")]
)
data class FindingEntity(
    @PrimaryKey val id:String=UUID.randomUUID().toString(),
    val inspectionId:String,
    val createdAt:Long=System.currentTimeMillis(),
    val location:String="",
    val photoPath:String="",
    val unsafeCondition:String="",
    val hazard:String="",
    val risk:String="MODERADO",
    val regulation:String="",
    val correctiveAction:String="",
    val implementationOwner:String="",
    val deadlineIso:String="",
    val verificationOwner:String="",
    val status:String="PENDIENTE",
    val closedAt:Long?=null
)

@Entity(
    tableName="finding_events",
    foreignKeys=[ForeignKey(entity=FindingEntity::class,parentColumns=["id"],childColumns=["findingId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("findingId")]
)
data class FindingEventEntity(
    @PrimaryKey val id:String=UUID.randomUUID().toString(),
    val findingId:String,
    val previousStatus:String,
    val newStatus:String,
    val changedAt:Long=System.currentTimeMillis(),
    val note:String=""
)


/** Visitas complementarias: reuniones CPHS, emergencias y gestiones técnicas.
 * No generan hallazgos de seguridad ni alteran las métricas de inspecciones.
 */
@Entity(tableName="other_visits")
data class AdditionalVisitEntity(
    @PrimaryKey val id:String=UUID.randomUUID().toString(),
    val kind:String="TECNICA",
    val title:String="",
    val center:String="",
    val area:String="",
    val visitedAt:Long=System.currentTimeMillis(),
    val responsible:String="",
    val participants:String="",
    val purpose:String="",
    val summary:String="",
    val agreements:String="",
    val agreementOwner:String="",
    val agreementDueIso:String="",
    val agreementStatus:String="SIN_ACUERDOS",
    val photoPath:String=""
)

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY visitedAt DESC") fun watchInspections():Flow<List<InspectionEntity>>
    @Query("SELECT * FROM findings ORDER BY createdAt DESC") fun watchAllFindings():Flow<List<FindingEntity>>
    @Query("SELECT * FROM findings WHERE inspectionId=:id ORDER BY createdAt DESC") fun watchFindings(id:String):Flow<List<FindingEntity>>
    @Query("SELECT * FROM inspections WHERE id=:id LIMIT 1") suspend fun inspection(id:String):InspectionEntity?
    @Query("SELECT * FROM findings WHERE inspectionId=:id ORDER BY createdAt") suspend fun findings(id:String):List<FindingEntity>
    @Query("SELECT * FROM participants WHERE inspectionId=:id") suspend fun participants(id:String):List<ParticipantEntity>
    @Query("SELECT * FROM finding_events WHERE findingId=:id ORDER BY changedAt") suspend fun events(id:String):List<FindingEventEntity>
    @Query("SELECT * FROM other_visits ORDER BY visitedAt DESC") fun watchAdditionalVisits():Flow<List<AdditionalVisitEntity>>
    @Query("SELECT * FROM other_visits WHERE id=:id LIMIT 1") suspend fun additionalVisit(id:String):AdditionalVisitEntity?
    @Upsert suspend fun saveAdditionalVisit(entry:AdditionalVisitEntity)
    @Upsert suspend fun saveInspection(entry:InspectionEntity)
    @Upsert suspend fun saveFinding(entry:FindingEntity)
    @Insert suspend fun addParticipant(entry:ParticipantEntity)
    @Insert suspend fun addEvent(entry:FindingEventEntity)
}

@Database(
    entities=[InspectionEntity::class,ParticipantEntity::class,FindingEntity::class,FindingEventEntity::class,AdditionalVisitEntity::class],
    version=2,exportSchema=false
)
abstract class InspectionDatabase:RoomDatabase() {
    abstract fun dao():InspectionDao
    companion object {
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS other_visits (
                    id TEXT NOT NULL PRIMARY KEY,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    center TEXT NOT NULL,
                    area TEXT NOT NULL,
                    visitedAt INTEGER NOT NULL,
                    responsible TEXT NOT NULL,
                    participants TEXT NOT NULL,
                    purpose TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    agreements TEXT NOT NULL,
                    agreementOwner TEXT NOT NULL,
                    agreementDueIso TEXT NOT NULL,
                    agreementStatus TEXT NOT NULL,
                    photoPath TEXT NOT NULL
                )""")
            }
        }
        @Volatile private var instance:InspectionDatabase?=null
        fun get(context:Context):InspectionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext,InspectionDatabase::class.java,"disal_inspecciones.db")
                    .addMigrations(MIGRATION_1_2).build().also { instance=it }
            }
    }
}
