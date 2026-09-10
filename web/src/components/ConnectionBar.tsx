import { useState } from "react";
import { api, getSettings, saveSettings } from "../api/client";

export function ConnectionBar({ onSaved }: { onSaved: () => void }) {
  const current = getSettings();
  const [host, setHost] = useState(current.host);
  const [port, setPort] = useState(current.port);
  const [token, setToken] = useState(current.token);
  const [status, setStatus] = useState<string>("");
  const [busy, setBusy] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const isConnected = status.startsWith("接続OK");

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
    <div className="connection-widget">
      <button
        className="connection-pill"
        onClick={() => setExpanded((open) => !open)}
        type="button"
        aria-expanded={expanded}
        aria-label="接続設定"
      >
        <span className={`connection-dot ${isConnected ? "online" : ""}`} />
        <span className="connection-pill-label">{isConnected ? "接続中" : "未接続"}</span>
        <span className="connection-chevron">⌄</span>
      </button>
      {expanded && (
        <div className="connection-popover">
          <div className="popover-heading"><div><strong>Androidと接続</strong><span>データは端末に保存されます</span></div><button onClick={() => setExpanded(false)} type="button" aria-label="閉じる">×</button></div>
          <label>ホスト<input value={host} onChange={(e) => setHost(e.target.value)} /></label>
          <label>ポート<input value={port} onChange={(e) => setPort(e.target.value)} inputMode="numeric" /></label>
          <label>トークン<input value={token} onChange={(e) => setToken(e.target.value)} type="password" placeholder="Android側で表示" /></label>
          <div className="connection-popover-actions">
            <button className="button-secondary" disabled={busy} onClick={() => void testConnection(false)} type="button">テスト</button>
            <button className="button-primary compact" disabled={busy} onClick={() => void testConnection(true)} type="button">{busy ? "接続中…" : "保存して接続"}</button>
          </div>
          {status && <div className={`connection-result ${isConnected ? "success" : "failure"}`}>{status}</div>}
        </div>
      )}
    </div>
  );
}
