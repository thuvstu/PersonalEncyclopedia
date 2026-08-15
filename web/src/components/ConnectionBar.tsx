import { useState } from "react";
import { api, getSettings, saveSettings } from "../api/client";

export function ConnectionBar({ onSaved }: { onSaved: () => void }) {
  const current = getSettings();
  const [host, setHost] = useState(current.host);
  const [port, setPort] = useState(current.port);
  const [token, setToken] = useState(current.token);
  const [status, setStatus] = useState<string>("");
  const [busy, setBusy] = useState(false);

  const testConnection = async (persist: boolean) => {
    setBusy(true);
    setStatus("");
    try {
      if (persist) saveSettings({ host, port, token });
      const health = await api.health();
      setStatus(`接続OK (${health.status})`);
      if (persist) onSaved();
    } catch (e) {
      setStatus(`接続失敗: ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <header className="connection-bar">
      <span className="brand">Personal Encyclopedia</span>
      <label>
        ホスト
        <input value={host} onChange={(e) => setHost(e.target.value)} />
      </label>
      <label>
        ポート
        <input
          value={port}
          onChange={(e) => setPort(e.target.value)}
          className="port-input"
        />
      </label>
      <label>
        トークン
        <input
          value={token}
          onChange={(e) => setToken(e.target.value)}
          type="password"
          placeholder="Android側で表示"
        />
      </label>
      <button disabled={busy} onClick={() => testConnection(false)}>
        接続テスト
      </button>
      <button disabled={busy} onClick={() => testConnection(true)}>
        保存
      </button>
      {status && <span className="conn-status">{status}</span>}
    </header>
  );
}
