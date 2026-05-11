import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Button, Input, Space, Typography } from 'antd';

const { Text } = Typography;

type ChatRole = 'user' | 'assistant';

interface ChatMessage {
  id: number;
  role: ChatRole;
  text: string;
}

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

export default function ChatPanel() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const nextIdRef = useRef(1);

  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages.length]);

  const appendMessage = (role: ChatRole, text: string) => {
    setMessages((prev) => [...prev, { id: nextIdRef.current++, role, text }]);
  };

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || sending) return;
    appendMessage('user', trimmed);
    setInput('');
    setSending(true);
    try {
      const res = await fetch('/api/chats/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: trimmed }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: { response: string } = await res.json();
      appendMessage('assistant', data.response);
    } catch {
      appendMessage('assistant', 'Failed to reach chat-service');
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
            Say hi — the chat-service will reply.
          </Text>
        ) : (
          messages.map((m) => (
            <div
              key={m.id}
              data-testid={`chat-message-${m.role}`}
              style={m.role === 'user' ? userBubbleStyle : assistantBubbleStyle}
            >
              {m.text}
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>
      <Space.Compact style={{ width: '100%' }}>
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
