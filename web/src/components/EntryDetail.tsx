import { useEffect, useState } from "react";
import { api, type Entry } from "../api/client";
import { Markdown } from "../lib/markdown";
import { typeInfo } from "../lib/entryTypes";

interface Props {
  entryId: string | null;
  onNavigate: (title: string) => void;
}

export function EntryDetail({ entryId, onNavigate }: Props) {
  const [entry, setEntry] = useState<Entry | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!entryId) return;
    let cancelled = false;
    setEntry(null);
    setError(null);
    void api
      .getEntry(entryId)
      .then((e) => {
        if (!cancelled) setEntry(e);
      })
      .catch((err) => {
        if (!cancelled) setError((err as Error).message);
      });
    return () => {
      cancelled = true;
    };
  }, [entryId]);

  if (!entryId) {
    return (
      <div className="detail-pane placeholder">
        左の一覧からエントリを選択してください
      </div>
    );
  }
  if (error) return <div className="detail-pane error">{error}</div>;
  if (!entry) return <div className="detail-pane placeholder">読み込み中…</div>;

  const info = typeInfo(entry.type);
  return (
    <div className="detail-pane">
      <div className="detail-header">
        <span
          className="type-badge"
          style={{ background: info.colorHex, color: "#fff" }}
        >
          {info.labelJa}
        </span>
        <h2>{entry.title}</h2>
        {entry.isFavorite && <span className="fav">★</span>}
      </div>
      {entry.summary && (
        <Markdown
          className="detail-summary"
          source={entry.summary}
          onNavigate={onNavigate}
        />
      )}
      {entry.sourceUrl && (
        <div className="detail-source">
          <a href={entry.sourceUrl} target="_blank" rel="noreferrer">
            {entry.sourceUrl}
          </a>
        </div>
      )}
      <Markdown source={entry.content ?? ""} onNavigate={onNavigate} />
    </div>
  );
}
