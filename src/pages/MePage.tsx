import { useSearchParams } from 'react-router-dom';
import { NavTabs } from '../components/layout/NavTabs';
import { MyStatsPanel } from '../components/MyStatsPanel';
import { ProfileForm } from '../components/ProfileForm';

type Tab = 'stats' | 'profile';

const TABS: { key: Tab; label: string }[] = [
  { key: 'stats', label: '数据中心' },
  { key: 'profile', label: '个人信息' },
];

/**
 * 我的（F1）：聚合「数据中心 / 个人信息」，集中账号与数据入口。
 */
export function MePage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'profile' ? 'profile' : 'stats';

  const switchTab = (t: Tab) => {
    setParams(t === 'stats' ? new URLSearchParams() : new URLSearchParams({ tab: t }));
  };

  return (
    <main className="max-w-4xl mx-auto py-8 px-6">
      <h1 className="text-xl font-semibold text-gray-800 mb-4">我的</h1>
      <NavTabs items={TABS} value={tab} onChange={switchTab} />
      {tab === 'stats' ? (
        <MyStatsPanel />
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 p-6 max-w-2xl">
          <ProfileForm />
        </div>
      )}
    </main>
  );
}
