import { useState } from "react";
import { api } from "./api/client";
import { ConnectPanel } from "./components/ConnectPanel";
import { ConnectionBar } from "./components/ConnectionBar";
import { EditorPanel } from "./components/EditorPanel";
import { EntryDetail } from "./components/EntryDetail";
import { EntryList } from "./components/EntryList";
import { GraphView } from "./components/GraphView";
import { OllamaPanel } from "./components/OllamaPanel";
import { QuizPanel } from "./components/QuizPanel";
import { SrsPanel } from "./components/SrsPanel";

type Tab = "entries" | "srs" | "quiz" | "connect" | "edit" | "ollama";

export default function App() {
  const [tab, setTab] = useState<Tab>("entries");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [navError, setNavError] = useState<string | null>(null);

  const navigateToTitle = async (title: string) => {
    setNavError(null);
    try {
      const hits = await api.search(title, 20);
      const exact = hits.find((e) => e.title === title);
      if (exact) {
        setSelectedId(exact.id);
        setTab("entries");
      } else {
        setNavError(`エントリ「${title}」が見つかりません`);
      }
    } catch (e) {
      setNavError((e as Error).message);
    }
  };

  return (
    <div className="app">
      <ConnectionBar onSaved={() => setReloadKey((k) => k + 1)} />
      <nav className="tabs">
        <button
          className={tab === "entries" ? "tab active" : "tab"}
          onClick={() => setTab("entries")}
        >
          エントリ
        </button>
        <button
          className={tab === "srs" ? "tab active" : "tab"}
          onClick={() => setTab("srs")}
        >
          単語帳
        </button>
        <button
          className={tab === "quiz" ? "tab active" : "tab"}
          onClick={() => setTab("quiz")}
        >
          クイズ
        </button>
        <button
          className={tab === "connect" ? "tab active" : "tab"}
          onClick={() => setTab("connect")}
        >
          つながり
        </button>
        <button
          className={tab === "edit" ? "tab active" : "tab"}
          onClick={() => setTab("edit")}
        >
          作成
        </button>
        <button
          className={tab === "ollama" ? "tab active" : "tab"}
          onClick={() => setTab("ollama")}
        >
          Ollama
        </button>
      </nav>
      {navError && <div className="error banner">{navError}</div>}
      {tab === "entries" && (
        <div className={selectedId ? "main-split has-selection" : "main-split"}>
          {/* ★wt65: key ではなく refreshKey で再読込する。key だと再マウントになり
              検索語・型フィルタ・スクロール位置が飛んでいた。 */}
          <EntryList
            refreshKey={reloadKey}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
          <div className="right-column">
            {/* ★wt64: スマホ幅でのみ表示(styles.css の .back-to-list)。PCでは display:none */}
            <button className="back-to-list" onClick={() => setSelectedId(null)}>
              ← 一覧に戻る
            </button>
            <EntryDetail
              entryId={selectedId}
              onNavigate={(t) => void navigateToTitle(t)}
              onChanged={() => setReloadKey((k) => k + 1)}
              onDeleted={() => {
                // ★wt65: 削除済みのエントリを詳細に残さない
                setSelectedId(null);
                setReloadKey((k) => k + 1);
              }}
            />
            <GraphView entryId={selectedId} />
          </div>
        </div>
      )}
      {tab === "srs" && <SrsPanel />}
      {tab === "quiz" && <QuizPanel />}
      {tab === "connect" && <ConnectPanel />}
      {tab === "edit" && <EditorPanel />}
      {tab === "ollama" && <OllamaPanel />}
    </div>
  );
}
