import { useEffect, useState } from "react";
import { api, type Connection } from "../api/client";

interface Props {
  entryId: string;
  onNavigate: (title: string) => void;
}

interface Group {
  key: string;
  label: string;
  items: Connection[];
}

/** Android の ConnectionSection と同じグループ化（前提/次/対比/関連/参照）。 */
export function groupConnections(all: Connection[]): Group[] {
  const before: Connection[] = [], next: Connection[] = [], contrast: Connection[] = [], related: Connection[] = [], other: Connection[] = [];
  for (const c of all) {
    const src = c.isSource ?? true;
    switch (c.relationType) {
      case "prerequisite":
      case "extends":
        (src ? next : before).push(c);
        break;
      case "contrast":
      case "contradicts":
        contrast.push(c);
        break;
      case "related":
        related.push(c);
        break;
      default:
        other.push(c);
    }
  }
  const sort = (a: Connection, b: Connection) => b.strength - a.strength || a.otherEntryTitle.localeCompare(b.otherEntryTitle);
  return [
    { key: "before", label: "⬅ 前提（先に）", items: before.sort(sort) },
    { key: "next", label: "➡ 次に進む", items: next.sort(sort) },
    { key: "contrast", label: "↔ 対比・混同注意", items: contrast.sort(sort) },
    { key: "related", label: "🔗 関連", items: related.sort(sort) },
    { key: "other", label: "📎 参照・例示・その他", items: other.sort(sort) },
  ];
}

const COLLAPSED = 6;

export function ConnectionGroups({ entryId, onNavigate }: Props) {
  const [items, setItems] = useState<Connection[]>([]);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  useEffect(() => {
    let cancelled = false;
    setItems([]);
    void api
      .getConnections(entryId)
      .then((c) => {
        if (!cancelled) setItems(c);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [entryId]);
  if (items.length === 0) return null;
  const groups = groupConnections(items).filter((g) => g.items.length > 0);
  return (
    <section className="conn-section">
      <div className="sticky-header">
        <strong>🔗 接続</strong>
        <span className="item-meta">{items.length}本</span>
      </div>
      {groups.map((g) => {
        const open = !!expanded[g.key];
        const shown = open ? g.items : g.items.slice(0, COLLAPSED);
        return (
          <div key={g.key} className="conn-group">
            <div className="conn-group-label">
              {g.label} <span className="item-meta">{g.items.length}</span>
            </div>
            <div className="conn-chips">
              {shown.map((c) => (
                <button
                  key={c.connectionId}
                  className={"conn-chip" + (c.strength >= 0.9 ? " strong" : "")}
                  title={c.note ?? c.relationType}
                  onClick={() => onNavigate(c.otherEntryTitle)}
                >
                  {c.otherEntryTitle}
                  {c.note && <span className="conn-note">{c.note}</span>}
                </button>
              ))}
              {g.items.length > COLLAPSED && (
                <button className="conn-chip more" onClick={() => setExpanded({ ...expanded, [g.key]: !open })}>
                  {open ? "閉じる" : `＋${g.items.length - COLLAPSED}`}
                </button>
              )}
            </div>
          </div>
        );
      })}
    </section>
  );
}
