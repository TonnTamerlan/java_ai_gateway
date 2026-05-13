import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ChatPanel, { consumeSse } from './ChatPanel';

function sseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const c of chunks) controller.enqueue(encoder.encode(c));
      controller.close();
    },
  });
  return new Response(stream, {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream' },
  });
}

describe('ChatPanel', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders empty state with disabled send and a model dropdown', () => {
    vi.stubGlobal('fetch', vi.fn());
    render(<ChatPanel />);
    expect(screen.getByText(/Say hi/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send/i })).toBeDisabled();
    expect(screen.getByTestId('chat-model')).toBeInTheDocument();
  });

  it('streams deltas into the assistant bubble and sends conversationId+model in the body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      sseResponse([
        'event:delta\ndata:{"type":"delta","text":"Hello"}\n\n',
        'event:delta\ndata:{"type":"delta","text":" world"}\n\n',
        'event:done\ndata:{"type":"done","usage":{"promptTokens":1,"completionTokens":2}}\n\n',
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<ChatPanel />);
    const textarea = screen.getByTestId('chat-input') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'hi' } });
    fireEvent.keyDown(textarea, { key: 'Enter' });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/chats/messages');
    expect(init.method).toBe('POST');
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    });
    const body = JSON.parse(init.body);
    expect(body.message).toBe('hi');
    expect(body.model).toBe('MEDIUM');
    expect(typeof body.conversationId).toBe('string');
    expect(body.conversationId.length).toBeGreaterThan(0);

    await waitFor(() =>
      expect(screen.getByTestId('chat-message-assistant')).toHaveTextContent('Hello world'),
    );
  });

  it('shows an error bubble when the SSE error event arrives', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      sseResponse([
        'event:error\ndata:{"type":"error","code":"x","message":"upstream blew up"}\n\n',
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<ChatPanel />);
    const textarea = screen.getByTestId('chat-input') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'ping' } });
    fireEvent.keyDown(textarea, { key: 'Enter' });

    await waitFor(() =>
      expect(screen.getByTestId('chat-message-assistant')).toHaveTextContent(
        /upstream blew up/i,
      ),
    );
  });

  it('shows a fallback when fetch rejects', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('boom')));
    render(<ChatPanel />);
    const textarea = screen.getByTestId('chat-input') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'ping' } });
    fireEvent.keyDown(textarea, { key: 'Enter' });

    await waitFor(() =>
      expect(screen.getByTestId('chat-message-assistant')).toHaveTextContent(
        /Failed to reach chat-service/i,
      ),
    );
  });

  it('does not send on Shift+Enter', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    render(<ChatPanel />);
    const textarea = screen.getByTestId('chat-input') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'line1' } });
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });

    expect(fetchMock).not.toHaveBeenCalled();
    expect(textarea.value).toBe('line1');
  });
});

describe('consumeSse', () => {
  it('parses events delivered in a single chunk', async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(c) {
        c.enqueue(
          encoder.encode(
            'event:delta\ndata:{"text":"a"}\n\nevent:done\ndata:{}\n\n',
          ),
        );
        c.close();
      },
    });
    const events: { event: string; data: string }[] = [];
    await consumeSse(stream, (e) => events.push(e));
    expect(events).toEqual([
      { event: 'delta', data: '{"text":"a"}' },
      { event: 'done', data: '{}' },
    ]);
  });

  it('parses events split across multiple chunks', async () => {
    const encoder = new TextEncoder();
    const parts = [
      'event:delta\nda',
      'ta:{"text":"hel',
      'lo"}\n\nevent:de',
      'lta\ndata:{"text":"world"}\n\n',
    ];
    const stream = new ReadableStream<Uint8Array>({
      start(c) {
        for (const p of parts) c.enqueue(encoder.encode(p));
        c.close();
      },
    });
    const events: { event: string; data: string }[] = [];
    await consumeSse(stream, (e) => events.push(e));
    expect(events).toEqual([
      { event: 'delta', data: '{"text":"hello"}' },
      { event: 'delta', data: '{"text":"world"}' },
    ]);
  });

  it('tolerates CRLF separators', async () => {
    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      start(c) {
        c.enqueue(encoder.encode('event:delta\r\ndata:{"text":"hi"}\r\n\r\n'));
        c.close();
      },
    });
    const events: { event: string; data: string }[] = [];
    await consumeSse(stream, (e) => events.push(e));
    expect(events).toEqual([{ event: 'delta', data: '{"text":"hi"}' }]);
  });
});
