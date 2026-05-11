import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ChatPanel from './ChatPanel';

const okJsonResponse = (body: unknown) =>
  ({ ok: true, json: async () => body }) as unknown as Response;

describe('ChatPanel', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the empty state with disabled send', () => {
    vi.stubGlobal('fetch', vi.fn());
    render(<ChatPanel />);
    expect(screen.getByText(/Say hi/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send/i })).toBeDisabled();
  });

  it('sends the message on Enter and renders the reply', async () => {
    const fetchMock = vi.fn().mockResolvedValue(okJsonResponse({ response: 'Message got' }));
    vi.stubGlobal('fetch', fetchMock);

    render(<ChatPanel />);
    const textarea = screen.getByTestId('chat-input') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'hello' } });
    fireEvent.keyDown(textarea, { key: 'Enter' });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/chats/messages');
    expect(init.method).toBe('POST');
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(JSON.parse(init.body)).toEqual({ message: 'hello' });

    expect(screen.getByTestId('chat-message-user')).toHaveTextContent('hello');
    await waitFor(() =>
      expect(screen.getByTestId('chat-message-assistant')).toHaveTextContent('Message got'),
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

  it('does not send on Cmd+Enter (metaKey)', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    render(<ChatPanel />);
    const textarea = screen.getByTestId('chat-input') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'mac line' } });
    fireEvent.keyDown(textarea, { key: 'Enter', metaKey: true });

    expect(fetchMock).not.toHaveBeenCalled();
    expect(textarea.value).toBe('mac line');
  });

  it('shows a failure message when fetch rejects', async () => {
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
});
