import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import App from './App';

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }),
    );
  });

  it('renders the landing card', async () => {
    render(<App />);
    expect(await screen.findByText('Goose is alive')).toBeInTheDocument();
    expect(screen.getByText('Typed Goose')).toBeInTheDocument();
  });

  it('shows API status tag', () => {
    render(<App />);
    expect(screen.getByTestId('api-status')).toBeInTheDocument();
  });
});
