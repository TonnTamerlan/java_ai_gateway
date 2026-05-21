import { Layout, Typography } from 'antd';
import ChatPanel from './components/ChatPanel';
import FileSummarizationPanel from './components/FileSummarizationPanel';
import FilesTable from './components/FilesTable';

const { Header, Content } = Layout;
const { Title } = Typography;

export default function App() {
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
          flexDirection: 'column',
          alignItems: 'center',
          padding: 24,
          gap: 16,
        }}
      >
        <ChatPanel />
        <FilesTable />
        <FileSummarizationPanel />
      </Content>
    </Layout>
  );
}
