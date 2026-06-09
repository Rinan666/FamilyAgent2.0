import type { ChatMessage, ChatSessionMessagePage } from '@/types';
import { sessionMessageItemToChatMessage } from '@/lib/api';

export type FetchSessionMessagesPage = (
  sessionId: number,
  beforeSeq?: number,
  limit?: number,
) => Promise<ChatSessionMessagePage>;

export async function loadSessionMessagesChronologically(
  fetchPage: FetchSessionMessagesPage,
  sessionId: number,
  limit = 40,
): Promise<ChatMessage[]> {
  let beforeSeq: number | undefined;
  let hasMore = true;
  let messages: ChatMessage[] = [];

  while (hasMore) {
    const page = await fetchPage(sessionId, beforeSeq, limit);
    const pageMessages = (page.items || []).map(sessionMessageItemToChatMessage);
    if (pageMessages.length > 0) {
      messages = [...pageMessages, ...messages];
    } else if (page.hasMore) {
      break;
    }

    if (!page.hasMore || page.nextBeforeSeq == null || page.nextBeforeSeq === beforeSeq) {
      break;
    }

    beforeSeq = page.nextBeforeSeq;
    hasMore = page.hasMore;
  }

  return messages;
}
