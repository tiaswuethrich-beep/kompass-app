package ch.akh.kompass.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import java.time.Duration

/** Ein Claude-Modell mit Preisen in US-Dollar pro Million Tokens. */
data class AiModel(val id: String, val label: String, val inPerM: Double, val outPerM: Double)

object AiModels {
    val HAIKU = AiModel("claude-haiku-5-5", "Haiku 5.5 (am günstigsten)", 0.10, 0.50)
    val SONNET = AiModel("claude-sonnet-5-5", "Sonnet 5.5 (klüger, 20x teurer)", 2.0, 10.0)
    val OPUS = AiModel("claude-opus-5-5", "Opus 5.5 (am klügsten, 40x teurer)", 4.0, 20.0)
    val all = listOf(HAIKU, SONNET, OPUS)
    fun byId(id: String): AiModel = all.firstOrNull { it.id == id } ?: HAIKU
}

/** Eine Nachricht. imageJpegB64 = optionales Foto (JPEG, Base64) zur Nachricht. */
data class AiTurn(val fromUser: Boolean, val text: String, val imageJpegB64: String? = null)

sealed class AiResult {
    data class Ok(
        val text: String,
        val costUsd: Double,
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheReadTokens: Long,
    ) : AiResult()

    data class Fail(val message: String, val keyProblem: Boolean = false) : AiResult()
}

/**
 * Dünne Hülle um das offizielle Anthropic-SDK. Kennt kein Android, damit sie
 * sich auch auf dem Rechner testen lässt.
 *
 * Sparmassnahmen: kurze Antworten (maxTokens), Aufwand "low", Systemtext vorne
 * stabil und Daten dahinter, damit sich bei Chats der Prompt-Cache lohnt.
 */
class ClaudeClient(apiKey: String, baseUrl: String? = null) : AutoCloseable {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .maxRetries(2)
        .timeout(Duration.ofSeconds(60))
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    /**
     * @param stableSystem Text, der sich (fast) nie ändert: Rolle und Regeln.
     * @param dataSystem Tagesdaten. Steht hinter dem stabilen Teil.
     * @param turns Gesprächsverlauf, letzter Eintrag ist die neue Frage.
     * @param cache Bei Chats einschalten: Claude merkt sich den bisherigen Verlauf
     *   günstig (Cache-Lesen kostet 10 % des normalen Preises).
     */
    fun ask(
        model: AiModel,
        stableSystem: String,
        dataSystem: String,
        turns: List<AiTurn>,
        maxTokens: Long,
        cache: Boolean,
    ): AiResult {
        val b = MessageCreateParams.builder()
            .model(model.id)
            .maxTokens(maxTokens)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .systemOfTextBlockParams(
                listOfNotNull(
                    TextBlockParam.builder().text(stableSystem).build(),
                    dataSystem.takeIf { it.isNotBlank() }?.let { TextBlockParam.builder().text(it).build() },
                ),
            )
        turns.forEach { t ->
            when {
                !t.fromUser -> b.addAssistantMessage(t.text)
                t.imageJpegB64 != null -> b.addUserMessageOfContentBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(
                            Base64ImageSource.builder().data(t.imageJpegB64).mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build(),
                        ),
                        ContentBlockParam.ofText(t.text),
                    ),
                )
                else -> b.addUserMessage(t.text)
            }
        }
        if (cache) b.cacheControl(CacheControlEphemeral.builder().build())

        return try {
            val msg = client.messages().create(b.build())
            val u = msg.usage()
            val cacheRead = u.cacheReadInputTokens().orElse(0L)
            val cacheWrite = u.cacheCreationInputTokens().orElse(0L)
            val cost = (u.inputTokens() * model.inPerM +
                cacheWrite * model.inPerM * 1.25 +
                cacheRead * model.inPerM * 0.1 +
                u.outputTokens() * model.outPerM) / 1_000_000.0
            val text = msg.content().mapNotNull { blk -> blk.text().map { it.text() }.orElse(null) }
                .joinToString("\n").trim()
            val stop = msg.stopReason().orElse(null)
            when {
                stop == StopReason.REFUSAL ->
                    AiResult.Fail("Claude möchte darauf nicht antworten. Formuliere die Frage anders.")
                text.isBlank() -> AiResult.Fail("Leere Antwort. Versuch es nochmals.")
                else -> AiResult.Ok(
                    if (stop == StopReason.MAX_TOKENS) "$text …" else text,
                    cost, u.inputTokens(), u.outputTokens(), cacheRead,
                )
            }
        } catch (e: UnauthorizedException) {
            AiResult.Fail("Der API-Key wird nicht akzeptiert. Prüf ihn unter Mehr › KI.", keyProblem = true)
        } catch (e: PermissionDeniedException) {
            AiResult.Fail("Dieser API-Key darf das Modell nicht nutzen.", keyProblem = true)
        } catch (e: NotFoundException) {
            AiResult.Fail("Modell nicht gefunden. Wähle unter Mehr › KI ein anderes.")
        } catch (e: RateLimitException) {
            AiResult.Fail("Zu viele Anfragen gerade. Warte kurz und versuch es nochmals.")
        } catch (e: BadRequestException) {
            val m = e.message ?: ""
            if (m.contains("credit", ignoreCase = true)) AiResult.Fail("Dein Anthropic-Guthaben ist aufgebraucht. Lade es in der Console auf.")
            else AiResult.Fail("Anfrage abgelehnt (400).")
        } catch (e: InternalServerException) {
            AiResult.Fail("Claude ist gerade überlastet. Versuch es später.")
        } catch (e: AnthropicServiceException) {
            AiResult.Fail("Fehler vom Server (${e.statusCode()}).")
        } catch (e: AnthropicIoException) {
            AiResult.Fail("Keine Verbindung. Bist du online?")
        } catch (e: Exception) {
            AiResult.Fail("Unerwarteter Fehler: ${e.javaClass.simpleName}")
        }
    }

    override fun close() = client.close()
}
