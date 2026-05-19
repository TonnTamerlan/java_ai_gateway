import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import FilesTable from './FilesTable';

function pageJson(content: ReturnType<typeof row>[] = [], totalElements = content.length) {
  return {
    content,
    totalElements,
    number: 0,
    size: 20,
  };
}

function row(overrides: Partial<ReturnType<typeof baseRow>> = {}) {
  return { ...baseRow(), ...overrides };
}

function baseRow() {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    jobId: '22222222-2222-2222-2222-222222222222',
    correlationId: '33333333-3333-3333-3333-333333333333',
    fileName: 'alpha.txt',
    status: 'DONE' as const,
    instruction: 'Summarize this',
    summary: 'a short summary',
    model: 'gpt-4o-mini',
    promptTokens: 42,
    completionTokens: 17,
    errorMessage: null,
    createdAt: '2026-05-19T09:00:00Z',
    updatedAt: '2026-05-19T09:01:00Z',
  };
}

function mockFetchOk(body: unknown) {
  return vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => body,
  });
}

function calledUrls(fetchMock: ReturnType<typeof vi.fn>): string[] {
  return fetchMock.mock.calls.map((c) => String(c[0]));
}

describe('FilesTable', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('fetches the first page with default sort (createdAt,desc) on mount', async () => {
    const fetchMock = mockFetchOk(pageJson([row()]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('/api/summarize/files?');
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).toContain('sort=createdAt%2Cdesc');
    expect(url).not.toContain('status=');
    expect(url).not.toContain('name=');

    expect(await screen.findByText('alpha.txt')).toBeInTheDocument();
  });

  it('refetches with status filter applied', async () => {
    const fetchMock = mockFetchOk(pageJson([row()]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    const select = screen.getByTestId('files-status-filter');
    // AntD Select: click to open then click the option text
    fireEvent.mouseDown(within(select).getByRole('combobox'));
    fireEvent.click(await screen.findByText('FAILED'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(calledUrls(fetchMock).at(-1)).toContain('status=FAILED');
  });

  it('debounces name search and sends `name=` in the query', async () => {
    const fetchMock = mockFetchOk(pageJson([row()]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    const input = screen.getByPlaceholderText(/Search by file name/i);
    fireEvent.change(input, { target: { value: 'alp' } });

    // The 300ms debounce should eventually fire a refetch with name=alp.
    await waitFor(
      () => {
        expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(2);
        expect(calledUrls(fetchMock).at(-1)).toContain('name=alp');
      },
      { timeout: 2000 },
    );
  });

  it('refetches with new sort when the File name header is clicked', async () => {
    const fetchMock = mockFetchOk(pageJson([row()]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByText('File name'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(calledUrls(fetchMock).at(-1)).toContain('sort=fileName%2Casc');
  });

  it('shows prompt and summary in the expanded row', async () => {
    const fetchMock = mockFetchOk(
      pageJson([row({ instruction: 'My special prompt', summary: 'My final summary' })]),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    expect(await screen.findByText('alpha.txt')).toBeInTheDocument();

    // The Antd expand toggle has aria-label="Expand row"
    const expandToggle = screen.getByRole('button', { name: /expand row/i });
    fireEvent.click(expandToggle);

    expect(await screen.findByText('My special prompt')).toBeInTheDocument();
    expect(screen.getByText('My final summary')).toBeInTheDocument();
  });

  it('renders an info icon per row that opens the stats popover on click', async () => {
    const r = row({ model: 'gpt-4o-mini', promptTokens: 11, completionTokens: 7 });
    const fetchMock = mockFetchOk(pageJson([r]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    const icon = await screen.findByTestId(`stats-icon-${r.id}`);
    expect(icon).toBeInTheDocument();

    // Popover trigger is set to ['hover', 'click']; click is more reliable in jsdom.
    fireEvent.click(icon);
    expect(await screen.findByText(/Model:/)).toBeInTheDocument();
    expect(screen.getByText('gpt-4o-mini')).toBeInTheDocument();
    expect(screen.getByText('11')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });
});
