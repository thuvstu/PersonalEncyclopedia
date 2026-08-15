import { useEffect, useState } from "react";
import { api, type SrsDueEntry } from "../api/client";

export function SrsPanel() {
  const [items, setItems] = useState<SrsDueEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  const load = async () => {
    setError(null);
    setResult(null);
    setRevealed(false);
    setIndex(0);
    try {
      setItems(await api.getSrsDue(50));
    } catch (e) {
      setError((e as Error).message);
      setItems([]);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const item = items[index];
  const grade = (g: number) => {
    void api.postSrsReview(item.entryId, g).then((r) => {
      setResult(
        `間隔 ${r.intervalDays}日・ease ${r.easeFactor.toFixed(2)}・次回 ${new Date(
          r.nextReviewAt,
        ).toLocaleDateString("ja-JP")}`,
      );
    });
  };

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>単語帳(SRS)</h2>
        <button onClick={() => void load()}>再読み込み</button>
      </div>
      {error && <div className="error">{error}</div>}
      {items.length === 0 && !error && (
        <p className="muted">今日の復習対象はありません</p>
      )}
      {item && (
        <div className="srs-card">
          <div className="srs-progress">
            {index + 1} / {items.length}
          </div>
          <h3>{item.term}</h3>
          {item.reading && <div className="srs-reading">{item.reading}</div>}
          {item.field && <div className="chip-static">{item.field}</div>}
          <div className="srs-reveal">
            {!revealed ? (
              <button onClick={() => setRevealed(true)}>答えを表示</button>
            ) : (
              <div className="srs-definition">{item.definition}</div>
            )}
          </div>
          {revealed && (
            <div className="srs-grades">
              {[0, 1, 2, 3, 4, 5].map((g) => (
                <button
                  key={g}
                  onClick={() => {
                    grade(g);
                    setRevealed(false);
                    setResult(null);
                    if (index + 1 < items.length) setIndex(index + 1);
                  }}
                >
                  {g}
                </button>
              ))}
            </div>
          )}
          {result && <div className="srs-result">{result}</div>}
        </div>
      )}
    </div>
  );
}
