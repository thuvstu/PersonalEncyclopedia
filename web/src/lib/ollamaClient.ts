export interface OllamaResult {
  content: string;
  raw: unknown;
}

// 設計書§7.7(v14.0新設): PC(React Webクライアント)からLAN内Ollamaへの直接呼び出し。
// データの読み書き自体はKtor API経由でAndroidのRoom DBに行うため、
// 「Android=データ本体」の原則は崩れない。Androidアプリを起動していなくても補助AIを使える。
export async function generateViaOllama(
  baseUrl: string,
  model: string,
  prompt: string,
  system?: string,
): Promise<OllamaResult> {
  const messages: { role: string; content: string }[] = [];
  if (system) messages.push({ role: "system", content: system });
  messages.push({ role: "user", content: prompt });

  const res = await fetch(`${baseUrl}/v1/chat/completions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model, messages }),
  });
  if (!res.ok) {
    throw new Error(`Ollama error ${res.status}: ${await res.text()}`);
  }
  const data = (await res.json()) as {
    choices?: { message?: { content?: string } }[];
  };
  const content = data.choices?.[0]?.message?.content ?? "";
  return { content, raw: data };
}
