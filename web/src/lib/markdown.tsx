import { Fragment, ReactNode } from "react";

interface InlineCtx {
  onNavigate?: (title: string) => void;
}

function inlineText(text: string, ctx: InlineCtx, keyPrefix: string): ReactNode[] {
  // インライン要素を順に分解。安全のためHTML文字列は一切使わずReact要素のみで構築する。
  const nodes: ReactNode[] = [];
  const regex =
    /(\[\[([^\]|]+)(?:\|([^\]]+))?\]\])|(\{([^{}|]+)\|([^{}]+)\})|(`([^`]+)`)|(\*\*([^*]+)\*\*)|(\*([^*]+)\*)/g;
  let last = 0;
  let m: RegExpExecArray | null;
  let i = 0;
  while ((m = regex.exec(text)) !== null) {
    if (m.index > last) {
      nodes.push(
        <Fragment key={`${keyPrefix}-t${i++}`}>
          {text.slice(last, m.index)}
        </Fragment>,
      );
    }
    if (m[2] !== undefined) {
      const title = m[2].trim();
      const label = (m[3] ?? m[2]).trim();
      nodes.push(
        <a
          key={`${keyPrefix}-w${i++}`}
          className="wikilink"
          href={`#entry:${encodeURIComponent(title)}`}
          onClick={(e) => {
            e.preventDefault();
            ctx.onNavigate?.(title);
          }}
        >
          {label}
        </a>,
      );
    } else if (m[5] !== undefined) {
      nodes.push(
        <ruby key={`${keyPrefix}-r${i++}`}>
          {m[5]}
          <rt>{m[6]}</rt>
        </ruby>,
      );
    } else if (m[7] !== undefined) {
      nodes.push(<code key={`${keyPrefix}-c${i++}`}>{m[8]}</code>);
    } else if (m[9] !== undefined) {
      nodes.push(
        <strong key={`${keyPrefix}-b${i++}`}>
          {inlineText(m[10], ctx, `${keyPrefix}-b${i}`)}
        </strong>,
      );
    } else if (m[11] !== undefined) {
      nodes.push(
        <em key={`${keyPrefix}-e${i++}`}>{m[12]}</em>,
      );
    }
    last = m.index + m[0].length;
  }
  if (last < text.length) {
    nodes.push(
      <Fragment key={`${keyPrefix}-t${i++}`}>{text.slice(last)}</Fragment>,
    );
  }
  return nodes;
}

function inlineChildren(text: string, ctx: InlineCtx, keyPrefix: string): ReactNode {
  return <>{inlineText(text, ctx, keyPrefix)}</>;
}

export function Markdown({
  source,
  onNavigate,
  className,
}: {
  source: string;
  onNavigate?: (title: string) => void;
  className?: string;
}) {
  const ctx: InlineCtx = { onNavigate };
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let i = 0;
  let codeBlock: string[] | null = null;

  while (i < lines.length) {
    const line = lines[i];

    if (codeBlock) {
      if (line.startsWith("```")) {
        blocks.push(
          <pre key={`pre${blocks.length}`}>
            <code>{codeBlock.join("\n")}</code>
          </pre>,
        );
        codeBlock = null;
      } else {
        codeBlock.push(line);
      }
      i++;
      continue;
    }

    if (line.startsWith("```")) {
      codeBlock = [];
      i++;
      continue;
    }

    const trimmed = line.trim();
    if (trimmed === "") {
      i++;
      continue;
    }
    if (trimmed === "---") {
      blocks.push(<hr key={`hr${blocks.length}`} />);
      i++;
      continue;
    }
    const heading = /^(#{1,3})\s+(.*)$/.exec(trimmed);
    if (heading) {
      const level = heading[1].length;
      const content = heading[2];
      const key = `h${blocks.length}`;
      if (level === 1) blocks.push(<h1 key={key}>{inlineChildren(content, ctx, key)}</h1>);
      else if (level === 2) blocks.push(<h2 key={key}>{inlineChildren(content, ctx, key)}</h2>);
      else blocks.push(<h3 key={key}>{inlineChildren(content, ctx, key)}</h3>);
      i++;
      continue;
    }
    if (trimmed.startsWith(">")) {
      const quote: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith(">")) {
        quote.push(lines[i].trim().replace(/^>\s?/, ""));
        i++;
      }
      blocks.push(
        <blockquote key={`q${blocks.length}`}>
          {inlineChildren(quote.join("\n"), ctx, `q${blocks.length}`)}
        </blockquote>,
      );
      continue;
    }
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
      const items: string[] = [];
      while (i < lines.length) {
        const t = lines[i].trim();
        if (t.startsWith("- ") || t.startsWith("* ")) {
          items.push(t.slice(2));
          i++;
        } else if (t === "") {
          i++;
          break;
        } else {
          break;
        }
      }
      blocks.push(
        <ul key={`ul${blocks.length}`}>
          {items.map((it, idx) => (
            <li key={idx}>{inlineChildren(it, ctx, `ul${blocks.length}-${idx}`)}</li>
          ))}
        </ul>,
      );
      continue;
    }
    if (/^\d+\.\s/.test(trimmed)) {
      const items: string[] = [];
      while (i < lines.length) {
        const t = lines[i].trim();
        if (/^\d+\.\s/.test(t)) {
          items.push(t.replace(/^\d+\.\s/, ""));
          i++;
        } else if (t === "") {
          i++;
          break;
        } else {
          break;
        }
      }
      blocks.push(
        <ol key={`ol${blocks.length}`}>
          {items.map((it, idx) => (
            <li key={idx}>{inlineChildren(it, ctx, `ol${blocks.length}-${idx}`)}</li>
          ))}
        </ol>,
      );
      continue;
    }

    // 通常段落（後続の空行・別ブロックまでまとめる）
    const para: string[] = [line];
    i++;
    while (i < lines.length) {
      const t = lines[i];
      if (t.trim() === "" || t.startsWith("#") || t.startsWith("```") || t.startsWith(">")) break;
      if (/^[-*]\s/.test(t.trim()) || /^\d+\.\s/.test(t.trim())) break;
      para.push(t);
      i++;
    }
    blocks.push(
      <p key={`p${blocks.length}`}>
        {inlineChildren(para.join("\n"), ctx, `p${blocks.length}`)}
      </p>,
    );
  }

  if (codeBlock) {
    blocks.push(
      <pre key={`pre${blocks.length}`}>
        <code>{codeBlock.join("\n")}</code>
      </pre>,
    );
  }

  return <div className={className}>{blocks}</div>;
}
