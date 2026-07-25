import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import AgentMessageList from './AgentMessageList';

const message = {
  id: 'save-command',
  role: 'user' as const,
  content: '保存到记忆库：孩子最近做应用题时，先复述题意后列式更稳定。',
  timestamp: '2026-07-19T00:00:00+08:00',
};

function renderMessageList(saveFeedback = {}) {
  return renderToStaticMarkup(
    <AgentMessageList
      messages={[message]}
      isLoadingMessages={false}
      isStreaming={false}
      mode="family"
      targetLabel="家庭成员"
      saveFeedback={saveFeedback}
      onDecideSaveConfirmation={vi.fn()}
    />,
  );
}

describe('AgentMessageList save controls', () => {
  it('does not render the legacy manual save button', () => {
    const markup = renderMessageList();

    expect(markup).not.toContain('智能保存');
  });

  it('keeps explicit-save feedback and confirmation actions visible', () => {
    const markup = renderMessageList({
      [message.id]: {
        status: 'confirmation' as const,
        detail: '请确认保存为家庭记忆：应用题先复述题意',
        confirmationId: 12,
      },
    });

    expect(markup).toContain('请确认保存为家庭记忆');
    expect(markup).toContain('aria-label="确认保存"');
    expect(markup).toContain('aria-label="取消保存"');
  });
});
