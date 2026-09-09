import { useCallback, useEffect, useState } from "react";
import { api, type StickyLink, type StickyNote } from "../api/client";

const COLORS: Record<string, string> = {
  yellow: "#fff4b8",
  pink: "#ffd6e0",
  blue: "#d6ecff",
  green: "#ddf5d6",
};
const COLOR_KEYS = Object.keys(COLORS);

interface Props {
  entryId: string;
  /** ★wt58: [[リンク]]チップ・新カードのタイトルで遷移 */
  onNavigate?: (title: string) => void;
}

/**
 * ★wt56 付箋（PC側）。Android の StickyNoteSection と同じ意味論:
 * 追加・編集・ピン・解決・削除。昇格(思考化/タスク化/接続候補)はAndroid側のみ。
 */
export function StickyNotes({ entryId, onNavigate }: Props) {
  const [notes, setNotes] = useState<StickyNote[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [color, setColor] = useState("yellow");
  const [showResolved, setShowResolved] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editText, setEditText] = useState("");

  const reload = useCallback(() => {
    void api
      .getStickyNotes(entryId)
      .then(setNotes)
      .catch((e) => setError((e as Error).message));
  }, [entryId]);

  useEffect(() => {
    setNotes([]);
    setError(null);
    setEditingId(null);
    reload();
  }, [reload]);

  const add = async () => {
    const t = text.trim();
    if (!t) return;
    await api.createStickyNote(entryId, t, color);
    setText("");
    reload();
  };

  const patch = async (n: StickyNote, p: Parameters<typeof api.updateStickyNote>[1]) => {
    await api.updateStickyNote(n.id, p);
    reload();
  };

  const remove = async (n: StickyNote) => {
    if (!confirm("この付箋を削除しますか？")) return;
    await api.deleteStickyNote(n.id);
    reload();
  };

  // ★wt58 増殖: リンク解決・派生カード・接続
  const [links, setLinks] = useState<Record<string, StickyLink[]>>({});
  useEffect(() => {
    let cancelled = false;
    void Promise.all(
      notes.filter((n) => n.text.includes("[[")).map((n) => api.getStickyLinks(n.id).then((l) => [n.id, l] as const).catch(() => [n.id, []] as const)),
    ).then((pairs) => {
      if (!cancelled) setLinks(Object.fromEntries(pairs));
    });
    return () => {
      cancelled = true;
    };
  }, [notes]);

  const grow = async (n: StickyNote) => {
    if (n.promotedEntryId) return;
    const title = n.text.split("\n").find((l) => l.trim())?.trim().slice(0, 40) ?? "付箋";
    if (!confirm(`「${title}」を新しいカードにして、このカードと接続しますか？`)) return;
    await api.promoteStickyToDefinition(n.id);
    reload();
    onNavigate?.(title);
  };
  const createFromLink = async (n: StickyNote, title: string) => {
    await api.createCardFromStickyLink(n.id, title);
    reload();
    onNavigate?.(title);
  };
  const connect = async (n: StickyNote) => {
    try {
      await api.connectSticky(n.id);
      reload();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const unresolved = notes.filter((n) => !n.isResolved);
  const resolved = notes.filter((n) => n.isResolved);

  const renderNote = (n: StickyNote) => (
    <div
      key={n.id}
      className={"sticky-note" + (n.isResolved ? " resolved" : "")}
      style={{ background: COLORS[n.color] ?? COLORS.yellow }}
    >
      <input
        type="checkbox"
        checked={n.isResolved}
        title={n.isResolved ? "未解決に戻す" : "解決済みにする"}
        onChange={() => void patch(n, { isResolved: !n.isResolved })}
      />
      {editingId === n.id ? (
        <textarea
          className="sticky-edit"
          value={editText}
          autoFocus
          onChange={(e) => setEditText(e.target.value)}
          onBlur={() => {
            const t = editText.trim();
            setEditingId(null);
            if (t && t !== n.text) void patch(n, { text: t });
          }}
        />
      ) : (
        <div
          className="sticky-text"
          onDoubleClick={() => {
            setEditingId(n.id);
            setEditText(n.text);
          }}
          title="ダブルクリックで編集"
        >
          {n.isPinned && <span className="sticky-pin">📌</span>}
          {n.text}
          {(links[n.id]?.length ?? 0) > 0 && (
            <div className="sticky-links">
              {links[n.id].map((l) =>
                l.entryId ? (
                  <button key={l.title} className="sticky-link" onClick={() => onNavigate?.(l.title)}>
                    🔗 {l.title}
                  </button>
                ) : (
                  <button key={l.title} className="sticky-link new" onClick={() => void createFromLink(n, l.title)}>
                    ＋ {l.title} を作る
                  </button>
                ),
              )}
            </div>
          )}
          {(n.promotedEntryId || n.promotedTaskId || n.promotedCandidateId) && (
            <div className="sticky-badges">
              {n.promotedEntryId && <span>💭 思考化済</span>}
              {n.promotedTaskId && <span>✅ タスク化済</span>}
              {n.promotedCandidateId && <span>🔗 接続候補</span>}
            </div>
          )}
        </div>
      )}
      <div className="sticky-actions">
        <button title={n.isPinned ? "ピンを外す" : "ピン留め"} onClick={() => void patch(n, { isPinned: !n.isPinned })}>
          📌
        </button>
        <button title="新しいカードにする（派生・接続）" disabled={!!n.promotedEntryId} onClick={() => void grow(n)}>
          📖
        </button>
        <button title="[[リンク]]先と今すぐ接続" disabled={!n.text.includes("[[")} onClick={() => void connect(n)}>
          🔗
        </button>
        <button title="削除" onClick={() => void remove(n)}>
          ✕
        </button>
      </div>
    </div>
  );

  return (
    <section className="sticky-section">
      <div className="sticky-header">
        <strong>📝 付箋</strong>
        <span className="item-meta">
          {unresolved.length}枚{resolved.length > 0 ? ` / 解決済み${resolved.length}` : ""}
        </span>
      </div>
      {error && <div className="error">{error}</div>}
      {unresolved.map(renderNote)}
      {resolved.length > 0 && (
        <button className="sticky-toggle" onClick={() => setShowResolved((v) => !v)}>
          {showResolved ? "▲" : "▼"} 解決済み {resolved.length}枚
        </button>
      )}
      {showResolved && resolved.map(renderNote)}
      <div className="sticky-add" style={{ background: COLORS[color] }}>
        <textarea
          placeholder="思ったことを一言…（[[カード名]] でリンク・Ctrl+Enterで保存）"
          value={text}
          rows={2}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === "Enter") void add();
          }}
        />
        <div className="sticky-add-row">
          {COLOR_KEYS.map((k) => (
            <button
              key={k}
              className={"sticky-color" + (k === color ? " selected" : "")}
              style={{ background: COLORS[k] }}
              onClick={() => setColor(k)}
              title={k}
            />
          ))}
          <span style={{ flex: 1 }} />
          <button className="sticky-save" disabled={!text.trim()} onClick={() => void add()}>
            貼る
          </button>
        </div>
      </div>
    </section>
  );
}
