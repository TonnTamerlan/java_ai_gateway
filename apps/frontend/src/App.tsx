import { useEffect, useState } from 'react';
import { Card, Layout, Space, Tag, Typography } from 'antd';
import ChatPanel from './components/ChatPanel';

const { Header, Content, Footer } = Layout;
const { Title, Text } = Typography;

type Status = 'checking' | 'up' | 'down';

export default function App() {
  const [apiStatus, setApiStatus] = useState<Status>('checking');

  useEffect(() => {
    let cancelled = false;
    fetch('/api/health')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((data) => {
        if (!cancelled) setApiStatus(data.status === 'UP' ? 'up' : 'down');
      })
      .catch(() => {
        if (!cancelled) setApiStatus('down');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center' }}>
        <Title level={3} style={{ color: '#fff', margin: 0 }}>
          Typed Goose
        </Title>
      </Header>
      <Content
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
        }}
      >
        <Card style={{ minWidth: 360, textAlign: 'center' }} title="Goose is alive">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Text>Step 1 — repo skeleton + walking compose.</Text>
            <Space>
              <Text strong>API service:</Text>
              <Tag
                color={apiStatus === 'up' ? 'green' : apiStatus === 'down' ? 'red' : 'default'}
                data-testid="api-status"
              >
                {apiStatus}
              </Tag>
            </Space>
          </Space>
        </Card>
      </Content>
      <Footer
        style={{
          padding: 16,
          background: '#fff',
          borderTop: '1px solid #f0f0f0',
        }}
      >
        <ChatPanel />
      </Footer>
    </Layout>
  );
}
