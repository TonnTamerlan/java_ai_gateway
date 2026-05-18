import { useEffect, useRef, useState } from 'react';
import {
  Button,
  Card,
  Input,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
  type UploadFile,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';

const { Dragger } = Upload;
const { Text } = Typography;

type FileStatus = 'PENDING' | 'DONE' | 'FAILED';
type JobStatus = 'PENDING' | 'PARTIAL' | 'DONE' | 'FAILED';

interface JobView {
  job: {
    id: string;
    status: JobStatus;
    fileCount: number;
    createdAt: string;
    updatedAt: string;
  };
  files: Array<{
    correlationId: string;
    status: FileStatus;
    summary: string | null;
    model: string | null;
    errorMessage: string | null;
    instruction: string;
  }>;
}

interface SubmitResponse {
  jobId: string;
  correlationIds: string[];
}

const MAX_FILES = 10;
const POLL_INTERVAL_MS = 1500;

function statusTag(status: FileStatus | JobStatus) {
  switch (status) {
    case 'DONE':
      return <Tag color="green">{status}</Tag>;
    case 'FAILED':
      return <Tag color="red">{status}</Tag>;
    case 'PARTIAL':
      return <Tag color="orange">{status}</Tag>;
    default:
      return <Tag color="blue">{status}</Tag>;
  }
}

export default function FileSummarizationPanel() {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [prompt, setPrompt] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [job, setJob] = useState<JobView | null>(null);
  const pollTimerRef = useRef<number | null>(null);

  useEffect(() => () => clearPoll(), []);

  const clearPoll = () => {
    if (pollTimerRef.current !== null) {
      window.clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  const pollJob = (jobId: string) => {
    const tick = async () => {
      try {
        const res = await fetch(`/api/summarize/${jobId}`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const view = (await res.json()) as JobView;
        setJob(view);
        if (view.job.status === 'DONE' || view.job.status === 'FAILED' || view.job.status === 'PARTIAL') {
          clearPoll();
          setSubmitting(false);
          return;
        }
      } catch (err) {
        // transient network errors: keep polling but log once
        console.warn('poll failed', err);
      }
      pollTimerRef.current = window.setTimeout(tick, POLL_INTERVAL_MS);
    };
    pollTimerRef.current = window.setTimeout(tick, POLL_INTERVAL_MS);
  };

  const handleSubmit = async () => {
    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt) {
      message.error('Enter a prompt for the batch.');
      return;
    }
    if (fileList.length === 0) {
      message.error('Add at least one .txt file.');
      return;
    }
    if (fileList.length > MAX_FILES) {
      message.error(`At most ${MAX_FILES} files are allowed.`);
      return;
    }

    const form = new FormData();
    form.append('prompt', trimmedPrompt);
    for (const f of fileList) {
      const file = f.originFileObj;
      if (!file) {
        message.error(`Cannot read file ${f.name}.`);
        return;
      }
      form.append('files', file, f.name);
    }

    clearPoll();
    setJob(null);
    setSubmitting(true);

    try {
      const res = await fetch('/api/summarize', { method: 'POST', body: form });
      if (!res.ok) {
        const detail = await res.json().catch(() => ({}));
        message.error(detail.error ?? `Submit failed (HTTP ${res.status})`);
        setSubmitting(false);
        return;
      }
      const submit = (await res.json()) as SubmitResponse;
      pollJob(submit.jobId);
    } catch (err) {
      console.error(err);
      message.error('Failed to reach calculation-service.');
      setSubmitting(false);
    }
  };

  const handleReset = () => {
    clearPoll();
    setFileList([]);
    setPrompt('');
    setJob(null);
    setSubmitting(false);
  };

  return (
    <Card style={{ maxWidth: 720, margin: '0 auto', width: '100%' }} title="Batch file summarization">
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Text type="secondary">
          Upload up to {MAX_FILES} <code>.txt</code> files, give one prompt for the batch, and submit.
        </Text>
        <Dragger
          multiple
          accept=".txt,text/plain"
          maxCount={MAX_FILES}
          beforeUpload={() => false}
          fileList={fileList}
          onChange={(info) => setFileList(info.fileList.slice(-MAX_FILES))}
          disabled={submitting}
          data-testid="summarize-dropzone"
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">Drag .txt files here, or click to pick</p>
          <p className="ant-upload-hint">Up to {MAX_FILES} files. Other types are rejected on submit.</p>
        </Dragger>
        <Input.TextArea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          autoSize={{ minRows: 3, maxRows: 6 }}
          placeholder="What should the AI do with each file? e.g. Summarize in two sentences."
          disabled={submitting}
          data-testid="summarize-prompt"
        />
        <Space>
          <Button
            type="primary"
            onClick={() => void handleSubmit()}
            loading={submitting}
            disabled={submitting || fileList.length === 0 || !prompt.trim()}
          >
            Submit batch
          </Button>
          <Button onClick={handleReset} disabled={submitting && job === null}>
            Reset
          </Button>
        </Space>
        {job ? (
          <Card size="small" type="inner" title={
            <Space>
              <Text>Job</Text>
              <Text code>{job.job.id.slice(0, 8)}…</Text>
              {statusTag(job.job.status)}
            </Space>
          }>
            <Table
              size="small"
              rowKey="correlationId"
              pagination={false}
              dataSource={job.files}
              columns={[
                {
                  title: 'Correlation',
                  dataIndex: 'correlationId',
                  render: (id: string) => <Text code>{id.slice(0, 8)}…</Text>,
                },
                {
                  title: 'Status',
                  dataIndex: 'status',
                  render: (s: FileStatus) => statusTag(s),
                },
                {
                  title: 'Result',
                  dataIndex: 'summary',
                  render: (s: string | null, row) =>
                    row.status === 'FAILED'
                      ? <Text type="danger">{row.errorMessage ?? 'failed'}</Text>
                      : s
                        ? <Text>{s}</Text>
                        : <Text type="secondary">…waiting</Text>,
                },
              ]}
            />
          </Card>
        ) : null}
      </Space>
    </Card>
  );
}
