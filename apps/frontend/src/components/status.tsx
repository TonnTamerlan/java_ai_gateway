import { Tag } from 'antd';

export type FileStatus = 'PENDING' | 'DONE' | 'FAILED';
export type JobStatus = 'PENDING' | 'PARTIAL' | 'DONE' | 'FAILED';

export function statusTag(status: FileStatus | JobStatus) {
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
