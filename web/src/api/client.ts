const STORAGE_KEY = "encyclopedia_settings";

export interface ServerSettings {
  host: string;
  port: string;
  token: string;
}

const DEFAULTS: ServerSettings = {
  host: (import.meta.env.VITE_ANDROID_HOST as string | undefined) ?? "192.168.1.100",
  port: (import.meta.env.VITE_ANDROID_PORT as string | undefined) ?? "8080",
  token: "",
};

let settings: ServerSettings = loadSettings();

export function loadSettings(): ServerSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return { ...DEFAULTS, ...(JSON.parse(raw) as Partial<ServerSettings>) };
  } catch {
    /* ignore corrupt storage */
  }
  return DEFAULTS;
}

export function saveSettings(next: ServerSettings): ServerSettings {
  settings = { ...DEFAULTS, ...next };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  return settings;
}

export function getSettings(): ServerSettings {
  return settings;
}

const BASE_URL = () => `http://${settings.host}:${settings.port}`;

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE_URL()}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${settings.token}`,
      ...(options.headers ?? {}),
    },
  });
  if (!res.ok) {
    throw new Error(`API error ${res.status}: ${await res.text()}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export interface Entry {
  id: string;
  type: string;
  title: string;
  content: string | null;
  summary: string | null;
  sourceUrl: string | null;
  isFavorite: boolean;
  isMuted: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface SrsDueEntry {
  entryId: string;
  title: string;
  term: string;
  definition: string;
  reading: string | null;
  field: string | null;
}

export interface SrsReviewResult {
  entryId: string;
  grade: number;
  intervalDays: number;
  easeFactor: number;
  nextReviewAt: number;
}

export interface Quiz {
  id: string;
  quizType: string;
  question: string;
  choices: string[];
  hints: string[];
  difficulty: number;
  generationMethod: string;
}

export interface QuizAttemptResult {
  quizId: string;
  isCorrect: boolean | null;
  score: number;
  gradingMethod: string;
  correctAnswer: string;
  explanation: string | null;
}

export interface Connection {
  connectionId: string;
  relationType: string;
  strength: number;
  otherEntryId: string;
  otherEntryTitle: string;
  otherEntryType: string;
}

export interface GraphNode {
  src: string;
  dst: string;
  relationType: string;
  strength: number;
  depth: number;
}

export const api = {
  health: () => request<{ status: string }>("/health"),
  getEntries: (limit = 50, offset = 0) =>
    request<Entry[]>(`/api/entries?limit=${limit}&offset=${offset}`),
  getEntry: (id: string) => request<Entry>(`/api/entries/${id}`),
  search: (q: string, limit = 20, type?: string) =>
    request<Entry[]>(
      `/api/search?q=${encodeURIComponent(q)}&limit=${limit}${
        type ? `&type=${type}` : ""
      }`,
    ),
  getConnections: (entryId: string) =>
    request<Connection[]>(`/api/connections?entryId=${entryId}`),
  getGraph: (entryId: string, depth = 3) =>
    request<GraphNode[]>(`/api/graph?entryId=${entryId}&depth=${depth}`),
  createConnection: (entryAId: string, entryBId: string, relationType: string) =>
    request("/api/connections", {
      method: "POST",
      body: JSON.stringify({ entryAId, entryBId, relationType }),
    }),
  getHeatmap: (days = 90) =>
    request<{ day: string; count: number }[]>(`/api/progress/heatmap?days=${days}`),
  getSrsDue: (limit = 30) => request<SrsDueEntry[]>(`/api/srs/due?limit=${limit}`),
  getSrsDueCount: () => request<{ dueCount: number }>(`/api/srs/count`),
  postSrsReview: (entryId: string, grade: number) =>
    request<SrsReviewResult>("/api/srs/review", {
      method: "POST",
      body: JSON.stringify({ entryId, grade }),
    }),
  getQuizzes: (limit = 10, types = ["qa", "mcq", "fill_blank"]) =>
    request<Quiz[]>(`/api/quiz?limit=${limit}&type=${types.join(",")}`),
  postQuizAttempt: (
    quizId: string,
    userAnswer: string,
    answeredWithinMs: number | null = null,
    hintsRevealed = 0,
  ) =>
    request<QuizAttemptResult>(`/api/quiz/${quizId}/attempt`, {
      method: "POST",
      body: JSON.stringify({ userAnswer, answeredWithinMs, hintsRevealed }),
    }),
};