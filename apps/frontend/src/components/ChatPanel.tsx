import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { Button, Input, Select, Space, Typography } from 'antd';

const { Text } = Typography;

type ChatRole = 'user' | 'assistant';

type ModelTier = 'FAST' | 'MEDIUM' | 'SLOW';

interface ChatMessage {
  id: number;
  role: ChatRole;
  text: string;
  errored?: boolean;
}

type SseEvent = { event: string; data: string };

const userBubbleStyle: React.CSSProperties = {
  alignSelf: 'flex-end',
  background: '#1677ff',
  color: '#fff',
  padding: '6px 12px',
  borderRadius: 12,
  maxWidth: '75%',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
};

const assistantBubbleStyle: React.CSSProperties = {
  alignSelf: 'flex-start',
  background: '#f0f0f0',
  color: '#000',
  padding: '6px 12px',
  borderRadius: 12,
  maxWidth: '75%',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
};

const erroredBubbleStyle: React.CSSProperties = {
  ...assistantBubbleStyle,
  background: '#fff1f0',
  color: '#cf1322',
  border: '1px solid #ffa39e',
};

function newConversationId(): string {
  const c = (globalThis as { crypto?: Crypto }).crypto;
  if (c?.randomUUID) return c.randomUUID();
  return `conv-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function ChatPanel() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [model, setModel] = useState<ModelTier>('MEDIUM');
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const nextIdRef = useRef(1);
  const conversationId = useMemo(newConversationId, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages.length]);

  const appendUser = (text: string) => {
    const id = nextIdRef.current++;
    setMessages((prev) => [...prev, { id, role: 'user', text }]);
    return id;
  };

  const startAssistantBubble = (): number => {
    const id = nextIdRef.current++;
    setMessages((prev) => [...prev, { id, role: 'assistant', text: '' }]);
    return id;
  };

  const appendToBubble = (id: number, more: string) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === id ? { ...m, text: m.text + more } : m)),
    );
  };

  const markErrored = (id: number, text: string) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === id ? { ...m, text, errored: true } : m)),
    );
  };

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || sending) return;
    appendUser(trimmed);
    setInput('');
    setSending(true);

    const assistantId = startAssistantBubble();

    try {
      const res = await fetch('/api/chats/messages', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ conversationId, model, message: trimmed }),
      });
      if (!res.ok || !res.body) {
        markErrored(assistantId, `Failed to reach chat-service (HTTP ${res.status})`);
        return;
      }
      await consumeSse(res.body, (evt) => {
        if (evt.event === 'delta') {
          const text = safeParseJson<{ text?: string }>(evt.data)?.text;
          if (text) appendToBubble(assistantId, text);
        } else if (evt.event === 'error') {
          const parsed = safeParseJson<{ message?: string }>(evt.data);
          markErrored(assistantId, parsed?.message ?? 'Upstream error');
        }
        // 'done' is a terminal marker; no UI update needed beyond closing the stream.
      });
    } catch {
      markErrored(assistantId, 'Failed to reach chat-service');
    } finally {
      setSending(false);
    }
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== 'Enter') return;
    if (e.shiftKey || e.metaKey) return;
    e.preventDefault();
    void handleSend();
  };

  return (
    <div
      data-testid="chat-panel"
      style={{ maxWidth: 720, margin: '0 auto', width: '100%' }}
    >
      <div
        data-testid="chat-messages"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          maxHeight: 240,
          overflowY: 'auto',
          padding: '8px 0',
          marginBottom: 8,
        }}
      >
        {messages.length === 0 ? (
          <Text type="secondary" style={{ textAlign: 'center' }}>
            Say hi — pick a model and start chatting.
          </Text>
        ) : (
          messages.map((m) => (
            <div
              key={m.id}
              data-testid={`chat-message-${m.role}`}
              style={
                m.role === 'user'
                  ? userBubbleStyle
                  : m.errored
                    ? erroredBubbleStyle
                    : assistantBubbleStyle
              }
            >
              {m.text}
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>
      <Space.Compact style={{ width: '100%' }}>
        <Select<ModelTier>
          data-testid="chat-model"
          value={model}
          onChange={setModel}
          disabled={sending}
          style={{ width: 110 }}
          options={[
            { value: 'FAST', label: 'FAST' },
            { value: 'MEDIUM', label: 'MEDIUM' },
            { value: 'SLOW', label: 'SLOW' },
          ]}
        />
        <Input.TextArea
          data-testid="chat-input"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          autoSize={{ minRows: 1, maxRows: 4 }}
          placeholder="Type a message — Enter to send, Shift+Enter or Cmd+Enter for newline"
          disabled={sending}
        />
        <Button
          type="primary"
          onClick={() => void handleSend()}
          loading={sending}
          disabled={!input.trim() || sending}
        >
          Send
        </Button>
      </Space.Compact>
    </div>
  );
}

function safeParseJson<T>(raw: string): T | undefined {
  try {
    return JSON.parse(raw) as T;
  } catch {
    return undefined;
  }
}

export async function consumeSse(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // SSE event blocks are separated by a blank line. Handle both LF and CRLF.
    let separatorIndex: number;
    while ((separatorIndex = nextSeparator(buffer)) !== -1) {
      const block = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex).replace(/^(\r?\n){2}/, '');
      const evt = parseSseBlock(block);
      if (evt) onEvent(evt);
    }
  }
  // Flush any trailing block without a closing blank line.
  if (buffer.trim().length > 0) {
    const evt = parseSseBlock(buffer);
    if (evt) onEvent(evt);
  }
}

function nextSeparator(buf: string): number {
  const a = buf.indexOf('\n\n');
  const b = buf.indexOf('\r\n\r\n');
  if (a === -1) return b;
  if (b === -1) return a;
  return Math.min(a, b);
}

function parseSseBlock(block: string): SseEvent | null {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  }
  if (dataLines.length === 0) return null;
  return { event, data: dataLines.join('\n') };
}
