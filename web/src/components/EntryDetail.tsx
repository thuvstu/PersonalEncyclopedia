import { useEffect, useState } from "react";
import { api, type Entry } from "../api/client";
import { Markdown } from "../lib/markdown";
import { typeInfo } from "../lib/entryTypes";
import { StickyNotes } from "./StickyNotes";
import { ConnectionGroups } from "./ConnectionGroups";

interface Props {
  entryId: string | null;
  onNavigate: (title: string) => void;
  /** ★wt65: お気に入り等の変化を一覧へ伝える */
  onChanged?: () => void;
  /** ★wt65: 削除済み。親が選択を解除して一覧を再読込する */
  onDeleted?: () => void;
}

export function EntryDetail({ entryId, onNavigate, onChanged, onDeleted }: Props) {
  const [entry, setEntry] = useState<Entry | null>(null);
  const [error, setError] = useState<string | null>(null);
  // ★wt65: 操作失敗は詳細全体を差し替えず、.inline で出す(error は読込失敗用)
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!entryId) return;
    let cancelled = false;
    setEntry(null);
    setError(null);
    setActionError(null);
    setBusy(false);
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

  // ★wt65: favorite はサーバ側でトグルされる契約なので、戻り値を正とする
  const toggleFav = async () => {
    setBusy(true);
    setActionError(null);
    try {
      const res = await api.toggleFavorite(entry.id);
      setEntry({ ...entry, isFavorite: res.isFavorite });
      onChanged?.();
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!window.confirm(`「${entry.title}」を削除しますか？`)) return;
    setBusy(true);
    setActionError(null);
    try {
      await api.deleteEntry(entry.id);
      onDeleted?.();
    } catch (e) {
      setActionError((e as Error).message);
      setBusy(false);
    }
  };

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
      {/* ★wt65: サーバ実装済みだったが届いていなかった導線 */}
      <div className="detail-actions">
        <button onClick={() => void toggleFav()} disabled={busy}>
          {entry.isFavorite ? "★ お気に入り解除" : "☆ お気に入り"}
        </button>
        <button className="danger" onClick={() => void remove()} disabled={busy}>
          削除
        </button>
        {busy && <span className="muted">処理中…</span>}
      </div>
      {actionError && <div className="error">{actionError}</div>}
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
      <ConnectionGroups entryId={entry.id} onNavigate={onNavigate} />
      <StickyNotes entryId={entry.id} onNavigate={onNavigate} />
    </div>
  );
}
