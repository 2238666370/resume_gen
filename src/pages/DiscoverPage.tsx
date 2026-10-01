import { useSearchParams } from 'react-router-dom';
import { NavTabs } from '../components/layout/NavTabs';
import { CommunityPanel } from '../components/CommunityPanel';
import { TemplateMarketPanel } from '../components/TemplateMarketPanel';

type Tab = 'community' | 'market';

const TABS: { key: Tab; label: string }[] = [
  { key: 'community', label: '社区' },
  { key: 'market', label: '模板市场' },
];

/**
 * 发现（F1）：聚合「社区 / 模板市场」，集中内容与资源入口；匿名可访问。
 */
export function DiscoverPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'market' ? 'market' : 'community';

  const switchTab = (t: Tab) => {
    setParams(t === 'community' ? new URLSearchParams() : new URLSearchParams({ tab: t }));
  };

  return (
    <main className="max-w-5xl mx-auto py-8 px-6">
      <h1 className="text-xl font-semibold text-gray-800 mb-4">发现</h1>
      <NavTabs items={TABS} value={tab} onChange={switchTab} />
      {tab === 'community' ? <CommunityPanel /> : <TemplateMarketPanel />}
    </main>
  );
}
