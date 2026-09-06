import { useEffect, useState } from "react";
import { api, type Quiz, type QuizAttemptResult } from "../api/client";

const TYPE_CHIPS: { id: string; label: string }[] = [
  { id: "qa", label: "記述" },
  { id: "mcq", label: "4択" },
  { id: "fill_blank", label: "穴埋め" },
  { id: "sort", label: "並べ替え" },
  { id: "cloze", label: "複数穴埋め" },
];

const BLANK = "＿＿＿";

function blankCount(q: string): number {
  if (!q.includes(BLANK)) return 0;
  return q.split(BLANK).length - 1;
}

export function QuizPanel() {
  const [quizzes, setQuizzes] = useState<Quiz[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [blanks, setBlanks] = useState<string[]>([]);
  const [picked, setPicked] = useState<number[]>([]);
  const [result, setResult] = useState<QuizAttemptResult | null>(null);
  const [startedAt, setStartedAt] = useState<number>(0);
  const [usedTypes, setUsedTypes] = useState(["qa", "mcq", "fill_blank", "sort", "cloze"]);

  const load = async () => {
    setError(null);
    setResult(null);
    setIndex(0);
    setAnswer("");
    setBlanks([]);
    setPicked([]);
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
  const nBlanks = quiz ? blankCount(quiz.question) : 0;

  const submit = async (userAnswer: string) => {
    const elapsed = startedAt > 0 ? Date.now() - startedAt : null;
    setResult(await api.postQuizAttempt(quiz.id, userAnswer, elapsed));
  };

  const resetInputs = () => {
    setAnswer("");
    setBlanks([]);
    setPicked([]);
    setStartedAt(0);
  };

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>クイズ演習</h2>
        <div className="type-chips">
          {TYPE_CHIPS.map((t) => (
            <button
              key={t.id}
              className={usedTypes.includes(t.id) ? "chip active" : "chip"}
              onClick={() => toggleType(t.id)}
            >
              {t.label}
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
          {quiz.quizType === "sort" && quiz.choices.length > 0 && (
            <div className="quiz-choices">
              <p>タップして並べる（並べた語をもう一度タップで戻す）</p>
              {quiz.choices.map((c, i) =>
                picked.includes(i) ? null : (
                  <button
                    key={i}
                    className="quiz-choice"
                    onClick={() => {
                      if (startedAt === 0) setStartedAt(Date.now());
                      setPicked((p) => [...p, i]);
                    }}
                  >
                    {c}
                  </button>
                ),
              )}
              {picked.length > 0 && (
                <p>
                  現在の順:{" "}
                  {picked.map((i, n) => `${n + 1}. ${quiz.choices[i]}`).join(" → ")}
                </p>
              )}
              <button
                disabled={picked.length !== quiz.choices.length}
                onClick={() =>
                  void submit(picked.map((i) => quiz.choices[i]).join(">"))
                }
              >
                回答
              </button>
            </div>
          )}
          {(quiz.quizType === "cloze" ||
            (quiz.quizType === "fill_blank" && nBlanks > 0)) && (
            <div className="quiz-input-row" style={{ flexDirection: "column" }}>
              {Array.from({ length: Math.max(nBlanks, 1) }).map((_, i) => (
                <input
                  key={i}
                  placeholder={nBlanks <= 1 ? "空欄" : `空欄 ${i + 1}`}
                  value={blanks[i] ?? ""}
                  onChange={(e) => {
                    const next = [...blanks];
                    next[i] = e.target.value;
                    setBlanks(next);
                  }}
                  onFocus={() => {
                    if (startedAt === 0) setStartedAt(Date.now());
                  }}
                />
              ))}
              <button
                disabled={!Array.from({ length: Math.max(nBlanks, 1) }).every(
                  (_, i) => (blanks[i] ?? "").trim(),
                )}
                onClick={() =>
                  void submit(
                    Array.from({ length: Math.max(nBlanks, 1) })
                      .map((_, i) => (blanks[i] ?? "").trim())
                      .join(">"),
                  )
                }
              >
                回答
              </button>
            </div>
          )}
          {(quiz.quizType === "qa" ||
            (quiz.quizType === "fill_blank" && nBlanks === 0)) && (
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
                  resetInputs();
                  setResult(null);
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
