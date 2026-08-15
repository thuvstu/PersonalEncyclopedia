export interface EntryTypeInfo {
  labelJa: string;
  colorHex: string;
}

export const ENTRY_TYPES: Record<string, EntryTypeInfo> = {
  thought: { labelJa: "思考・感想", colorHex: "#8B5CF6" },
  definition: { labelJa: "単語・用語", colorHex: "#10B981" },
  webpage: { labelJa: "Webページ", colorHex: "#3B82F6" },
  book: { labelJa: "本", colorHex: "#F59E0B" },
  video: { labelJa: "動画", colorHex: "#EF4444" },
  document: { labelJa: "ドキュメント", colorHex: "#64748B" },
  media: { labelJa: "メディア", colorHex: "#06B6D4" },
  person: { labelJa: "人物", colorHex: "#EC4899" },
  org: { labelJa: "組織", colorHex: "#A855F7" },
  place: { labelJa: "場所", colorHex: "#14B8A6" },
  event: { labelJa: "出来事", colorHex: "#F97316" },
  liked: { labelJa: "いいね収集", colorHex: "#F43F5E" },
  ai_conv: { labelJa: "AI対話", colorHex: "#6366F1" },
};

export function typeInfo(type: string): EntryTypeInfo {
  return ENTRY_TYPES[type] ?? { labelJa: type, colorHex: "#94A3B8" };
}

export function formatDate(ts: number): string {
  return new Date(ts).toLocaleDateString("ja-JP", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}
