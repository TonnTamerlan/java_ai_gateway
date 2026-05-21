import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import App from './App';

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      }),
    );
  });

  it('renders header and the three main panels', { timeout: 20_000 }, () => {
    render(<App />);
    expect(screen.getByText('Typed Goose')).toBeInTheDocument();
    expect(screen.getByText('Send')).toBeInTheDocument();
    expect(screen.getByText('All summarization files')).toBeInTheDocument();
    expect(screen.getByText('Batch file summarization')).toBeInTheDocument();
  });

  it('does not render the alive checker', { timeout: 20_000 }, () => {
    render(<App />);
    expect(screen.queryByText('Goose is alive')).not.toBeInTheDocument();
    expect(screen.queryByTestId('api-status')).not.toBeInTheDocument();
  });
});
