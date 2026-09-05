import { useState } from "react";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { api } from "../api/client";

export function EditorPanel() {
  const [type, setType] = useState("thought");
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const editor = useEditor({
    extensions: [StarterKit],
    content: "",
  });

  const save = async () => {
    setError(null);
    setSaved(null);
    if (!title.trim()) {
      setError("タイトルを入力してください");
      return;
    }
    try {
      const html = editor?.getHTML() ?? "";
      const entry = await api.createEntry(type, title.trim(), html);
      setSaved(`保存しました: ${entry.title} (${entry.id})`);
      setTitle("");
      editor?.commands.clearContent();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="panel">
      <div className="toolbar">
        <h2>作成(Tiptap)</h2>
      </div>
      {error && <div className="error">{error}</div>}
      {saved && <div className="saved">{saved}</div>}
      <div className="row">
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="thought">メモ</option>
          <option value="definition">単語帳</option>
        </select>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="タイトル"
          style={{ flex: 1 }}
        />
      </div>
      <div className="tiptap-wrap">
        <EditorContent editor={editor} />
      </div>
      <div className="row">
        <button onClick={() => void save()}>保存</button>
      </div>
    </div>
  );
}
