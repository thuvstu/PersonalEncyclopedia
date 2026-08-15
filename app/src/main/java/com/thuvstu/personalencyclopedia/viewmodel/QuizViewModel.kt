package com.thuvstu.personalencyclopedia.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.entity.ProgressEventEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import com.thuvstu.personalencyclopedia.repository.QuizRepository
import com.thuvstu.personalencyclopedia.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizRepo: QuizRepository,
    private val progressEventDao: ProgressEventDao,   // ← Phase 3で追加
    private val settingsRepo: SettingsRepository      // ★最適化R2: クイズ演習設定
) : ViewModel() {

    enum class SessionMode { NORMAL, SURVIVAL }

    sealed class QuizUiState {
        object SelectMode : QuizUiState()
        object Loading : QuizUiState()
        object Empty : QuizUiState()
        data class Question(
            val quiz: QuizBankEntity,
            val choices: List<String>,
            val hints: List<String>,
            val hintsRevealed: Int,
            val questionNumber: Int,
            val totalQuestions: Int,
            val mode: SessionMode = SessionMode.NORMAL
        ) : QuizUiState()
        data class Answered(
            val quiz: QuizBankEntity,
            val userAnswer: String,
            val isCorrect: Boolean?,
            val score: Float,
            val gradingMethod: String,
            val questionNumber: Int,
            val totalQuestions: Int,
            val choices: List<String> = emptyList(),   // ★最適化R4: MCQの正解強調表示用
            // ★新採点システム(試作): rubric採点の根拠(LLM/ヒューリスティックのrationale)
            val rubricRationale: String? = null,
            val rubricEvidenceJson: String? = null
        ) : QuizUiState()
        data class SessionComplete(
            val totalAnswered: Int,
            val correctCount: Int,
            val totalScore: Float,
            val survivalStreak: Int? = null
        ) : QuizUiState()
        // §8.7.2 プレッシャーテスト(全列挙型)
        data class EnumerateQuestion(
            val fieldLabel: String,
            val correctSet: List<String>,
            val matched: List<String>,
            val timeLeftMs: Long
        ) : QuizUiState()
        data class EnumerateComplete(
            val fieldLabel: String,
            val matchedCount: Int,
            val totalCount: Int,
            val missed: List<String>
        ) : QuizUiState()
    }

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.SelectMode)
    val uiState: StateFlow<QuizUiState> = _uiState

    private var quizzes: List<QuizBankEntity> = emptyList()
    private var currentIndex = 0
    private var correctCount = 0
    private var totalScore = 0f
    private var questionShownAt = 0L   // §8.7.3 (v8): 設問表示時刻（回答時間計測用）
    private var mode = SessionMode.NORMAL
    private var enumerateJob: Job? = null

    val quizCount: StateFlow<Int> = quizRepo.observeQuizCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startSession(topicId: String? = null) {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            val count = settingsRepo.quizQuestionCount.first()
            val difficultyMin = settingsRepo.quizDifficultyMin.first()
            val types = settingsRepo.quizTypes.first().toList()
            quizzes = quizRepo.getNextQuizzes(
                topicId = topicId,
                limit = count,
                difficultyMin = if (difficultyMin <= 1) null else difficultyMin,
                types = types
            )
            currentIndex = 0
            correctCount = 0
            totalScore = 0f
            mode = SessionMode.NORMAL
            enumerateJob?.cancel()

            if (quizzes.isEmpty()) {
                _uiState.value = QuizUiState.Empty
            } else {
                showCurrentQuestion()
            }
        }
    }

    // §8.7.2 サバイバル形式: 1問でも間違えると即終了。連続正解数を記録。
    fun startSurvivalSession(topicId: String? = null) {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            val count = settingsRepo.quizSurvivalCount.first()
            val difficultyMin = settingsRepo.quizDifficultyMin.first()
            val types = settingsRepo.quizTypes.first().toList()
            quizzes = quizRepo.getNextQuizzes(
                topicId = topicId,
                limit = count,
                difficultyMin = if (difficultyMin <= 1) null else difficultyMin,
                types = types
            )
            currentIndex = 0
            correctCount = 0
            totalScore = 0f
            mode = SessionMode.SURVIVAL
            enumerateJob?.cancel()

            if (quizzes.isEmpty()) {
                _uiState.value = QuizUiState.Empty
            } else {
                showCurrentQuestion()
            }
        }
    }

    fun generateQuizzes() {
        viewModelScope.launch {
            quizRepo.generateQuizzesFromDefinitions()
            startSession()
        }
    }

    // §8.7.2 プレッシャーテスト: 同一分野のentry群を制限時間内にできるだけ多く列挙
    fun startEnumerateChallenge() {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            val challenge = quizRepo.buildEnumerateChallenge()
            if (challenge == null) {
                _uiState.value = QuizUiState.Empty
                return@launch
            }
            val initial = QuizUiState.EnumerateQuestion(
                fieldLabel = challenge.field,
                correctSet = challenge.answers,
                matched = emptyList(),
                timeLeftMs = settingsRepo.quizPressureSeconds.first() * 1000L
            )
            _uiState.value = initial
            enumerateJob?.cancel()
            enumerateJob = viewModelScope.launch {
                while (true) {
                    delay(250)
                    val st = _uiState.value as? QuizUiState.EnumerateQuestion ?: break
                    val remaining = st.timeLeftMs - 250
                    if (remaining <= 0) {
                        finishEnumerate(st)
                        break
                    }
                    _uiState.value = st.copy(timeLeftMs = remaining)
                }
            }
        }
    }

    private fun finishEnumerate(st: QuizUiState.EnumerateQuestion) {
        _uiState.value = QuizUiState.EnumerateComplete(
            fieldLabel = st.fieldLabel,
            matchedCount = st.matched.size,
            totalCount = st.correctSet.size,
            missed = st.correctSet.filterNot { it in st.matched }
        )
    }

    fun submitEnumerateAnswer(answer: String) {
        val st = _uiState.value as? QuizUiState.EnumerateQuestion ?: return
        val hit = quizRepo.matchEnumerateAnswer(answer, st.correctSet, st.matched)
        if (hit != null) {
            _uiState.value = st.copy(matched = st.matched + hit)
        }
    }

    private fun showCurrentQuestion() {
        if (currentIndex >= quizzes.size) {
            _uiState.value = QuizUiState.SessionComplete(
                totalAnswered = correctCount,
                correctCount = correctCount,
                totalScore = totalScore,
                survivalStreak = if (mode == SessionMode.SURVIVAL) correctCount else null
            )
            return
        }
        val quiz = quizzes[currentIndex]
        questionShownAt = SystemClock.elapsedRealtime()
        _uiState.value = QuizUiState.Question(
            quiz = quiz,
            choices = quizRepo.parseChoices(quiz.choicesJson),
            hints = quizRepo.parseHints(quiz.hintsJson),
            hintsRevealed = 0,
            questionNumber = currentIndex + 1,
            totalQuestions = quizzes.size,
            mode = mode
        )
    }

    fun revealHint() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (state.hintsRevealed < state.hints.size) {
            _uiState.value = state.copy(hintsRevealed = state.hintsRevealed + 1)
        }
    }

    fun submitAnswer(answer: String) {
        val state = _uiState.value as? QuizUiState.Question ?: return
        val quiz = state.quiz

        viewModelScope.launch {
            // §8.7.3 (v8): 設問表示からの経過時間を記録
            val answeredWithinMs = SystemClock.elapsedRealtime() - questionShownAt
            val result = quizRepo.gradeAndRecord(
                quiz = quiz,
                userAnswer = answer,
                hintsRevealed = state.hintsRevealed,
                answeredWithinMs = answeredWithinMs,
                hintPenalty = settingsRepo.quizHintPenalty.first()
            )
            val attempt = result.attempt

            // ★ Phase 3追加: 進捗イベント記録
            progressEventDao.insert(
                ProgressEventEntity(
                    entityType = "quiz",
                    entityId = quiz.id,
                    eventType = "answered"
                )
            )

            if (attempt.isCorrect == true) correctCount++
            totalScore += attempt.score

            // §8.7.2 サバイバル形式: 正解以外（未習含む）は即終了
            if (mode == SessionMode.SURVIVAL && attempt.isCorrect != true) {
                _uiState.value = QuizUiState.SessionComplete(
                    totalAnswered = correctCount,
                    correctCount = correctCount,
                    totalScore = totalScore,
                    survivalStreak = correctCount
                )
                return@launch
            }

            _uiState.value = QuizUiState.Answered(
                quiz = quiz,
                userAnswer = answer,
                isCorrect = attempt.isCorrect,
                score = attempt.score,
                gradingMethod = attempt.gradingMethod,
                questionNumber = state.questionNumber,
                totalQuestions = state.totalQuestions,
                choices = quizRepo.parseChoices(quiz.choicesJson),
                rubricRationale = result.rubricRationale,
                rubricEvidenceJson = result.rubricEvidenceJson
            )
        }
    }

    fun markUnlearned() {
        submitAnswer("__UNLEARNED__")
    }

    fun nextQuestion() {
        if (mode == SessionMode.SURVIVAL) {
            // 正解時のみここへ来る（不正解はsubmitAnswerで終了済み）
            currentIndex++
            showCurrentQuestion()
        } else {
            currentIndex++
            showCurrentQuestion()
        }
    }

    override fun onCleared() {
        super.onCleared()
        enumerateJob?.cancel()
    }
}