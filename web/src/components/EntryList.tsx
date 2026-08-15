import { useEffect, useState } from "react";
import { api, type Entry } from "../api/client";
import { ENTRY_TYPES, typeInfo, formatDate } from "../lib/entryTypes";

interface Props {
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function EntryList({ selectedId, onSelect }: Props) {
  const [query, setQuery] = useState("");
  const [type, setType] = useState<string>("");
  const [entries, setEntries] = useState<Entry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async (q: string, t: string) => {
    setLoading(true);
    setError(null);
    try {
      if (q.trim()) {
        setEntries(await api.search(q.trim(), 100));
      } else {
        const list = await api.getEntries(200, 0);
        setEntries(t ? list.filter((e) => e.type === t) : list);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load("", "");
  }, []);

  const typeNames = Object.keys(ENTRY_TYPES);

  return (
    <div className="entry-pane">
      <div className="toolbar">
        <input
          placeholder="検索…（例: 単語, 概念）"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") void load(query, type);
          }}
        />
        <button onClick={() => void load(query, type)} disabled={loading}>
          検索
        </button>
        <button onClick={() => void load("", type)} disabled={loading}>
          全件
        </button>
      </div>
      <div className="type-chips">
        <button
          className={type === "" ? "chip active" : "chip"}
          onClick={() => {
            setType("");
            void load(query, "");
          }}
        >
          すべて
        </button>
        {typeNames.map((t) => (
          <button
            key={t}
            className={type === t ? "chip active" : "chip"}
            style={{ borderColor: typeInfo(t).colorHex }}
            onClick={() => {
              setType(t);
              void load(query, t);
            }}
          >
            {typeInfo(t).labelJa}
          </button>
        ))}
      </div>
      {error && <div className="error">{error}</div>}
      <ul className="entry-list">
        {entries.map((e) => (
          <li
            key={e.id}
            className={selectedId === e.id ? "item selected" : "item"}
            onClick={() => onSelect(e.id)}
          >
            <span
              className="type-dot"
              style={{ background: typeInfo(e.type).colorHex }}
              title={typeInfo(e.type).labelJa}
            />
            <div className="item-body">
              <div className="item-title">
                {e.title}
                {e.isFavorite && <span className="fav">★</span>}
              </div>
              <div className="item-meta">
                {typeInfo(e.type).labelJa} · {formatDate(e.updatedAt)}
              </div>
            </div>
          </li>
        ))}
        {!loading && entries.length === 0 && (
          <li className="item empty">エントリがありません</li>
        )}
      </ul>
    </div>
  );
}
