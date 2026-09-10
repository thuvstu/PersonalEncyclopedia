import type { Entry, StickyNote } from "../api/client";

const now = Date.now();
const hoursAgo = (hours: number) => now - hours * 60 * 60 * 1000;

/**
 * Webクライアント単体で価値を確認できるようにした、少量のプレビュー用データ。
 * Androidへ接続できた場合は実データを優先し、このデータは表示しない。
 */
export const DEMO_ENTRIES: Entry[] = [
  {
    id: "demo-active-recall",
    type: "definition",
    title: "アクティブリコール",
    content: "学習した内容を読み返すのではなく、何も見ずに思い出す学習法。思い出せなかった箇所が、そのまま次に復習すべき場所になる。知識を「わかったつもり」から「使える状態」へ移すための基本技術。",
    summary: "思い出す行為そのものを学習にする方法。",
    sourceUrl: null,
    isFavorite: true,
    isMuted: false,
    createdAt: hoursAgo(5),
    updatedAt: hoursAgo(2),
  },
  {
    id: "demo-ai-evidence",
    type: "thought",
    title: "生成AIの幻覚は、検索ではなく根拠管理の問題",
    content: "回答の正しさだけを評価するのではなく、どの主張がどの根拠に支えられているかを追跡できる設計が必要。正解率を上げるだけでは、重要な意思決定で安心して使えない。",
    summary: "AIを便利にする鍵は、もっともらしさではなく根拠の可視化。",
    sourceUrl: null,
    isFavorite: true,
    isMuted: false,
    createdAt: hoursAgo(27),
    updatedAt: hoursAgo(20),
  },
  {
    id: "demo-mesopotamia",
    type: "thought",
    title: "メソポタミアを「文明の始まり」とだけ覚えない",
    content: "都市、文字、制度、交易は同時に生まれたのではない。灌漑農業の余剰、神殿を中心にした再分配、債務と労働の記録が重なり、都市国家という制度になった。年表ではなく、複数の条件が接続する過程として理解したい。",
    summary: "文明を単一の発明ではなく、制度の束として捉え直す。",
    sourceUrl: null,
    isFavorite: false,
    isMuted: false,
    createdAt: hoursAgo(43),
    updatedAt: hoursAgo(31),
  },
  {
    id: "demo-hash-table",
    type: "definition",
    title: "ハッシュテーブル",
    content: "キーをハッシュ関数で配列の位置に変換し、キーと値の組を管理するデータ構造。平均的な検索は O(1) だが、衝突の扱いと負荷率の管理が性能を左右する。速さだけでなく、最悪時の振る舞いまで記録する。",
    summary: "平均 O(1) の検索を実現する、キーと値のための構造。",
    sourceUrl: null,
    isFavorite: false,
    isMuted: false,
    createdAt: hoursAgo(61),
    updatedAt: hoursAgo(46),
  },
  {
    id: "demo-knowledge-card",
    type: "thought",
    title: "知識カードには、必ず次の問いを残す",
    content: "完成した説明だけを保存すると、カードは読むだけの資料になる。最後に「何と混同するか」「どの条件で崩れるか」「次に何を調べるか」のいずれかを残すと、知識が次の探索を生む。",
    summary: "カードの価値は、答えだけでなく次の問いまで含めて決まる。",
    sourceUrl: null,
    isFavorite: true,
    isMuted: false,
    createdAt: hoursAgo(75),
    updatedAt: hoursAgo(70),
  },
  {
    id: "demo-recursion",
    type: "definition",
    title: "再帰と基底ケース",
    content: "問題を小さな同型の問題へ分解し、自分自身を呼び出して解く方法。必ず基底ケース（これ以上分解しない条件）を定義する。基底ケースは、知識を実装へ移すときの「終了条件」の考え方にもつながる。",
    summary: "再帰を理解する入口は、呼び出し方より終了条件。",
    sourceUrl: null,
    isFavorite: false,
    isMuted: false,
    createdAt: hoursAgo(92),
    updatedAt: hoursAgo(84),
  },
];

export const DEMO_ACTIVITY = [
  0, 1, 0, 2, 1, 0, 3, 0, 1, 2, 0, 0, 2, 4,
  1, 0, 2, 1, 0, 3, 2, 0, 1, 2, 0, 4, 2, 3,
].map((count, index) => ({
  day: `${28 - index}日前`,
  count,
}));

export const DEMO_NOTES: StickyNote[] = [
  {
    id: "demo-note-1",
    entryId: "demo-ai-evidence",
    text: "「正しそう」と「根拠が追える」は別の品質。評価軸を分ける。",
    color: "yellow",
    source: "demo",
    isPinned: true,
    isResolved: false,
    promotedEntryId: "demo-ai-evidence",
    promotedTaskId: null,
    promotedCandidateId: null,
    createdAt: hoursAgo(4),
    updatedAt: hoursAgo(4),
  },
  {
    id: "demo-note-2",
    entryId: "demo-mesopotamia",
    text: "「四大文明」という並べ方が隠している前提を調べる。",
    color: "blue",
    source: "demo",
    isPinned: false,
    isResolved: false,
    promotedEntryId: null,
    promotedTaskId: null,
    promotedCandidateId: null,
    createdAt: hoursAgo(18),
    updatedAt: hoursAgo(18),
  },
  {
    id: "demo-note-3",
    entryId: "demo-active-recall",
    text: "復習の正解率ではなく、次に思い出せるまでの間隔を見る。",
    color: "green",
    source: "demo",
    isPinned: false,
    isResolved: false,
    promotedEntryId: null,
    promotedTaskId: null,
    promotedCandidateId: null,
    createdAt: hoursAgo(36),
    updatedAt: hoursAgo(36),
  },
];

export const DEMO_DUE_COUNT = 4;
