import { useEffect, useMemo, useState } from "react";
import { api, type Entry, type StickyNote } from "../api/client";
import { formatDate, typeInfo } from "../lib/entryTypes";

interface Props {
  onSelectTab: (tab: "entries" | "srs" | "quiz" | "connect" | "edit" | "ollama" | "home") => void;
  onNavigate: (title: string) => void;
  onDueCountChange: (count: number | null) => void;
}

interface ActivityDay {
  day: string;
  count: number;
}

function todayLabel(): string {
  return new Intl.DateTimeFormat("ja-JP", {
    weekday: "long",
    month: "long",
    day: "numeric",
  }).format(new Date());
}

function relativeTime(timestamp: number): string {
  const diff = Math.max(0, Date.now() - timestamp);
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "たった今";
  if (minutes < 60) return `${minutes}分前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}時間前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}日前`;
  return formatDate(timestamp);
}

export function Dashboard({ onSelectTab, onNavigate, onDueCountChange }: Props) {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [activity, setActivity] = useState<ActivityDay[]>([]);
  const [notes, setNotes] = useState<StickyNote[]>([]);
  const [dueCount, setDueCount] = useState<number | null>(null);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      const [health, entryResult, dueResult, heatmapResult, notesResult] = await Promise.allSettled([
        api.health(),
        api.getEntries(12, 0),
        api.getSrsDueCount(),
        api.getHeatmap(28),
        api.getRecentStickyNotes(5),
      ]);
      if (cancelled) return;

      setConnected(health.status === "fulfilled");
      if (entryResult.status === "fulfilled") setEntries(entryResult.value);
      if (dueResult.status === "fulfilled") {
        setDueCount(dueResult.value.dueCount);
        onDueCountChange(dueResult.value.dueCount);
      } else {
        setDueCount(null);
        onDueCountChange(null);
      }
      if (heatmapResult.status === "fulfilled") setActivity(heatmapResult.value);
      if (notesResult.status === "fulfilled") setNotes(notesResult.value);
      setLoading(false);
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [onDueCountChange]);

  const maxActivity = Math.max(1, ...activity.map((day) => day.count));
  const activeDays = activity.filter((day) => day.count > 0).length;
  const totalActivity = activity.reduce((sum, day) => sum + day.count, 0);
  const recentEntries = entries.slice(0, 6);
  const firstName = "あなた";

  const activityLabel = useMemo(() => {
    if (!activity.length) return "直近28日の記録";
    return `${activity[0].day} — ${activity[activity.length - 1].day}`;
  }, [activity]);

  return (
    <div className="dashboard">
      <section className="dashboard-hero">
        <div className="hero-copy">
          <div className="eyebrow"><span className="eyebrow-line" /> {todayLabel()}</div>
          <h1>知識を、<em>資産</em>に変える。</h1>
          <p>
            {firstName}の考え、学び、発見をひとつの場所へ。
            <br className="hero-break" />つながりを見つけ、忘れない知識に育てよう。
          </p>
          <div className="hero-actions">
            <button className="button-primary" onClick={() => onSelectTab("edit")} type="button">
              <span>＋</span> 新しい知識を記録
            </button>
            <button className="button-quiet" onClick={() => onSelectTab("entries")} type="button">
              知識ベースを見る <span>→</span>
            </button>
          </div>
          <div className="hero-trust">
            <span className="trust-check">✓</span>
            <span>ローカルファースト</span>
            <span className="trust-separator">·</span>
            <span>あなたのデータはあなたのもの</span>
          </div>
        </div>
        <div className="focus-card">
          <div className="focus-card-top">
            <span className="focus-label">TODAY'S FOCUS</span>
            <span className="focus-spark">✦</span>
          </div>
          <div className="focus-orbit" aria-hidden="true">
            <span className="orbit orbit-one" />
            <span className="orbit orbit-two" />
            <span className="orbit-dot dot-one" />
            <span className="orbit-dot dot-two" />
            <span className="orbit-core">◷</span>
          </div>
          <h2>{dueCount && dueCount > 0 ? `${dueCount}件の復習が待っています` : "今日の知識を育てる"}</h2>
          <p>
            {dueCount && dueCount > 0
              ? "短い復習で、学びを長期記憶へ。"
              : "ひとつのメモから、次の発見が始まります。"}
          </p>
          <button className="focus-link" onClick={() => onSelectTab(dueCount && dueCount > 0 ? "srs" : "edit")} type="button">
            {dueCount && dueCount > 0 ? "復習をはじめる" : "最初の知識を記録する"} <span>↗</span>
          </button>
        </div>
      </section>

      <section className="metric-grid" aria-label="概要">
        <div className="metric-card">
          <div className="metric-icon metric-icon-coral">▤</div>
          <div><span className="metric-label">最近の知識</span><strong>{loading ? "—" : entries.length}</strong><span className="metric-suffix">件</span></div>
          <span className="metric-trend">今週</span>
        </div>
        <div className="metric-card">
          <div className="metric-icon metric-icon-blue">◷</div>
          <div><span className="metric-label">復習待ち</span><strong>{loading || dueCount === null ? "—" : dueCount}</strong><span className="metric-suffix">件</span></div>
          <span className="metric-trend metric-trend-soft">SRS</span>
        </div>
        <div className="metric-card">
          <div className="metric-icon metric-icon-green">✦</div>
          <div><span className="metric-label">記録した日</span><strong>{loading ? "—" : activeDays}</strong><span className="metric-suffix">日</span></div>
          <span className="metric-trend metric-trend-soft">28日間</span>
        </div>
        <div className="metric-card metric-card-highlight">
          <div className="metric-icon metric-icon-dark">⌁</div>
          <div><span className="metric-label">学びのリズム</span><strong>{loading ? "—" : totalActivity}</strong><span className="metric-suffix">アクション</span></div>
          <span className="metric-trend">↗</span>
        </div>
      </section>

      <section className="dashboard-columns">
        <div className="surface recent-surface">
          <div className="surface-heading">
            <div><span className="section-kicker">YOUR LIBRARY</span><h2>最近の知識</h2></div>
            <button className="surface-link" onClick={() => onSelectTab("entries")} type="button">すべて見る <span>→</span></button>
          </div>
          {recentEntries.length > 0 ? (
            <div className="recent-list">
              {recentEntries.map((entry) => {
                const info = typeInfo(entry.type);
                return (
                  <button className="recent-item" key={entry.id} onClick={() => onNavigate(entry.title)} type="button">
                    <span className="recent-type-mark" style={{ backgroundColor: info.colorHex }} />
                    <span className="recent-item-main">
                      <strong>{entry.title}</strong>
                      <span>{info.labelJa} <i>·</i> {relativeTime(entry.updatedAt)}</span>
                    </span>
                    <span className="recent-arrow">↗</span>
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="empty-library">
              <div className="empty-library-icon">✦</div>
              <strong>{connected ? "まだ知識がありません" : "まずは端末に接続しましょう"}</strong>
              <p>{connected ? "最初の記録を作ると、ここに表示されます。" : "Androidアプリと接続すると、あなたの知識がここに現れます。"}</p>
              <button className="button-secondary" onClick={() => onSelectTab(connected ? "edit" : "connect")} type="button">
                {connected ? "最初の知識を記録" : "接続設定を開く"}
              </button>
            </div>
          )}
        </div>

        <div className="surface rhythm-surface">
          <div className="surface-heading">
            <div><span className="section-kicker">YOUR RHYTHM</span><h2>学びのリズム</h2></div>
            <span className="surface-period">28日間</span>
          </div>
          <div className="activity-summary"><strong>{totalActivity}</strong><span>回のアクション</span><span className="activity-change">{activeDays > 0 ? `· ${activeDays}日活動` : "· ここから始めよう"}</span></div>
          <div className="activity-chart" aria-label={activityLabel}>
            {(activity.length ? activity : Array.from({ length: 28 }, (_, index) => ({ day: String(index), count: 0 }))).map((day, index) => (
              <span
                className={`activity-bar ${day.count > 0 ? "has-value" : ""}`}
                key={`${day.day}-${index}`}
                title={`${day.day}: ${day.count}件`}
                style={{ height: `${day.count ? 18 + (day.count / maxActivity) * 60 : 8}px` }}
              />
            ))}
          </div>
          <div className="activity-axis"><span>28日前</span><span>今日</span></div>
          <div className="rhythm-footer"><span className="rhythm-dot" /> 記録・復習・発見の合計</div>
        </div>
      </section>

      {notes.length > 0 && (
        <section className="surface notes-surface">
          <div className="surface-heading">
            <div><span className="section-kicker">QUICK THOUGHTS</span><h2>最近の付箋</h2></div>
            <button className="surface-link" onClick={() => onSelectTab("entries")} type="button">知識ベースへ <span>→</span></button>
          </div>
          <div className="dashboard-notes">
            {notes.slice(0, 3).map((note) => <button key={note.id} onClick={() => onSelectTab("entries")} type="button">{note.text}<span>{relativeTime(note.updatedAt)} ↗</span></button>)}
          </div>
        </section>
      )}

      {!connected && (
        <section className="connection-callout">
          <div className="callout-icon">⌁</div>
          <div className="callout-copy"><span className="section-kicker">PRIVATE BY DEFAULT</span><h2>知識を安全に、ひとつに。</h2><p>Personal EncyclopediaはAndroid端末を知識の保管庫にします。クラウドに預けず、必要なときだけこのワークスペースからアクセスできます。</p></div>
          <button className="button-dark" onClick={() => onSelectTab("connect")} type="button">接続を設定 <span>→</span></button>
        </section>
      )}
    </div>
  );
}
