import { useEffect, useMemo, useState } from "react";
import {
  Background,
  Controls,
  ReactFlow,
  type Edge,
  type Node,
} from "@xyflow/react";
import { api, type GraphNode } from "../api/client";
import { typeInfo } from "../lib/entryTypes";

interface Props {
  entryId: string | null;
}

export function GraphView({ entryId }: Props) {
  const [raw, setRaw] = useState<GraphNode[]>([]);
  const [depth, setDepth] = useState(3);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [titles, setTitles] = useState<Record<string, { title: string; type: string }>>({});

  const loadGraph = async (id: string, d: number) => {
    setLoading(true);
    setError(null);
    try {
      const edges = await api.getGraph(id, d);
      setRaw(edges);
      const ids = new Set<string>();
      for (const e of edges) {
        ids.add(e.src);
        ids.add(e.dst);
      }
      const info: Record<string, { title: string; type: string }> = {};
      await Promise.all(
        [...ids].map(async (nid) => {
          try {
            const entry = await api.getEntry(nid);
            info[nid] = { title: entry.title, type: entry.type };
          } catch {
            info[nid] = { title: nid, type: "unknown" };
          }
        }),
      );
      setTitles(info);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (entryId) void loadGraph(entryId, depth);
    else setRaw([]);
  }, [entryId]);

  const graph = useMemo(() => {
    const depthOf = new Map<string, number>();
    for (const edge of raw) {
      depthOf.set(edge.src, Math.min(depthOf.get(edge.src) ?? Infinity, edge.depth - 1));
      depthOf.set(edge.dst, Math.min(depthOf.get(edge.dst) ?? Infinity, edge.depth));
    }
    const center = raw[0]?.src ?? "";
    if (center) depthOf.set(center, 0);

    const byDepth = new Map<number, string[]>();
    for (const [id, d] of depthOf) {
      if (!byDepth.has(d)) byDepth.set(d, []);
      byDepth.get(d)!.push(id);
    }
    const positions = new Map<string, { x: number; y: number }>();
    for (const [d, ids] of byDepth) {
      ids.forEach((id, idx) => {
        const angle = (2 * Math.PI * idx) / Math.max(ids.length, 1) - Math.PI / 2;
        const radius = d === 0 ? 0 : d * 240;
        positions.set(id, { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius });
      });
    }

    const nodes: Node[] = [...depthOf.keys()].map((id) => {
      const info = titles[id];
      const color = info ? typeInfo(info.type).colorHex : "#94A3B8";
      return {
        id,
        position: positions.get(id) ?? { x: 0, y: 0 },
        data: { label: info?.title ?? id },
        style: {
          background: color,
          color: "#fff",
          borderRadius: 8,
          padding: "6px 10px",
          border: id === center ? "2px solid #000" : "none",
        },
      };
    });

    const edges: Edge[] = raw.map((e, i) => ({
      id: `${e.src}-${e.dst}-${i}`,
      source: e.src,
      target: e.dst,
      label: e.relationType,
      style: { strokeWidth: 1 + Math.min(e.strength, 1) * 2 },
      labelStyle: { fontSize: 10 },
    }));

    return { nodes, edges, centerTitle: titles[center]?.title ?? "" };
  }, [raw, titles]);

  if (!entryId) {
    return <div className="graph-pane placeholder">左の一覧からエントリを選択すると関連グラフを表示します</div>;
  }

  return (
    <div className="graph-pane">
      <div className="toolbar">
        <span>
          中心: <strong>{graph.centerTitle || entryId}</strong>
        </span>
        <label>
          深さ
          <input
            type="number"
            min={1}
            max={6}
            value={depth}
            onChange={(e) => setDepth(Number(e.target.value))}
          />
        </label>
        <button onClick={() => void loadGraph(entryId, depth)} disabled={loading}>
          再描画
        </button>
      </div>
      {error && <div className="error">{error}</div>}
      <ReactFlow
        nodes={graph.nodes}
        edges={graph.edges}
        fitView
        proOptions={{ hideAttribution: true }}
      >
        <Background />
        <Controls />
      </ReactFlow>
    </div>
  );
}
