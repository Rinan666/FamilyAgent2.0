export type PersistSessionMessages<T> = (messages: T[]) => Promise<void>;

type EnqueuePersistMessagesOptions<T> = {
  queue: Promise<void>;
  messages: T[];
  persist?: PersistSessionMessages<T>;
};

type EnqueuePersistMessagesResult = {
  task: Promise<void>;
  nextQueue: Promise<void>;
};

export function enqueuePersistMessages<T>({
  queue,
  messages,
  persist,
}: EnqueuePersistMessagesOptions<T>): EnqueuePersistMessagesResult {
  if (!messages.length || !persist) {
    return {
      task: Promise.resolve(),
      nextQueue: queue,
    };
  }

  const task = queue
    .catch(() => undefined)
    .then(() => persist(messages));

  return {
    task,
    nextQueue: task.catch(() => undefined),
  };
}
