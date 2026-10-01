import { useSearchParams } from 'react-router-dom';
import { NavTabs } from '../components/layout/NavTabs';
import { InterviewPanel } from '../components/InterviewPanel';
import { AiHistoryPanel } from '../components/AiHistoryPanel';

type Tab = 'interview' | 'history';

const TABS: { key: Tab; label: string }[] = [
  { key: 'interview', label: '面试准备' },
  { key: 'history', label: '生成记录' },
];

/**
 * AI 助手（F1）：聚合「面试准备 / 生成记录」，集中全部 AI 能力与产出入口。
 */
export function AiHubPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'history' ? 'history' : 'interview';

  const switchTab = (t: Tab) => {
    setParams(t === 'interview' ? new URLSearchParams() : new URLSearchParams({ tab: t }));
  };

  return (
    <main className="max-w-3xl mx-auto py-8 px-6">
      <h1 className="text-xl font-semibold text-gray-800 mb-4">AI 助手</h1>
      <NavTabs items={TABS} value={tab} onChange={switchTab} />
      {tab === 'interview' ? <InterviewPanel /> : <AiHistoryPanel />}
    </main>
  );
}
