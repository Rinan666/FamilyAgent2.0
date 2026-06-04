'use client';

import { useEffect, useRef } from 'react';
import katex from 'katex';
import 'katex/dist/katex.min.css';

interface MathRendererProps {
  /** Text that may contain $...$ (inline) or $$...$$ (block) math */
  content: string;
  className?: string;
}

/**
 * MathRenderer — renders text with KaTeX for math expressions.
 *
 * Supported delimiters:
 * - $...$ for inline math
 * - $$...$$ for display (block) math
 */
export default function MathRenderer({ content, className = '' }: MathRendererProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current || !content) return;

    // Replace block math first ($$...$$), then inline ($...$)
    let html = content
      // Escape HTML entities in non-math text first
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    // Block math: $$...$$
    html = html.replace(/\$\$([\s\S]*?)\$\$/g, (_match, formula: string) => {
      try {
        return katex.renderToString(formula.trim(), {
          displayMode: true,
          throwOnError: false,
        });
      } catch {
        return `<pre>${formula.trim()}</pre>`;
      }
    });

    // Inline math: $...$ (but not $$)
    html = html.replace(/(?<!\$)\$(?!\$)([\s\S]*?)(?<!\$)\$(?!\$)/g, (_match, formula: string) => {
      try {
        return katex.renderToString(formula.trim(), {
          displayMode: false,
          throwOnError: false,
        });
      } catch {
        return `$${formula.trim()}$`;
      }
    });

    containerRef.current.innerHTML = html;
  }, [content]);

  return <div ref={containerRef} className={`math-content ${className}`} />;
}
