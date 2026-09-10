import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "./api/client";
import { ConnectPanel } from "./components/ConnectPanel";
import { ConnectionBar } from "./components/ConnectionBar";
import { Dashboard } from "./components/Dashboard";
import { EditorPanel } from "./components/EditorPanel";
import { EntryDetail } from "./components/EntryDetail";
import { EntryList } from "./components/EntryList";
import { GraphView } from "./components/GraphView";
import { OllamaPanel } from "./components/OllamaPanel";
import { QuizPanel } from "./components/QuizPanel";
import { SrsPanel } from "./components/SrsPanel";

type Tab = "home" | "entries" | "srs" | "quiz" | "connect" | "edit" | "ollama";

interface NavItemProps {
  tab: Tab;
  activeTab: Tab;
  icon: string;
  label: string;
  badge?: number | null;
  onSelect: (tab: Tab) => void;
}

function NavItem({ tab, activeTab, icon, label, badge, onSelect }: NavItemProps) {
  return (
    <button
      className={`side-nav-item ${activeTab === tab ? "active" : ""}`}
      onClick={() => onSelect(tab)}
      type="button"
    >
      <span className="nav-icon" aria-hidden="true">{icon}</span>
      <span>{label}</span>
      {badge != null && badge > 0 && <span className="nav-badge">{badge}</span>}
    </button>
  );
}

const TAB_LABELS: Record<Tab, string> = {
  home: "ホーム",
  entries: "知識ベース",
  srs: "復習",
  quiz: "クイズ",
  connect: "つながり",
  edit: "新しい記録",
  ollama: "AI Lab",
};

export default function App() {
  const [tab, setTab] = useState<Tab>("home");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [navError, setNavError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [entrySearch, setEntrySearch] = useState("");
  const [dueCount, setDueCount] = useState<number | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const focusSearch = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener("keydown", focusSearch);
    return () => window.removeEventListener("keydown", focusSearch);
  }, []);

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

  const selectTab = (next: Tab) => {
    setNavError(null);
    setTab(next);
  };

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = search.trim();
    if (!query) return;
    setEntrySearch(query);
    setTab("entries");
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true">P</div>
          <div>
            <div className="brand-name">Personal</div>
            <div className="brand-name brand-name-light">Encyclopedia</div>
          </div>
        </div>
        <div className="workspace-switcher">
          <span className="workspace-avatar">私</span>
          <span className="workspace-copy">
            <strong>マイ・ワークスペース</strong>
            <small>ローカル保存</small>
          </span>
          <span className="workspace-chevron">⌄</span>
        </div>

        <nav className="side-nav" aria-label="メインナビゲーション">
          <div className="nav-label">WORKSPACE</div>
          <NavItem tab="home" activeTab={tab} icon="⌂" label="ホーム" onSelect={selectTab} />
          <NavItem tab="entries" activeTab={tab} icon="▤" label="知識ベース" onSelect={selectTab} />
          <NavItem tab="srs" activeTab={tab} icon="◷" label="復習" badge={dueCount} onSelect={selectTab} />
          <NavItem tab="quiz" activeTab={tab} icon="✦" label="クイズ" onSelect={selectTab} />

          <div className="nav-label nav-label-spaced">EXPLORE</div>
          <NavItem tab="connect" activeTab={tab} icon="⌘" label="つながり" onSelect={selectTab} />
          <NavItem tab="ollama" activeTab={tab} icon="✧" label="AI Lab" onSelect={selectTab} />
        </nav>

        <div className="sidebar-bottom">
          <button className="side-nav-item capture-link" onClick={() => selectTab("edit")} type="button">
            <span className="nav-icon">＋</span>
            <span>知識を記録する</span>
          </button>
          <div className="sidebar-rule" />
          <div className="sidebar-footer-note">
            <span className="privacy-dot" />
            <span>あなたの知識は、あなたの端末に。</span>
          </div>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="breadcrumb">
            <span className="mobile-brand-mark">P</span>
            <span className="breadcrumb-muted">Workspace</span>
            <span className="breadcrumb-separator">/</span>
            <strong>{TAB_LABELS[tab]}</strong>
          </div>
          <form className="global-search" onSubmit={submitSearch} role="search">
            <span className="search-icon" aria-hidden="true">⌕</span>
            <input
              ref={searchRef}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="知識を検索…"
              aria-label="知識を検索"
            />
            <kbd>⌘ K</kbd>
          </form>
          <div className="topbar-actions">
            <button className="topbar-new" onClick={() => selectTab("edit")} type="button">
              <span>＋</span> 新しく記録
            </button>
            <ConnectionBar onSaved={() => setReloadKey((key) => key + 1)} />
            <div className="user-avatar" title="マイ・ワークスペース">私</div>
          </div>
        </header>

        {navError && <div className="error banner">{navError}</div>}
        <main className="page-body">
          {tab === "home" && (
            <Dashboard
              onSelectTab={selectTab}
              onNavigate={navigateToTitle}
              onDueCountChange={setDueCount}
            />
          )}
          {tab === "entries" && (
            <div className="main-split">
              <EntryList
                key={`${reloadKey}-${entrySearch}`}
                selectedId={selectedId}
                onSelect={setSelectedId}
                initialQuery={entrySearch}
              />
              <div className="right-column">
                <EntryDetail entryId={selectedId} onNavigate={(title) => void navigateToTitle(title)} />
                <GraphView entryId={selectedId} />
              </div>
            </div>
          )}
          {tab === "srs" && <SrsPanel />}
          {tab === "quiz" && <QuizPanel />}
          {tab === "connect" && <ConnectPanel />}
          {tab === "edit" && <EditorPanel />}
          {tab === "ollama" && <OllamaPanel />}
        </main>
      </div>
    </div>
  );
}
