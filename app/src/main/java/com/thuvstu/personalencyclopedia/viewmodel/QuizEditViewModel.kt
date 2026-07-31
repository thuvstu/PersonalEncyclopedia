package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.dao.QuizDao
import com.thuvstu.personalencyclopedia.db.entity.QuizBankEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuizEditViewModel @Inject constructor(
    private val quizDao: QuizDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val quizId: String? = savedStateHandle["quizId"]
    val isNew: Boolean = quizId == null

    private val json = Json { ignoreUnknownKeys = true }

    private val _quizType = MutableStateFlow("qa")
    val quizType: StateFlow<String> = _quizType
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question
    private val _answer = MutableStateFlow("")
    val answer: StateFlow<String> = _answer
    private val _choices = MutableStateFlow(listOf("", "", "", ""))
    val choices: StateFlow<List<String>> = _choices
    private val _hints = MutableStateFlow(listOf<String>())
    val hints: StateFlow<List<String>> = _hints
    private val _explanation = MutableStateFlow("")
    val explanation: StateFlow<String> = _explanation
    private val _difficulty = MutableStateFlow(3)
    val difficulty: StateFlow<Int> = _difficulty

    private val _saved = MutableSharedFlow<String>()
    val saved: SharedFlow<String> = _saved

    init {
        val id = quizId
        if (id != null) {
            viewModelScope.launch {
                quizDao.getQuizById(id)?.let { q ->
                    _quizType.value = q.quizType
                    _question.value = q.question
                    _answer.value = q.answer
                    _explanation.value = q.explanation ?: ""
                    _difficulty.value = q.difficulty
                    _choices.value = parseList(q.choicesJson).let {
                        if (it.isEmpty()) listOf("", "", "", "") else it
                    }
                    _hints.value = parseList(q.hintsJson)
                }
            }
        }
    }

    fun setQuizType(v: String) { _quizType.value = v }
    fun setQuestion(v: String) { _question.value = v }
    fun setAnswer(v: String) { _answer.value = v }
    fun setExplanation(v: String) { _explanation.value = v }
    fun setDifficulty(v: Int) { _difficulty.value = v }

    fun updateChoice(index: Int, value: String) {
        _choices.update { it.toMutableList().apply { this[index] = value } }
    }
    fun addChoice() {
        if (_choices.value.size < 6) _choices.update { it + "" }
    }
    fun removeChoice(index: Int) {
        if (_choices.value.size > 2) _choices.update { it.toMutableList().apply { removeAt(index) } }
    }
    fun updateHint(index: Int, value: String) {
        _hints.update { it.toMutableList().apply { this[index] = value } }
    }
    fun addHint() {
        if (_hints.value.size < 3) _hints.update { it + "" }   // §8.5: ヒント最大3件
    }
    fun removeHint(index: Int) {
        _hints.update { it.toMutableList().apply { removeAt(index) } }
    }

    val canSave: StateFlow<Boolean> = combine(question, answer, quizType, choices) { q, a, t, c ->
        q.isNotBlank() && a.isNotBlank() && (t != "mcq" || c.count { it.isNotBlank() } >= 2)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun save() {
        viewModelScope.launch {
            val entity = QuizBankEntity(
                id = quizId ?: UUID.randomUUID().toString(),
                quizType = _quizType.value,
                question = _question.value.trim(),
                answer = _answer.value.trim(),
                choicesJson = Json.encodeToString(
                    if (_quizType.value == "mcq") _choices.value.filter { it.isNotBlank() }
                    else emptyList()
                ),
                hintsJson = Json.encodeToString(_hints.value.filter { it.isNotBlank() }),
                explanation = _explanation.value.takeIf { it.isNotBlank() },
                generationMethod = "manual",
                difficulty = _difficulty.value
            )
            quizDao.insertQuiz(entity)
            _saved.emit(entity.id)
        }
    }

    private fun parseList(jsonStr: String): List<String> = try {
        json.parseToJsonElement(jsonStr).jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }
}