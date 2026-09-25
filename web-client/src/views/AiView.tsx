/**
 * Chat-style AI view with two modes:
 *   - Diary entries -> POST /api/ai/parse, response offers "Add all to diary"
 *   - Recipe        -> POST /api/ai/parse-recipe, response offers "Save as recipe"
 *
 * The conversation lives in AiChatProvider (mounted above the router) so it
 * survives navigation, exactly like the cached AiView in the desktop client.
 * Port of AiView.java.
 */

import { useEffect, useRef, useState, type KeyboardEvent } from 'react';

import { api } from '../api/client';
import { friendlyMessage } from '../components/Toast';
import { DiaryBubble } from './ai/DiaryBubble';
import { RecipeBubble } from './ai/RecipeBubble';
import { useAiChat, type AiMode } from './ai/AiChatContext';

export function AiView() {
  const chat = useAiChat();
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Keep the newest bubble in view as the conversation grows.
  useEffect(() => {
    const node = scrollRef.current;
    if (node) node.scrollTop = node.scrollHeight;
  }, [chat.messages]);

  async function send() {
    const trimmed = text.trim();
    if (!trimmed || busy) return;

    chat.append({ role: 'user', text: trimmed });
    chat.append({ role: 'thinking' });
    setText('');
    setBusy(true);

    try {
      if (chat.mode === 'DIARY') {
        const result = await api.parseIngredients(trimmed);
        chat.replaceLast({ role: 'ai-diary', result });
      } else {
        const result = await api.parseRecipe(trimmed);
        chat.replaceLast({ role: 'ai-recipe', result });
      }
    } catch (err) {
      chat.replaceLast({ role: 'ai-error', text: friendlyMessage(err) });
    } finally {
      setBusy(false);
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Ctrl+Enter sends, matching the desktop client's shortcut.
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      void send();
    }
  }

  return (
    <>
      <div className="pad-16" style={{ paddingBottom: 12 }}>
        <div className="row">
          <h1 className="section-title grow">AI Food Parser</h1>
          <button type="button" className="btn" onClick={chat.clear}>
            Clear chat
          </button>
        </div>
        <p className="muted" style={{ marginTop: 4 }}>
          Describe a meal in plain language. Choose "Diary entries" to log it for today, or "Recipe"
          to save a reusable recipe.
        </p>
      </div>

      <div className="chat-scroll" ref={scrollRef}>
        <div className="chat-row">
          <div className="chat-ai">
            Hi! Type a meal and pick a mode. In Diary mode I list foods to log; in Recipe mode I
            draft a reusable recipe you can save with one click.
          </div>
        </div>

        {chat.messages.map((message) => {
          switch (message.role) {
            case 'user':
              return (
                <div className="chat-row right" key={message.id}>
                  <div className="chat-user">{message.text}</div>
                </div>
              );
            case 'thinking':
              return (
                <div className="chat-row" key={message.id}>
                  <div className="chat-ai" style={{ fontStyle: 'italic' }}>
                    thinking…
                  </div>
                </div>
              );
            case 'ai-diary':
              return <DiaryBubble key={message.id} result={message.result} />;
            case 'ai-recipe':
              return <RecipeBubble key={message.id} result={message.result} />;
            case 'ai-error':
              return (
                <div className="chat-row" key={message.id}>
                  <div className="chat-ai">
                    <div className="inline-hint">{message.text}</div>
                  </div>
                </div>
              );
          }
        })}
      </div>

      <div className="chat-input-bar">
        <textarea
          className="grow"
          rows={2}
          placeholder="e.g. 2 scrambled eggs, 1 slice of whole wheat toast with butter, a banana"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          aria-label="Describe your meal"
        />
        <div className="col" style={{ gap: 2, flex: '0 0 auto' }}>
          <label
            htmlFor="ai-mode"
            style={{ fontSize: 10, color: 'var(--ink-muted)' }}
          >
            Mode
          </label>
          <select
            id="ai-mode"
            className="field-inline"
            value={chat.mode}
            onChange={(e) => chat.setMode(e.target.value as AiMode)}
          >
            <option value="DIARY">Diary entries</option>
            <option value="RECIPE">Recipe</option>
          </select>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => void send()}
          disabled={busy || text.trim() === ''}
          title="Ctrl+Enter"
        >
          {busy ? 'Thinking…' : 'Send'}
        </button>
      </div>
    </>
  );
}
