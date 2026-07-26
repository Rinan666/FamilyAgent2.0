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
      families={[]}
      activeFamilyId={null}
      onConfirmSaveDraft={vi.fn()}
      onCancelSaveDraft={vi.fn()}
    />,
  );
}

describe('AgentMessageList save controls', () => {
  it('does not render the legacy manual save button', () => {
    const markup = renderMessageList();

    expect(markup).not.toContain('智能保存');
  });

  it('shows an editable draft before any save action', () => {
    const markup = renderMessageList({
      [message.id]: {
        status: 'draft' as const,
        detail: '家庭记忆草稿已准备，请修改或确认后保存。',
        draft: {
          should_save: true,
          tool: 'FAMILY_MEMORY' as const,
          content: '孩子最近做应用题时，先复述题意后列式更稳定。',
          title: '应用题先复述题意',
          summary: '先复述题意后列式更稳定。',
          visibility: 'CARE_VISIBLE',
          entry_type: 'DAILY',
          memory_type: 'ELDER_ADVICE',
          scope: 'CARE_VISIBLE',
          category: 'OTHER',
          severity: 2,
          importance: 3,
          tags: ['应用题'],
          reason: '按用户要求整理为草稿。',
          confirmation_message: '草稿已准备。',
        },
      },
    });

    expect(markup).toContain('保存草稿');
    expect(markup).toContain('尚未保存，可直接修改后确认');
    expect(markup).toContain('应用题先复述题意');
    expect(markup).toContain('确认保存');
    expect(markup).toContain('取消');
  });
});
