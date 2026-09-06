import { useEffect, useState } from "react";
import { api, type Quiz, type QuizAttemptResult } from "../api/client";

const TYPE_CHIPS: { id: string; label: string }[] = [
  { id: "qa", label: "記述" },
  { id: "mcq", label: "4択" },
  { id: "fill_blank", label: "穴埋め" },
  { id: "sort", label: "並べ替え" },
  { id: "cloze", label: "複数穴埋め" },
  { id: "tf", label: "正誤" },
  { id: "multi", label: "複数選択" },
  { id: "match", label: "対応づけ" },
];

const BLANK = "＿＿＿";

function blankCount(q: string): number {
  if (!q.includes(BLANK)) return 0;
  return q.split(BLANK).length - 1;
}

function parseMatchPairs(choices: string[]): [string, string][] {
  return choices.flatMap((raw) => {
    const i = raw.indexOf("|");
    if (i <= 0 || i === raw.length - 1) return [];
    const left = raw.slice(0, i).trim();
    const right = raw.slice(i + 1).trim();
    return left && right ? [[left, right] as [string, string]] : [];
  });
}

export function QuizPanel() {
  const [quizzes, setQuizzes] = useState<Quiz[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [blanks, setBlanks] = useState<string[]>([]);
  const [picked, setPicked] = useState<number[]>([]);
  const [multiSel, setMultiSel] = useState<number[]>([]);
  const [matchLeft, setMatchLeft] = useState<string[]>([]);
  const [matchRight, setMatchRight] = useState<string[]>([]);
  const [matched, setMatched] = useState<[string, string][]>([]);
  const [selL, setSelL] = useState<string | null>(null);
  const [selR, setSelR] = useState<string | null>(null);
  const [result, setResult] = useState<QuizAttemptResult | null>(null);
  const [startedAt, setStartedAt] = useState<number>(0);
  const [usedTypes, setUsedTypes] = useState(TYPE_CHIPS.map((t) => t.id));

  const load = async () => {
    setError(null);
    setResult(null);
    setIndex(0);
    resetInputs();
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
  const matchPairs = quiz ? parseMatchPairs(quiz.choices) : [];

  const submit = async (userAnswer: string) => {
    const elapsed = startedAt > 0 ? Date.now() - startedAt : null;
    setResult(await api.postQuizAttempt(quiz.id, userAnswer, elapsed));
  };

  const resetInputs = () => {
    setAnswer("");
    setBlanks([]);
    setPicked([]);
    setMultiSel([]);
    setMatchLeft([]);
    setMatchRight([]);
    setMatched([]);
    setSelL(null);
    setSelR(null);
    setStartedAt(0);
  };

  const ensureStart = () => {
    if (startedAt === 0) setStartedAt(Date.now());
  };

  const tryPair = (nextL: string | null, nextR: string | null) => {
    if (nextL && nextR) {
      setMatched((m) => [...m, [nextL, nextR]]);
      setMatchLeft((ls) => ls.filter((x) => x !== nextL));
      setMatchRight((rs) => rs.filter((x) => x !== nextR));
      setSelL(null);
      setSelR(null);
    } else {
      setSelL(nextL);
      setSelR(nextR);
    }
  };

  useEffect(() => {
    if (quiz?.quizType === "match" && matchPairs.length >= 2 && matchLeft.length === 0 && matched.length === 0) {
      setMatchLeft(matchPairs.map((p) => p[0]).sort(() => Math.random() - 0.5));
      setMatchRight(matchPairs.map((p) => p[1]).sort(() => Math.random() - 0.5));
    }
  }, [quiz?.id]);

  const written =
    quiz &&
    (quiz.quizType === "qa" ||
      (quiz.quizType === "fill_blank" && nBlanks === 0));

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
          {quiz.quizType === "tf" && (
            <div className="quiz-choices">
              <button className="quiz-choice" onClick={() => void submit("正しい")}>
                正しい
              </button>
              <button className="quiz-choice" onClick={() => void submit("誤り")}>
                誤り
              </button>
            </div>
          )}
          {quiz.quizType === "multi" && quiz.choices.length > 0 && (
            <div className="quiz-choices">
              <p>当てはまるものをすべて選べ</p>
              {quiz.choices.map((c, i) => (
                <button
                  key={i}
                  className={multiSel.includes(i) ? "quiz-choice active" : "quiz-choice"}
                  onClick={() => {
                    ensureStart();
                    setMultiSel((s) =>
                      s.includes(i) ? s.filter((x) => x !== i) : [...s, i],
                    );
                  }}
                >
                  {c}
                </button>
              ))}
              <button
                disabled={multiSel.length === 0}
                onClick={() =>
                  void submit(
                    [...multiSel].sort((a, b) => a - b).map((i) => quiz.choices[i]).join(">"),
                  )
                }
              >
                回答
              </button>
            </div>
          )}
          {quiz.quizType === "match" && matchPairs.length >= 2 && (
            <div className="quiz-choices">
              <p>左右をタップして対応づけよ</p>
              <div style={{ display: "flex", gap: 12 }}>
                <div style={{ flex: 1 }}>
                  {matchLeft.map((c) => (
                    <button
                      key={c}
                      className={selL === c ? "quiz-choice active" : "quiz-choice"}
                      onClick={() => {
                        ensureStart();
                        const next = selL === c ? null : c;
                        tryPair(next, selR);
                      }}
                    >
                      {c}
                    </button>
                  ))}
                </div>
                <div style={{ flex: 1 }}>
                  {matchRight.map((c) => (
                    <button
                      key={c}
                      className={selR === c ? "quiz-choice active" : "quiz-choice"}
                      onClick={() => {
                        ensureStart();
                        const next = selR === c ? null : c;
                        tryPair(selL, next);
                      }}
                    >
                      {c}
                    </button>
                  ))}
                </div>
              </div>
              {matched.length > 0 && (
                <p>
                  組: {matched.map(([l, r]) => `${l} ⇔ ${r}`).join("、")}
                </p>
              )}
              <button
                disabled={matchLeft.length !== 0 || matched.length === 0}
                onClick={() =>
                  void submit(matched.map(([l, r]) => `${l}=${r}`).join(">"))
                }
              >
                回答
              </button>
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
                      ensureStart();
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
                  onFocus={ensureStart}
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
          {written && (
            <div className="quiz-input-row">
              <input
                placeholder="回答を入力"
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                onFocus={ensureStart}
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
