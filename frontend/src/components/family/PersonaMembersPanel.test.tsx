import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import PersonaMembersPanel from './PersonaMembersPanel';

describe('PersonaMembersPanel graph mode', () => {
  it('shows the add entry for an owner when there are no persona members', () => {
    const markup = renderToStaticMarkup(
      <PersonaMembersPanel familyId={6} isOwner graphMode />,
    );

    expect(markup).toContain('新增精神成员');
  });

  it('keeps the empty state read-only for non-owners', () => {
    const markup = renderToStaticMarkup(
      <PersonaMembersPanel familyId={6} graphMode />,
    );

    expect(markup).toContain('还没有精神成员');
    expect(markup).not.toContain('新增精神成员');
  });
});
