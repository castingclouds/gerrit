package ai.fluxuate.gerrit.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(name = "teams")
data class TeamEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(unique = true, nullable = false)
    val name: String,
    
    @Column(unique = true, nullable = false)
    val uuid: String = UUID.randomUUID().toString(),
    
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    
    @Column(name = "visible_to_all", nullable = false)
    val visibleToAll: Boolean = false,
    
    @Column(name = "owner_id")
    val ownerId: String? = null,
    
    @Column(name = "created_on", nullable = false)
    val createdOn: Instant = Instant.now(),
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val members: MutableList<String> = mutableListOf(),
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val subteams: MutableList<String> = mutableListOf(),
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as TeamEntity
        
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "TeamEntity(id=$id, name='$name', uuid='$uuid')"
    }
}
