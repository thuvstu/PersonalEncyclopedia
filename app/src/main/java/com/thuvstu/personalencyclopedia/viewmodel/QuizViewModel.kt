package com.thuvstu.personalencyclopedia.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.ProgressEventDao
import com.thuvstu.personalencyclopedia.db.entity.ProgressEventEntity
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import com.thuvstu.personalencyclopedia.repository.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizRepo: QuizRepository,
    private val progressEventDao: ProgressEventDao   // ← Phase 3で追加
) : ViewModel() {

    sealed class QuizUiState {
        object Loading : QuizUiState()
        object Empty : QuizUiState()
        data class Question(
            val quiz: QuizBankEntity,
            val choices: List<String>,
            val hints: List<String>,
            val hintsRevealed: Int,
            val questionNumber: Int,
            val totalQuestions: Int
        ) : QuizUiState()
        data class Answered(
            val quiz: QuizBankEntity,
            val userAnswer: String,
            val isCorrect: Boolean?,
            val score: Float,
            val gradingMethod: String,
            val questionNumber: Int,
            val totalQuestions: Int
        ) : QuizUiState()
        data class SessionComplete(
            val totalAnswered: Int,
            val correctCount: Int,
            val totalScore: Float
        ) : QuizUiState()
    }

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState

    private var quizzes: List<QuizBankEntity> = emptyList()
    private var currentIndex = 0
    private var correctCount = 0
    private var totalScore = 0f
    private var questionShownAt = 0L   // §8.7.3 (v8): 設問表示時刻（回答時間計測用）

    val quizCount: StateFlow<Int> = quizRepo.observeQuizCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startSession(topicId: String? = null) {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            quizzes = quizRepo.getNextQuizzes(topicId = topicId, limit = 10)
            currentIndex = 0
            correctCount = 0
            totalScore = 0f

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

    private fun showCurrentQuestion() {
        if (currentIndex >= quizzes.size) {
            _uiState.value = QuizUiState.SessionComplete(
                totalAnswered = quizzes.size,
                correctCount = correctCount,
                totalScore = totalScore
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
            totalQuestions = quizzes.size
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
            val attempt = quizRepo.gradeAndRecord(
                quiz = quiz,
                userAnswer = answer,
                hintsRevealed = state.hintsRevealed,
                answeredWithinMs = answeredWithinMs
            )

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

            _uiState.value = QuizUiState.Answered(
                quiz = quiz,
                userAnswer = answer,
                isCorrect = attempt.isCorrect,
                score = attempt.score,
                gradingMethod = attempt.gradingMethod,
                questionNumber = state.questionNumber,
                totalQuestions = state.totalQuestions
            )
        }
    }

    fun markUnlearned() {
        submitAnswer("__UNLEARNED__")
    }

    fun nextQuestion() {
        currentIndex++
        showCurrentQuestion()
    }
}