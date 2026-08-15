import { useEffect, useState } from "react";
import { api, type Quiz, type QuizAttemptResult } from "../api/client";

export function QuizPanel() {
  const [quizzes, setQuizzes] = useState<Quiz[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [result, setResult] = useState<QuizAttemptResult | null>(null);
  const [startedAt, setStartedAt] = useState<number>(0);
  const [usedTypes, setUsedTypes] = useState(["qa", "mcq", "fill_blank"]);

  const load = async () => {
    setError(null);
    setResult(null);
    setIndex(0);
    setAnswer("");
    try {
      setQuizzes(await api.getQuizzes(10, usedTypes));
    } catch (e) {
      setError((e as Error).message);
      setQuizzes([]);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const toggleType = (t: string) => {
    setUsedTypes((prev) =>
      prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t],
    );
  };

  const quiz = quizzes[index];

  const submit = async (userAnswer: string) => {
    const elapsed = startedAt > 0 ? Date.now() - startedAt : null;
    setResult(
      await api.postQuizAttempt(quiz.id, userAnswer, elapsed),
    );
  };

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>クイズ演習</h2>
        <div className="type-chips">
          {["qa", "mcq", "fill_blank"].map((t) => (
            <button
              key={t}
              className={usedTypes.includes(t) ? "chip active" : "chip"}
              onClick={() => toggleType(t)}
            >
              {t}
            </button>
          ))}
        </div>
        <button onClick={() => void load()}>出題</button>
      </div>
      {error && <div className="error">{error}</div>}
      {quiz && (
        <div className="quiz-card">
          <div className="quiz-progress">
            {index + 1} / {quizzes.length} · {quiz.quizType} · 難易度{" "}
            {quiz.difficulty}
          </div>
          <h3>{quiz.question}</h3>
          {quiz.quizType === "mcq" && quiz.choices.length > 0 && (
            <div className="quiz-choices">
              {quiz.choices.map((c, i) => (
                <button
                  key={i}
                  onClick={() => void submit(c)}
                  className="quiz-choice"
                >
                  {c}
                </button>
              ))}
            </div>
          )}
          {(quiz.quizType === "qa" || quiz.quizType === "fill_blank") && (
            <div className="quiz-input-row">
              <input
                placeholder="回答を入力"
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                onFocus={() => {
                  if (startedAt === 0) setStartedAt(Date.now());
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && answer.trim()) void submit(answer.trim());
                }}
              />
              <button
                disabled={!answer.trim()}
                onClick={() => void submit(answer.trim())}
              >
                回答
              </button>
            </div>
          )}
          <button
            className="unlearned"
            onClick={() => void submit("__UNLEARNED__")}
          >
            未習
          </button>
          {result && (
            <div className={`quiz-result ${result.isCorrect ? "ok" : "ng"}`}>
              <div>
                {result.isCorrect === null
                  ? "未習として記録"
                  : result.isCorrect
                    ? "正解"
                    : "不正解"}{" "}
                (score {result.score}, {result.gradingMethod})
              </div>
              {!result.isCorrect && result.correctAnswer && (
                <div className="quiz-correct">
                  正解: {result.correctAnswer}
                </div>
              )}
              {result.explanation && (
                <div className="quiz-explanation">{result.explanation}</div>
              )}
              <button
                onClick={() => {
                  setAnswer("");
                  setResult(null);
                  setStartedAt(0);
                  if (index + 1 < quizzes.length) setIndex(index + 1);
                  else void load();
                }}
              >
                次へ
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
