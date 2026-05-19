import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type HTMLAttributes,
} from 'react';
import {
  Button,
  Card,
  Empty,
  Input,
  Popconfirm,
  Popover,
  Select,
  Space,
  Table,
  Typography,
  message,
  type TablePaginationConfig,
} from 'antd';
import type { ColumnsType, SorterResult } from 'antd/es/table/interface';
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { statusTag, type FileStatus } from './status';

const { Text, Paragraph } = Typography;

interface FileRow {
  id: string;
  jobId: string;
  correlationId: string;
  fileName: string;
  status: FileStatus;
  instruction: string;
  summary: string | null;
  model: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

interface PageResponse {
  content: FileRow[];
  totalElements: number;
  number: number;
  size: number;
}

type SortField = 'fileName' | 'status' | 'createdAt' | 'updatedAt';
type SortOrder = 'asc' | 'desc';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'PENDING', label: 'PENDING' },
  { value: 'DONE', label: 'DONE' },
  { value: 'FAILED', label: 'FAILED' },
];

function formatInstant(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
}

function StatsPopoverContent({ row }: { row: FileRow }) {
  return (
    <Space direction="vertical" size={2} style={{ minWidth: 180 }}>
      <Text>
        <Text strong>Model:</Text>{' '}
        {row.model ?? <Text type="secondary">—</Text>}
      </Text>
      <Text>
        <Text strong>Prompt tokens:</Text>{' '}
        {row.promptTokens ?? <Text type="secondary">—</Text>}
      </Text>
      <Text>
        <Text strong>Completion tokens:</Text>{' '}
        {row.completionTokens ?? <Text type="secondary">—</Text>}
      </Text>
    </Space>
  );
}

const RowsContext = createContext<FileRow[]>([]);

type HoverRowProps = HTMLAttributes<HTMLTableRowElement> & {
  'data-row-key'?: string;
};

function HoverableRow(props: HoverRowProps) {
  const rows = useContext(RowsContext);
  const key = props['data-row-key'];
  const record = key ? rows.find((r) => r.id === key) : undefined;
  if (!record) return <tr {...props} />;
  return (
    <Popover
      content={<StatsPopoverContent row={record} />}
      title="Run stats"
      placement="topLeft"
      mouseEnterDelay={0.1}
      mouseLeaveDelay={0.1}
      trigger="hover"
    >
      <tr {...props} data-testid={`files-row-${record.id}`} />
    </Popover>
  );
}

export default function FilesTable() {
  const [data, setData] = useState<FileRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [page, setPage] = useState(0);
  const [sortField, setSortField] = useState<SortField>('createdAt');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
  const [statusFilter, setStatusFilter] = useState<'ALL' | FileStatus>('ALL');
  const [nameInput, setNameInput] = useState('');
  const [nameQuery, setNameQuery] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const reqIdRef = useRef(0);

  useEffect(() => {
    const handle = window.setTimeout(() => setNameQuery(nameInput.trim()), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
  }, [nameInput]);

  // Reset to first page when filters or search change.
  useEffect(() => {
    setPage(0);
  }, [statusFilter, nameQuery, sortField, sortOrder]);

  const fetchPage = useCallback(async () => {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(PAGE_SIZE));
    params.set('sort', `${sortField},${sortOrder}`);
    if (statusFilter !== 'ALL') params.set('status', statusFilter);
    if (nameQuery) params.set('name', nameQuery);

    const reqId = ++reqIdRef.current;
    setLoading(true);
    try {
      const res = await fetch(`/api/summarize/files?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = (await res.json()) as Partial<PageResponse>;
      if (reqId !== reqIdRef.current) return; // a newer request superseded us
      const content = Array.isArray(body.content) ? body.content : [];
      const totalCount = typeof body.totalElements === 'number' ? body.totalElements : 0;
      setData(content);
      setTotal(totalCount);
      // If we landed on an empty page past the first, step back so the user
      // doesn't stare at an empty table after deleting the last row.
      if (content.length === 0 && page > 0 && totalCount > 0) {
        setPage((p) => Math.max(0, p - 1));
      }
    } catch (err) {
      console.error('files fetch failed', err);
      if (reqId === reqIdRef.current) {
        message.error('Failed to load files.');
        setData([]);
        setTotal(0);
      }
    } finally {
      if (reqId === reqIdRef.current) setLoading(false);
    }
  }, [page, sortField, sortOrder, statusFilter, nameQuery]);

  useEffect(() => {
    void fetchPage();
  }, [fetchPage]);

  const handleDelete = useCallback(async () => {
    if (selectedKeys.length === 0) return;
    setDeleting(true);
    try {
      const res = await fetch('/api/summarize/files', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ids: selectedKeys }),
      });
      if (!res.ok) {
        message.error('Delete failed');
        await fetchPage();
        return;
      }
      const { deleted } = (await res.json()) as { deleted: number };
      message.success(`Deleted ${deleted} file(s)`);
      setSelectedKeys([]);
      await fetchPage();
    } catch (err) {
      console.error('delete failed', err);
      message.error('Delete failed');
    } finally {
      setDeleting(false);
    }
  }, [selectedKeys, fetchPage]);

  const columns = useMemo<ColumnsType<FileRow>>(
    () => [
      {
        title: 'ID',
        dataIndex: 'id',
        key: 'id',
        width: 140,
        render: (id: string) => <Text code>{id.slice(0, 8)}…</Text>,
      },
      {
        title: 'File name',
        dataIndex: 'fileName',
        key: 'fileName',
        sorter: true,
        sortOrder: sortField === 'fileName' ? toAntdOrder(sortOrder) : null,
        render: (name: string) =>
          name ? <Text>{name}</Text> : <Text type="secondary">(unknown)</Text>,
      },
      {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        width: 120,
        sorter: true,
        sortOrder: sortField === 'status' ? toAntdOrder(sortOrder) : null,
        render: (s: FileStatus) => statusTag(s),
      },
      {
        title: 'Created at',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 200,
        sorter: true,
        sortOrder: sortField === 'createdAt' ? toAntdOrder(sortOrder) : null,
        render: (iso: string) => <Text>{formatInstant(iso)}</Text>,
      },
      {
        title: 'Updated at',
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: 200,
        sorter: true,
        sortOrder: sortField === 'updatedAt' ? toAntdOrder(sortOrder) : null,
        render: (iso: string) => <Text>{formatInstant(iso)}</Text>,
      },
    ],
    [sortField, sortOrder],
  );

  const handleTableChange = (
    pagination: TablePaginationConfig,
    _filters: unknown,
    sorter: SorterResult<FileRow> | SorterResult<FileRow>[],
  ) => {
    const next = Array.isArray(sorter) ? sorter[0] : sorter;
    if (next && next.field && next.order) {
      const field = String(next.field) as SortField;
      const order: SortOrder = next.order === 'ascend' ? 'asc' : 'desc';
      if (field !== sortField || order !== sortOrder) {
        setSortField(field);
        setSortOrder(order);
      }
    } else if (next && !next.order && sortField !== 'createdAt') {
      // user cleared the sort
      setSortField('createdAt');
      setSortOrder('desc');
    }

    if (pagination.current && pagination.current - 1 !== page) {
      setPage(pagination.current - 1);
    }
  };

  return (
    <Card
      style={{ maxWidth: 1080, margin: '0 auto', width: '100%' }}
      title="All summarization files"
      extra={
        <Button
          icon={<ReloadOutlined />}
          onClick={() => void fetchPage()}
          loading={loading}
          aria-label="refresh files"
        >
          Refresh
        </Button>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space wrap>
          <Input.Search
            placeholder="Search by file name…"
            allowClear
            value={nameInput}
            onChange={(e) => setNameInput(e.target.value)}
            onSearch={(v) => setNameQuery(v.trim())}
            style={{ width: 260 }}
            data-testid="files-search"
          />
          <Select
            value={statusFilter}
            onChange={(v) => setStatusFilter(v)}
            options={STATUS_OPTIONS}
            style={{ width: 160 }}
            data-testid="files-status-filter"
          />
          <Popconfirm
            title={`Delete ${selectedKeys.length} file(s)?`}
            description="This soft-deletes the rows. They won't appear in the list anymore."
            okText="Delete"
            okButtonProps={{ danger: true, loading: deleting }}
            onConfirm={handleDelete}
            disabled={selectedKeys.length === 0}
          >
            <Button
              danger
              icon={<DeleteOutlined />}
              disabled={selectedKeys.length === 0}
              loading={deleting}
              data-testid="files-delete-button"
            >
              Delete{selectedKeys.length > 0 ? ` (${selectedKeys.length})` : ''}
            </Button>
          </Popconfirm>
        </Space>
        <RowsContext.Provider value={data}>
          <Table<FileRow>
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={data}
            columns={columns}
            components={{ body: { row: HoverableRow } }}
            rowSelection={{
              type: 'checkbox',
              selectedRowKeys: selectedKeys,
              onChange: setSelectedKeys,
              preserveSelectedRowKeys: false,
            }}
            locale={{ emptyText: <Empty description="No files yet" /> }}
            onChange={handleTableChange}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total,
              showSizeChanger: false,
            }}
            expandable={{
              expandRowByClick: true,
              expandedRowRender: (row) => (
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <div>
                    <Text strong>Prompt</Text>
                    <Paragraph
                      style={{
                        marginTop: 4,
                        whiteSpace: 'pre-wrap',
                        background: '#fafafa',
                        padding: 8,
                        borderRadius: 4,
                      }}
                    >
                      {row.instruction}
                    </Paragraph>
                  </div>
                  <div>
                    <Text strong>Summary</Text>
                    <Paragraph
                      style={{
                        marginTop: 4,
                        whiteSpace: 'pre-wrap',
                        background: '#fafafa',
                        padding: 8,
                        borderRadius: 4,
                      }}
                    >
                      {row.status === 'FAILED' ? (
                        <Text type="danger">{row.errorMessage ?? 'failed'}</Text>
                      ) : row.summary ? (
                        row.summary
                      ) : (
                        <Text type="secondary">…not ready</Text>
                      )}
                    </Paragraph>
                  </div>
                </Space>
              ),
            }}
          />
        </RowsContext.Provider>
      </Space>
    </Card>
  );
}

function toAntdOrder(order: SortOrder): 'ascend' | 'descend' {
  return order === 'asc' ? 'ascend' : 'descend';
}
