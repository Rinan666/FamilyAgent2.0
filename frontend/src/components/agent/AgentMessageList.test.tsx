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
          memory_library: 'FAMILY' as const,
          memory_type: 'OBSERVATION' as const,
          content: '孩子最近做应用题时，先复述题意后列式更稳定。',
          title: '应用题先复述题意',
          summary: '先复述题意后列式更稳定。',
          visibility: 'CARE_VISIBLE',
          selected_family_ids: [],
          importance: 3,
          tags: ['应用题'],
          reason: '按用户要求整理为草稿。',
          confirmation_message: '草稿已准备。',
        },
      },
    });

    expect(markup).toContain('保存草稿');
    expect(markup).toContain('应用题先复述题意');
    expect(markup).toContain('确认保存');
    expect(markup).toContain('取消');
  });

  it('shows the real save result beside the command message', () => {
    const successMarkup = renderMessageList({
      [message.id]: {
        status: 'saved' as const,
        detail: '个人记忆 · 电器维修笔记 · 仅自己可见',
        href: '/dashboard/memory-library?library=personal',
      },
    });
    const errorMarkup = renderMessageList({
      [message.id]: {
        status: 'error' as const,
        detail: '保存失败，请稍后重试。',
      },
    });

    expect(successMarkup).toContain('个人记忆');
    expect(successMarkup).toContain('打开');
    expect(errorMarkup).toContain('保存失败，请稍后重试。');
  });
});
