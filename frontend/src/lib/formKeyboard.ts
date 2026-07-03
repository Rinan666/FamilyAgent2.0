import type { KeyboardEvent } from 'react';

export function submitFormOnEnter(event: KeyboardEvent<HTMLFormElement>) {
  if (event.key !== 'Enter' || event.nativeEvent.isComposing) return;
  if (event.shiftKey || event.altKey || event.ctrlKey || event.metaKey) return;
  if (!(event.target instanceof HTMLInputElement)) return;

  event.preventDefault();
  event.currentTarget.requestSubmit();
}

export function isPlainEnter(event: KeyboardEvent) {
  return (
    event.key === 'Enter'
    && !event.nativeEvent.isComposing
    && !event.shiftKey
    && !event.altKey
    && !event.ctrlKey
    && !event.metaKey
  );
}
