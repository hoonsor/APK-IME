package tw.hoonsor.ime.voice

import tw.hoonsor.ime.contracts.EditorHints
import tw.hoonsor.ime.contracts.RewriteException
import tw.hoonsor.ime.contracts.TextRewriter
import tw.hoonsor.ime.contracts.VoiceErrorCode
import tw.hoonsor.ime.voice.ai.AiException
import tw.hoonsor.ime.voice.ai.AiRole
import tw.hoonsor.ime.voice.ai.AiRunner
import tw.hoonsor.ime.voice.ai.Prompts
import tw.hoonsor.ime.voice.ai.Sanitizer
import tw.hoonsor.ime.voice.text.TextRules

/** 編輯面板 AI 欄（潤飾、AI 寫作、翻譯）。使用「文字 AI」模型，失敗時改用備援。 */
internal class DefaultTextRewriter(private val runner: AiRunner) : TextRewriter {

    override fun rewrite(text: String, instruction: String, hints: EditorHints): Result<String> = run(hints) {
        val system = Prompts.join(
            Prompts.editSystem(text),
            Prompts.dictionary(runner.voiceTerms(60)),
        )
        val (out, _) = runner.chat(AiRole.REWRITE, AiRole.REWRITE_BACKUP, system, Prompts.draft(instruction), TIMEOUT_MS)
        Sanitizer.clean(out, text, guardAnswer = false)
    }

    override fun compose(brief: String, hints: EditorHints): Result<String> = run(hints) {
        val system = Prompts.join(
            Prompts.COMPOSE,
            Prompts.appTone(hints),
            Prompts.dictionary(runner.voiceTerms(60)),
        )
        val (out, _) = runner.chat(AiRole.REWRITE, AiRole.REWRITE_BACKUP, system, Prompts.draft(brief), TIMEOUT_MS)
        Sanitizer.clean(out, brief, guardAnswer = false)
    }

    private fun run(hints: EditorHints, block: () -> String?): Result<String> {
        if (hints.isPassword) return Result.failure(RewriteException(VoiceErrorCode.PASSWORD_FIELD, "密碼欄位不使用 AI"))
        return try {
            val r = block()
            if (r.isNullOrBlank()) Result.failure(RewriteException(VoiceErrorCode.PROVIDER_ERROR, "AI 沒有回傳內容"))
            else Result.success(TextRules.finalizeText(r))
        } catch (e: AiException) {
            Result.failure(RewriteException(e.code, e.message ?: "AI 失敗"))
        } catch (e: Exception) {
            Result.failure(RewriteException(VoiceErrorCode.UNKNOWN, e.javaClass.simpleName))
        }
    }

    companion object {
        /** MiniMax M3 是推理模型，可能較慢，給寬鬆一點。 */
        private const val TIMEOUT_MS = 30_000
    }
}
