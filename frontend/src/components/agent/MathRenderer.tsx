'use client';

import type { ReactNode } from 'react';

interface MathRendererProps {
  content: string;
  className?: string;
}

interface ParagraphBlock {
  type: 'paragraph';
  content: string;
}

interface HeadingBlock {
  type: 'heading';
  level: 1 | 2 | 3;
  content: string;
}

interface ListBlock {
  type: 'list';
  ordered: boolean;
  items: string[];
}

interface QuoteBlock {
  type: 'quote';
  content: string;
}

interface CodeBlock {
  type: 'code';
  content: string;
  language?: string;
}

interface RuleBlock {
  type: 'rule';
}

interface TableBlock {
  type: 'table';
  headers: string[];
  rows: string[][];
}

type ContentBlock = ParagraphBlock | HeadingBlock | ListBlock | QuoteBlock | CodeBlock | RuleBlock | TableBlock;

function flushParagraph(buffer: string[], blocks: ContentBlock[]) {
  if (buffer.length === 0) return;
  blocks.push({ type: 'paragraph', content: buffer.join('\n').trim() });
  buffer.length = 0;
}

function splitBlocks(content: string): ContentBlock[] {
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  const blocks: ContentBlock[] = [];
  const paragraphBuffer: string[] = [];
  let codeBuffer: string[] = [];
  let codeLanguage = '';
  let inCodeBlock = false;

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
    const line = lines[lineIndex];
    const trimmed = line.trim();
    const fenceMatch = trimmed.match(/^```(\S*)/);

    if (fenceMatch) {
      if (inCodeBlock) {
        blocks.push({ type: 'code', content: codeBuffer.join('\n'), language: codeLanguage || undefined });
        codeBuffer = [];
        codeLanguage = '';
        inCodeBlock = false;
      } else {
        flushParagraph(paragraphBuffer, blocks);
        codeLanguage = fenceMatch[1] || '';
        inCodeBlock = true;
      }
      continue;
    }

    if (inCodeBlock) {
      codeBuffer.push(line);
      continue;
    }

    if (!trimmed) {
      flushParagraph(paragraphBuffer, blocks);
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,3})\s+(.+)$/);
    if (headingMatch) {
      flushParagraph(paragraphBuffer, blocks);
      blocks.push({
        type: 'heading',
        level: headingMatch[1].length as 1 | 2 | 3,
        content: headingMatch[2],
      });
      continue;
    }

    if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      flushParagraph(paragraphBuffer, blocks);
      blocks.push({ type: 'rule' });
      continue;
    }

    const table = collectTable(lines, lineIndex);
    if (table.rows.length > 0) {
      flushParagraph(paragraphBuffer, blocks);
      blocks.push({ type: 'table', headers: table.headers, rows: table.rows });
      lineIndex += table.consumed - 1;
      continue;
    }

    const unorderedItems = collectListItems(lines, lineIndex, false);
    const orderedItems = collectListItems(lines, lineIndex, true);
    if (unorderedItems.items.length > 0 || orderedItems.items.length > 0) {
      flushParagraph(paragraphBuffer, blocks);
      const list = orderedItems.items.length > 0 ? orderedItems : unorderedItems;
      blocks.push({ type: 'list', ordered: list.ordered, items: list.items });
      lineIndex += list.consumed - 1;
      continue;
    }

    if (trimmed.startsWith('>')) {
      flushParagraph(paragraphBuffer, blocks);
      blocks.push({ type: 'quote', content: trimmed.replace(/^>\s?/, '') });
      continue;
    }

    paragraphBuffer.push(line);
  }

  if (inCodeBlock && codeBuffer.length > 0) {
    blocks.push({ type: 'code', content: codeBuffer.join('\n'), language: codeLanguage || undefined });
  }
  flushParagraph(paragraphBuffer, blocks);

  return blocks.filter((block) => {
    if (block.type === 'rule') return true;
    if (block.type === 'list') return block.items.length > 0;
    if (block.type === 'table') return block.headers.length > 0 && block.rows.length > 0;
    return block.content.trim().length > 0;
  });
}

function collectListItems(lines: string[], start: number, ordered: boolean) {
  const marker = ordered ? /^\s*\d+[.)]\s+(.+)$/ : /^\s*[-*+]\s+(.+)$/;
  const items: string[] = [];
  let index = start;

  while (index < lines.length) {
    const match = lines[index].match(marker);
    if (!match) break;
    items.push(match[1]);
    index += 1;
  }

  return { ordered, items, consumed: index - start };
}

function parseTableRow(line: string) {
  const trimmed = line.trim();
  if (!trimmed.includes('|')) return [];
  return trimmed
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim());
}

function isTableSeparator(line: string) {
  const cells = parseTableRow(line);
  return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell));
}

function collectTable(lines: string[], start: number) {
  const headers = parseTableRow(lines[start]);
  if (headers.length < 2 || start + 1 >= lines.length || !isTableSeparator(lines[start + 1])) {
    return { headers: [], rows: [], consumed: 0 };
  }

  const rows: string[][] = [];
  let index = start + 2;
  while (index < lines.length) {
    const row = parseTableRow(lines[index]);
    if (row.length !== headers.length) break;
    rows.push(row);
    index += 1;
  }

  return { headers, rows, consumed: index - start };
}

function renderInlineText(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\[[^\]]+\]\((https?:\/\/[^)\s]+)\)|\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(<span key={`text-${lastIndex}`}>{text.slice(lastIndex, match.index)}</span>);
    }

    const token = match[0];
    const key = `token-${match.index}`;
    if (token.startsWith('`')) {
      nodes.push(
        <code key={key} className="rounded bg-stone-100 px-1 py-0.5 font-mono text-[0.92em] text-stone-800">
          {token.slice(1, -1)}
        </code>,
      );
    } else if (token.startsWith('**') || token.startsWith('__')) {
      nodes.push(<strong key={key} className="font-semibold text-stone-950">{token.slice(2, -2)}</strong>);
    } else if (token.startsWith('[')) {
      const linkMatch = token.match(/^\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)$/);
      if (linkMatch) {
        nodes.push(
          <a
            key={key}
            href={linkMatch[2]}
            target="_blank"
            rel="noreferrer"
            className="font-medium text-emerald-700 underline underline-offset-4 hover:text-emerald-800"
          >
            {linkMatch[1]}
          </a>,
        );
      }
    } else {
      nodes.push(<em key={key} className="italic">{token.slice(1, -1)}</em>);
    }

    lastIndex = match.index + token.length;
  }

  if (lastIndex < text.length) {
    nodes.push(<span key={`text-${lastIndex}`}>{text.slice(lastIndex)}</span>);
  }

  return nodes;
}

function renderParagraph(content: string, key: string) {
  return (
    <p key={key} className="mb-2 whitespace-pre-wrap leading-7 last:mb-0">
      {renderInlineText(content)}
    </p>
  );
}

export default function MathRenderer({ content, className = '' }: MathRendererProps) {
  if (!content) {
    return null;
  }

  const blocks = splitBlocks(content);

  return (
    <div className={`break-words ${className}`.trim()}>
      {blocks.map((block, index) => {
        if (block.type === 'code') {
          return (
            <div key={`code-wrap-${index}`} className="my-3 overflow-hidden rounded-2xl border border-stone-800 bg-stone-950">
              {block.language && (
                <div className="border-b border-white/10 px-3 py-1.5 font-mono text-[11px] text-stone-400">
                  {block.language}
                </div>
              )}
              <pre className="overflow-x-auto px-3 py-3 text-xs leading-6 text-stone-100">
                <code>{block.content}</code>
              </pre>
            </div>
          );
        }

        if (block.type === 'heading') {
          const classes = [
            'mb-2 mt-4 font-semibold text-stone-950 first:mt-0',
            block.level === 1 ? 'text-xl' : block.level === 2 ? 'text-lg' : 'text-base',
          ].join(' ');
          const children = renderInlineText(block.content);
          if (block.level === 1) return <h1 key={`heading-${index}`} className={classes}>{children}</h1>;
          if (block.level === 2) return <h2 key={`heading-${index}`} className={classes}>{children}</h2>;
          return <h3 key={`heading-${index}`} className={classes}>{children}</h3>;
        }

        if (block.type === 'list') {
          const Tag = block.ordered ? 'ol' : 'ul';
          return (
            <Tag
              key={`list-${index}`}
              className={`mb-3 space-y-1 pl-5 leading-7 last:mb-0 ${block.ordered ? 'list-decimal' : 'list-disc'}`}
            >
              {block.items.map((item, itemIndex) => (
                <li key={`item-${itemIndex}`}>{renderInlineText(item)}</li>
              ))}
            </Tag>
          );
        }

        if (block.type === 'quote') {
          return (
            <blockquote
              key={`quote-${index}`}
              className="my-3 border-l-4 border-emerald-200 bg-emerald-50/70 px-3 py-2 text-sm leading-7 text-stone-700"
            >
              {renderInlineText(block.content)}
            </blockquote>
          );
        }

        if (block.type === 'rule') {
          return <hr key={`rule-${index}`} className="my-4 border-stone-200" />;
        }

        if (block.type === 'table') {
          return (
            <div key={`table-${index}`} className="my-4 overflow-x-auto rounded-2xl border border-stone-200">
              <table className="min-w-full divide-y divide-stone-200 text-left text-sm">
                <thead className="bg-stone-50 text-xs font-semibold uppercase text-stone-500">
                  <tr>
                    {block.headers.map((header, headerIndex) => (
                      <th key={`header-${headerIndex}`} className="px-3 py-2">
                        {renderInlineText(header)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100 bg-white/80 text-stone-700">
                  {block.rows.map((row, rowIndex) => (
                    <tr key={`row-${rowIndex}`}>
                      {row.map((cell, cellIndex) => (
                        <td key={`cell-${cellIndex}`} className="px-3 py-2 align-top leading-6">
                          {renderInlineText(cell)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        }

        return renderParagraph(block.content, `paragraph-${index}`);
      })}
    </div>
  );
}
