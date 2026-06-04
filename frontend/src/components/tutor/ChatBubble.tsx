'use client';

import { cn } from '@/lib/utils';
import type { ChatMessage } from '@/types';
import MathRenderer from './MathRenderer';

interface ChatBubbleProps {
  message: ChatMessage;
  isStreaming?: boolean;
}

export default function ChatBubble({ message, isStreaming }: ChatBubbleProps) {
  const isUser = message.role === 'user';

  return (
    <div
      className={cn(
        'flex gap-3 animate-slide-up',
        isUser ? 'justify-end' : 'justify-start',
      )}
    >
      {/* AI头像 */}
      {!isUser && (
        <div className="w-8 h-8 bg-blue-600 text-white rounded-full flex items-center justify-center text-sm font-medium shrink-0">
          智
        </div>
      )}

      {/* 气泡 */}
      <div
        className={cn(
          'max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed',
          isUser
            ? 'bg-blue-600 text-white rounded-br-md'
            : 'bg-white border border-gray-200 text-gray-900 rounded-bl-md',
          isStreaming && 'typing-cursor',
        )}
      >
        <div className="whitespace-pre-wrap break-words">
          {message.content ? <MathRenderer content={message.content} /> : (isStreaming ? '' : '...')}
        </div>
      </div>

      {/* 用户头像 */}
      {isUser && (
        <div className="w-8 h-8 bg-gray-300 text-gray-600 rounded-full flex items-center justify-center text-sm font-medium shrink-0">
          U
        </div>
      )}
    </div>
  );
}
