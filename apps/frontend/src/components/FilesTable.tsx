import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Input,
  Popover,
  Select,
  Space,
  Table,
  Typography,
  message,
  type TablePaginationConfig,
} from 'antd';
import type { ColumnsType, SorterResult } from 'antd/es/table/interface';
import { InfoCircleOutlined, ReloadOutlined } from '@ant-design/icons';
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

export default function FilesTable() {
  const [data, setData] = useState<FileRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [sortField, setSortField] = useState<SortField>('createdAt');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');
  const [statusFilter, setStatusFilter] = useState<'ALL' | FileStatus>('ALL');
  const [nameInput, setNameInput] = useState('');
  const [nameQuery, setNameQuery] = useState('');
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
      setData(Array.isArray(body.content) ? body.content : []);
      setTotal(typeof body.totalElements === 'number' ? body.totalElements : 0);
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
      {
        title: '',
        key: 'stats',
        width: 48,
        align: 'center',
        render: (_: unknown, row: FileRow) => (
          <Popover
            content={<StatsPopoverContent row={row} />}
            title="Run stats"
            placement="bottomLeft"
            mouseEnterDelay={0.2}
            trigger={['hover', 'click']}
          >
            <InfoCircleOutlined
              data-testid={`stats-icon-${row.id}`}
              style={{ cursor: 'pointer', color: '#888' }}
            />
          </Popover>
        ),
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
        </Space>
        <Table<FileRow>
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={data}
          columns={columns}
          locale={{ emptyText: <Empty description="No files yet" /> }}
          onChange={handleTableChange}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
          }}
          expandable={{
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
      </Space>
    </Card>
  );
}

function toAntdOrder(order: SortOrder): 'ascend' | 'descend' {
  return order === 'asc' ? 'ascend' : 'descend';
}
