import { useEffect, useState } from "react";
import {
  api,
  type Candidate,
  type Connection,
  type Entry,
} from "../api/client";

const RELATION_TYPES = [
  "related",
  "references",
  "contradicts",
  "extends",
  "exemplifies",
  "authored_by",
  "published_by",
  "located_at",
  "occurred_at",
];

async function resolveTitle(title: string): Promise<Entry | null> {
  const hits = await api.search(title, 20);
  return hits.find((e) => e.title === title) ?? hits[0] ?? null;
}

async function titleOf(id: string): Promise<string> {
  try {
    return (await api.getEntry(id)).title;
  } catch {
    return id;
  }
}

export function ConnectPanel() {
  const [error, setError] = useState<string | null>(null);
  const [entryTitle, setEntryTitle] = useState("");
  const [connections, setConnections] = useState<Connection[]>([]);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [candidateTitles, setCandidateTitles] = useState<Record<string, string>>({});
  const [titleA, setTitleA] = useState("");
  const [titleB, setTitleB] = useState("");
  const [relType, setRelType] = useState("related");
  const [heatmap, setHeatmap] = useState<{ day: string; count: number }[]>([]);
  const [dueCount, setDueCount] = useState<number | null>(null);

  const loadCandidates = async () => {
    try {
      const list = await api.getCandidates();
      setCandidates(list);
      const titles: Record<string, string> = {};
      await Promise.all(
        list.map(async (c) => {
          titles[`${c.id}:a`] = await titleOf(c.entryAId);
          titles[`${c.id}:b`] = await titleOf(c.entryBId);
        }),
      );
      setCandidateTitles(titles);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const loadStats = async () => {
    try {
      setHeatmap(await api.getHeatmap(90));
      setDueCount((await api.getSrsDueCount()).dueCount);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  useEffect(() => {
    void loadCandidates();
    void loadStats();
  }, []);

  const loadConnections = async () => {
    setError(null);
    try {
      const entry = await resolveTitle(entryTitle.trim());
      if (!entry) {
        setError(`エントリ「${entryTitle}」が見つかりません`);
        setConnections([]);
        return;
      }
      setConnections(await api.getConnections(entry.id));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const create = async () => {
    setError(null);
    try {
      const a = await resolveTitle(titleA.trim());
      const b = await resolveTitle(titleB.trim());
      if (!a || !b) {
        setError("両方のタイトルが解決できる必要があります");
        return;
      }
      await api.createConnection(a.id, b.id, relType);
      setTitleA("");
      setTitleB("");
      await loadConnections();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const remove = async (id: string) => {
    try {
      await api.deleteConnection(id);
      setConnections((prev) => prev.filter((c) => c.connectionId !== id));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const judge = async (id: string, approve: boolean) => {
    try {
      if (approve) await api.approveCandidate(id);
      else await api.rejectCandidate(id);
      setCandidates((prev) => prev.filter((c) => c.id !== id));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const max = Math.max(1, ...heatmap.map((d) => d.count));

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>つながり・統計</h2>
      </div>
      {error && <div className="error">{error}</div>}

      <h3>接続の検索</h3>
      <div className="row">
        <input
          value={entryTitle}
          onChange={(e) => setEntryTitle(e.target.value)}
          placeholder="エントリタイトル"
        />
        <button onClick={() => void loadConnections()}>表示</button>
      </div>
      {connections.map((c) => (
        <div key={c.connectionId} className="row">
          <span>
            {c.relationType} → {c.otherEntryTitle} ({c.otherEntryType})
          </span>
          <button onClick={() => void remove(c.connectionId)}>削除</button>
        </div>
      ))}

      <h3>接続の作成</h3>
      <div className="row">
        <input
          value={titleA}
          onChange={(e) => setTitleA(e.target.value)}
          placeholder="タイトルA"
        />
        <select value={relType} onChange={(e) => setRelType(e.target.value)}>
          {RELATION_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <input
          value={titleB}
          onChange={(e) => setTitleB(e.target.value)}
          placeholder="タイトルB"
        />
        <button onClick={() => void create()}>作成</button>
      </div>

      <h3>承認待ち候補 ({candidates.length})</h3>
      {candidates.map((c) => (
        <div key={c.id} className="row">
          <span>
            {candidateTitles[`${c.id}:a`] ?? c.entryAId} ↔{" "}
            {candidateTitles[`${c.id}:b`] ?? c.entryBId}（{c.suggestedType}・
            {c.similarity.toFixed(2)}）
          </span>
          <button onClick={() => void judge(c.id, true)}>承認</button>
          <button onClick={() => void judge(c.id, false)}>却下</button>
        </div>
      ))}

      <h3>学習ヒートマップ(90日)</h3>
      <div className="muted">
        復習待ち: {dueCount === null ? "—" : `${dueCount}件`}
      </div>
      <div className="heatmap">
        {heatmap.map((d) => (
          <div
            key={d.day}
            className="heatmap-cell"
            title={`${d.day}: ${d.count}`}
            style={{ height: `${4 + (36 * d.count) / max}px` }}
          />
        ))}
      </div>
    </div>
  );
}
