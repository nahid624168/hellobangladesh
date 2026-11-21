package com.exambase.backend.ai

import com.exambase.backend.Database
import com.exambase.backend.ApiException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.util.UUID

@Serializable
data class AiGeneratedQuestionInput(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = -1,
    val explanation: String = "",
)

@Serializable
data class AiGeneratedQuizCreateRequest(
    val title: String = "",
    val description: String? = null,
    val questions: List<AiGeneratedQuestionInput> = emptyList(),
)

@Serializable
data class AiGeneratedQuizSetDto(
    val id: String,
    val title: String,
    val description: String?,
    val questionCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AiGeneratedQuizDetailDto(
    @SerialName("set") val quizSet: AiGeneratedQuizSetDto,
    val questions: List<AiGeneratedQuestionInput>,
)

/** Owner-scoped storage for reviewed AI quiz drafts; never inserts into the official question bank. */
class AiGeneratedQuizStore(private val db: Database) {
    private val json = Json

    fun list(owner: UUID): List<AiGeneratedQuizSetDto> = db.withConnection { c ->
        c.prepareStatement("SELECT s.id,s.title,s.description,(SELECT count(*) FROM ai_generated_quiz_questions q WHERE q.set_id=s.id) question_count,s.created_at,s.updated_at FROM ai_generated_quiz_sets s WHERE s.owner_id=? ORDER BY s.updated_at DESC,s.id").use { ps ->
            ps.setObject(1, owner)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapSet(rs)) } }
        }
    }

    fun create(owner: UUID, req: AiGeneratedQuizCreateRequest): AiGeneratedQuizDetailDto {
        val title = req.title.trim()
        if (title.isEmpty() || title.length > 120) bad("title", "Give this quiz a title (maximum 120 characters).")
        if (req.description.orEmpty().length > 1000) bad("description", "Description is too long.")
        if (req.questions.isEmpty() || req.questions.size > 5) bad("questions", "Save between 1 and 5 reviewed questions.")
        val clean = req.questions.mapIndexed { i, q ->
            val question = q.question.trim()
            val options = q.options.map { it.trim() }
            if (question.isEmpty() || question.length > 2000) bad("questions[$i].question", "Question text is required.")
            if (options.size !in 2..6 || options.any { it.isBlank() }) bad("questions[$i].options", "Use 2 to 6 non-empty options.")
            if (q.correctOptionIndex !in options.indices) bad("questions[$i].correctOptionIndex", "Choose one correct option.")
            AiGeneratedQuestionInput(question, options, q.correctOptionIndex, q.explanation.trim().take(2000))
        }
        return db.transaction { c ->
            val id = c.prepareStatement("INSERT INTO ai_generated_quiz_sets(owner_id,title,description) VALUES (?,?,?) RETURNING id").use { ps ->
                ps.setObject(1, owner); ps.setString(2, title); ps.setString(3, req.description?.trim()?.takeIf(String::isNotEmpty))
                ps.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java) }
            }
            c.prepareStatement("INSERT INTO ai_generated_quiz_questions(set_id,position,question,options,correct_option_index,explanation) VALUES (?,?,?,?::jsonb,?,?)").use { ps ->
                clean.forEachIndexed { i, q ->
                    ps.setObject(1, id); ps.setInt(2, i + 1); ps.setString(3, q.question); ps.setString(4, json.encodeToString(q.options)); ps.setInt(5, q.correctOptionIndex); ps.setString(6, q.explanation); ps.addBatch()
                }
                ps.executeBatch()
            }
            detailIn(c, owner, id)!!
        }
    }

    fun get(owner: UUID, rawId: String): AiGeneratedQuizDetailDto = db.withConnection { c ->
        val id = parseId(rawId)
        detailIn(c, owner, id) ?: missing()
    }

    fun delete(owner: UUID, rawId: String) {
        val id = parseId(rawId)
        val n = db.withConnection { c -> c.prepareStatement("DELETE FROM ai_generated_quiz_sets WHERE id=? AND owner_id=?").use { ps -> ps.setObject(1,id); ps.setObject(2,owner); ps.executeUpdate() } }
        if (n == 0) missing()
    }

    private fun detailIn(c: java.sql.Connection, owner: UUID, id: UUID): AiGeneratedQuizDetailDto? {
        val head = c.prepareStatement("SELECT s.id,s.title,s.description,(SELECT count(*) FROM ai_generated_quiz_questions q WHERE q.set_id=s.id) question_count,s.created_at,s.updated_at FROM ai_generated_quiz_sets s WHERE s.id=? AND s.owner_id=?").use { ps ->
            ps.setObject(1,id); ps.setObject(2,owner); ps.executeQuery().use { rs -> if (rs.next()) mapSet(rs) else null }
        } ?: return null
        val questions = c.prepareStatement("SELECT question,options::text,correct_option_index,explanation FROM ai_generated_quiz_questions WHERE set_id=? ORDER BY position").use { ps ->
            ps.setObject(1,id); ps.executeQuery().use { rs -> buildList { while (rs.next()) add(AiGeneratedQuestionInput(rs.getString(1), json.decodeFromString<List<String>>(rs.getString(2)), rs.getInt(3), rs.getString(4))) } }
        }
        return AiGeneratedQuizDetailDto(head, questions)
    }

    private fun mapSet(rs: ResultSet) = AiGeneratedQuizSetDto(rs.getObject("id", UUID::class.java).toString(), rs.getString("title"), rs.getString("description"), rs.getInt("question_count"), rs.getTimestamp("created_at").toInstant().toString(), rs.getTimestamp("updated_at").toInstant().toString())
    private fun parseId(raw: String) = runCatching { UUID.fromString(raw) }.getOrElse { missing() }
    private fun missing(): Nothing = throw ApiException(HttpStatusCode.NotFound, "not_found", "AI quiz set not found.")
    private fun bad(field: String, message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, "invalid_request", "Invalid request.", mapOf(field to message))
}
