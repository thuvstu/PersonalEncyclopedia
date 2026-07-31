const ANDROID_HOST = import.meta.env.VITE_ANDROID_HOST ?? "192.168.1.100";
const ANDROID_PORT = import.meta.env.VITE_ANDROID_PORT ?? "8080";
const BASE_URL = `http://${ANDROID_HOST}:${ANDROID_PORT}`;

let accessToken = localStorage.getItem("encyclopedia_token") ?? "";

export function setToken(token: string) {
  accessToken = token;
  localStorage.setItem("encyclopedia_token", token);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
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
  isFavorite: boolean;
  createdAt: number;
  updatedAt: number;
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
  search: (q: string, mode = "hybrid") =>
    request<Entry[]>(`/api/search?q=${encodeURIComponent(q)}&mode=${mode}`),
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
};