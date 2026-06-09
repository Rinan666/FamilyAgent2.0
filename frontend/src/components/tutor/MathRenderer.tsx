'use client';

interface MathRendererProps {
  content: string;
  className?: string;
}

interface TextBlock {
  type: 'text';
  content: string;
}

interface CodeBlock {
  type: 'code';
  content: string;
}

type ContentBlock = TextBlock | CodeBlock;

function splitBlocks(content: string): ContentBlock[] {
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  const blocks: ContentBlock[] = [];
  let textBuffer: string[] = [];
  let codeBuffer: string[] = [];
  let inCodeBlock = false;

  const flushText = () => {
    if (textBuffer.length === 0) return;
    blocks.push({ type: 'text', content: textBuffer.join('\n').trim() });
    textBuffer = [];
  };

  const flushCode = () => {
    if (codeBuffer.length === 0) return;
    blocks.push({ type: 'code', content: codeBuffer.join('\n') });
    codeBuffer = [];
  };

  lines.forEach((line) => {
    if (line.trim().startsWith('```')) {
      if (inCodeBlock) {
        flushCode();
      } else {
        flushText();
      }
      inCodeBlock = !inCodeBlock;
      return;
    }

    if (inCodeBlock) {
      codeBuffer.push(line);
      return;
    }

    textBuffer.push(line);
  });

  if (inCodeBlock) {
    flushCode();
  } else {
    flushText();
  }

  return blocks.filter((block) => block.content.trim().length > 0);
}

function renderInlineText(text: string) {
  const parts = text.split(/(`[^`]+`)/g);

  return parts.map((part, index) => {
    if (part.startsWith('`') && part.endsWith('`') && part.length >= 2) {
      return (
        <code
          key={`inline-${index}`}
          className="rounded bg-gray-100 px-1 py-0.5 font-mono text-[0.92em] text-gray-800"
        >
          {part.slice(1, -1)}
        </code>
      );
    }

    return <span key={`text-${index}`}>{part}</span>;
  });
}

function renderTextBlock(content: string) {
  return content.split(/\n{2,}/).map((paragraph, index) => {
    const lines = paragraph.split('\n');

    if (lines.every((line) => line.trim().startsWith('- ') || line.trim().startsWith('* '))) {
      return (
        <ul key={`list-${index}`} className="mb-2 list-disc space-y-1 pl-5 last:mb-0">
          {lines.map((line, lineIndex) => (
            <li key={`item-${lineIndex}`} className="leading-6">
              {renderInlineText(line.replace(/^\s*[-*]\s*/, ''))}
            </li>
          ))}
        </ul>
      );
    }

    return (
      <p key={`paragraph-${index}`} className="mb-2 whitespace-pre-wrap leading-6 last:mb-0">
        {renderInlineText(paragraph)}
      </p>
    );
  });
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
            <pre key={`code-${index}`} className="my-3 overflow-x-auto rounded-lg bg-gray-900/95 px-3 py-2 text-xs text-gray-100">
              <code>{block.content}</code>
            </pre>
          );
        }

        return (
          <div key={`block-${index}`}>
            {renderTextBlock(block.content)}
          </div>
        );
      })}
    </div>
  );
}
