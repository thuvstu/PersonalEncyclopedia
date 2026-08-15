import { useState } from "react";
import { generateViaOllama } from "../lib/ollamaClient";

const DEFAULTS = {
  baseUrl: "http://192.168.1.10:11434",
  model: "qwen3.6",
};

export function OllamaPanel() {
  const [baseUrl, setBaseUrl] = useState(
    localStorage.getItem("ollama_base_url") ?? DEFAULTS.baseUrl,
  );
  const [model, setModel] = useState(
    localStorage.getItem("ollama_model") ?? DEFAULTS.model,
  );
  const [system, setSystem] = useState(
    "あなたは個人向け知識管理アプリの補助AIです。簡潔に日本語で回答してください。",
  );
  const [prompt, setPrompt] = useState("");
  const [output, setOutput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    setBusy(true);
    setError(null);
    localStorage.setItem("ollama_base_url", baseUrl);
    localStorage.setItem("ollama_model", model);
    try {
      const res = await generateViaOllama(baseUrl, model, prompt, system);
      setOutput(res.content);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>Ollama 直接呼び出し（§7.7）</h2>
      </div>
      <div className="form-grid">
        <label>
          Base URL
          <input value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
        </label>
        <label>
          モデル
          <input value={model} onChange={(e) => setModel(e.target.value)} />
        </label>
      </div>
      <label>
        システムプロンプト
        <textarea value={system} onChange={(e) => setSystem(e.target.value)} rows={2} />
      </label>
      <label>
        プロンプト
        <textarea value={prompt} onChange={(e) => setPrompt(e.target.value)} rows={5} />
      </label>
      <button onClick={() => void run()} disabled={busy || !prompt.trim()}>
        {busy ? "実行中…" : "生成"}
      </button>
      {error && <div className="error">{error}</div>}
      {output && (
        <pre className="ollama-output">{output}</pre>
      )}
    </div>
  );
}
