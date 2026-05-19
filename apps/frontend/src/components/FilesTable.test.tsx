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

  it('shows prompt and summary when the row body is clicked (expandRowByClick)', async () => {
    const fetchMock = mockFetchOk(
      pageJson([row({ instruction: 'My special prompt', summary: 'My final summary' })]),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    expect(await screen.findByText('alpha.txt')).toBeInTheDocument();

    // expandRowByClick — clicking the file-name cell expands the row.
    fireEvent.click(screen.getByText('alpha.txt'));

    expect(await screen.findByText('My special prompt')).toBeInTheDocument();
    expect(screen.getByText('My final summary')).toBeInTheDocument();
  });

  it('surfaces the stats popover when the row is hovered', async () => {
    const r = row({ model: 'gpt-4o-mini', promptTokens: 11, completionTokens: 7 });
    const fetchMock = mockFetchOk(pageJson([r]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    const rowEl = await screen.findByTestId(`files-row-${r.id}`);

    fireEvent.mouseEnter(rowEl);

    await waitFor(
      () => expect(screen.getByText(/Run stats/i)).toBeInTheDocument(),
      { timeout: 1000 },
    );
    expect(screen.getByText('gpt-4o-mini')).toBeInTheDocument();
    expect(screen.getByText('11')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('disables the delete button until rows are selected', async () => {
    const r = row();
    const fetchMock = mockFetchOk(pageJson([r]));
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    const button = await screen.findByTestId('files-delete-button');
    expect(button).toBeDisabled();

    // The first checkbox after the table header is the "select all" toggle;
    // the second one is for the only row.
    const checkboxes = await screen.findAllByRole('checkbox');
    fireEvent.click(checkboxes[checkboxes.length - 1]);

    await waitFor(() => expect(screen.getByTestId('files-delete-button')).not.toBeDisabled());
    expect(screen.getByTestId('files-delete-button').textContent).toContain('Delete (1)');
  });

  it('issues DELETE /api/summarize/files and refetches after confirm', async () => {
    const r = row();
    const list = pageJson([r]);
    const fetchMock = vi.fn().mockImplementation(async (input: RequestInfo, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === 'DELETE') {
        return { ok: true, status: 200, json: async () => ({ deleted: 1 }) };
      }
      // Default = GET list
      void url;
      return { ok: true, status: 200, json: async () => list };
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<FilesTable />);
    await screen.findByText('alpha.txt');
    const initialGetCount = fetchMock.mock.calls.length;

    const checkboxes = await screen.findAllByRole('checkbox');
    fireEvent.click(checkboxes[checkboxes.length - 1]);
    await waitFor(() => expect(screen.getByTestId('files-delete-button')).not.toBeDisabled());

    fireEvent.click(screen.getByTestId('files-delete-button'));
    // Popconfirm OK button
    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    await waitFor(() => {
      const deleteCalls = fetchMock.mock.calls.filter((c) => c[1]?.method === 'DELETE');
      expect(deleteCalls.length).toBe(1);
    });

    const deleteCall = fetchMock.mock.calls.find((c) => c[1]?.method === 'DELETE');
    expect(String(deleteCall?.[0])).toContain('/api/summarize/files');
    expect(JSON.parse(String(deleteCall?.[1]?.body))).toEqual({ ids: [r.id] });
    expect((deleteCall?.[1] as RequestInit | undefined)?.headers).toEqual({
      'Content-Type': 'application/json',
    });

    // Refetch happened after DELETE: at least one more GET call than before delete.
    await waitFor(() => {
      const getCalls = fetchMock.mock.calls.filter((c) => !c[1] || c[1].method !== 'DELETE');
      expect(getCalls.length).toBeGreaterThan(initialGetCount);
    });

    // Selection cleared — button label drops back to plain "Delete".
    await waitFor(() => {
      expect(screen.getByTestId('files-delete-button').textContent).toBe('Delete');
    });
  });
});
