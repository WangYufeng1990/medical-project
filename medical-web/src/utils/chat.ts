/** One id per send attempt: the server treats a retry with the same id as the same message. */
export const newClientMessageId = () =>
  globalThis.crypto?.randomUUID?.() ?? `c-${Date.now()}-${Math.random().toString(16).slice(2)}`
