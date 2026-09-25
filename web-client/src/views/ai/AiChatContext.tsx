/**
 * Chat history for the AI tab.
 *
 * The desktop client caches the AiView instance so the conversation survives
 * tab switches. React Router unmounts routes, so the history is lifted into
 * this provider (mounted above <Routes>) to get the same behaviour. It is
 * cleared on sign-out because the whole shell remounts.
 */

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { ParseResult, ParsedRecipe } from '../../api/types';

export type ChatMessage =
  | { id: number; role: 'user'; text: string }
  | { id: number; role: 'thinking' }
  | { id: number; role: 'ai-diary'; result: ParseResult }
  | { id: number; role: 'ai-recipe'; result: ParsedRecipe }
  | { id: number; role: 'ai-error'; text: string };

/**
 * A plain `Omit` over a union collapses it to the members' shared keys, which
 * would erase `text` and `result`. Distributing keeps each variant intact.
 */
type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never;

export type NewChatMessage = DistributiveOmit<ChatMessage, 'id'>;

export type AiMode = 'DIARY' | 'RECIPE';

interface AiChatState {
  messages: ChatMessage[];
  mode: AiMode;
  setMode: (mode: AiMode) => void;
  append: (message: NewChatMessage) => void;
  /** Swap the trailing "thinking…" bubble for the real answer. */
  replaceLast: (message: NewChatMessage) => void;
  clear: () => void;
}

const AiChatContext = createContext<AiChatState | null>(null);

export function AiChatProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [mode, setMode] = useState<AiMode>('DIARY');
  // A ref, not state: ids are only ever consumed inside the updater, so they
  // must not be subject to StrictMode's double-invocation of state setters.
  const nextId = useRef(1);

  const append = useCallback((message: NewChatMessage) => {
    setMessages((current) => [...current, { ...message, id: nextId.current++ } as ChatMessage]);
  }, []);

  const replaceLast = useCallback((message: NewChatMessage) => {
    setMessages((current) => {
      if (current.length === 0) return current;
      const last = current[current.length - 1];
      return [...current.slice(0, -1), { ...message, id: last.id } as ChatMessage];
    });
  }, []);

  const clear = useCallback(() => setMessages([]), []);

  const value = useMemo<AiChatState>(
    () => ({ messages, mode, setMode, append, replaceLast, clear }),
    [messages, mode, append, replaceLast, clear],
  );

  return <AiChatContext.Provider value={value}>{children}</AiChatContext.Provider>;
}

export function useAiChat(): AiChatState {
  const ctx = useContext(AiChatContext);
  if (!ctx) throw new Error('useAiChat must be used inside <AiChatProvider>');
  return ctx;
}
