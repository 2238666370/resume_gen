import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { createResume, deleteResume, listResumes } from '../api/resumes';
import { listShares, revokeShare } from '../api/shares';
import { consumePendingTemplate } from '../api/templates';
import type { ResumeListItem, ShareVO } from '../api/types';
import { getErrorMessage } from '../api/client';
import { NavTabs } from '../components/layout/NavTabs';
import { MyTemplateList } from '../components/MyTemplateList';

const SIZE = 10;

type Tab = 'resumes' | 'shares' | 'templates';

const TABS: { key: Tab; label: string }[] = [
  { key: 'resumes', label: '我的简历' },
  { key: 'shares', label: '我的分享' },
  { key: 'templates', label: '我的模板' },
];

/**
 * 工作台（F1）：聚合「我的简历 / 我的分享 / 我的模板」，消除模板导航孤岛。
 * Tab 由 `?tab=` 查询参数驱动，可分享、可前进后退。
 */
export function WorkbenchPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const tab: Tab = tabParam === 'shares' || tabParam === 'templates' ? tabParam : 'resumes';

  const [items, setItems] = useState<ResumeListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [shares, setShares] = useState<ShareVO[]>([]);
  const [sharesLoading, setSharesLoading] = useState(false);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listResumes(page, SIZE, keyword || undefined);
      setItems(res.records);
      setTotal(res.total);
    } catch (err) {
      console.error('加载列表失败', err);
    } finally {
      setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => {
    if (tab === 'resumes') load();
  }, [tab, load]);

  const loadShares = useCallback(async () => {
    setSharesLoading(true);
    try {
      setShares(await listShares());
    } catch (err) {
      alert(getErrorMessage(err, '加载分享列表失败'));
    } finally {
      setSharesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === 'shares') loadShares();
  }, [tab, loadShares]);

  const switchTab = (t: Tab) => {
    setParams(t === 'resumes' ? new URLSearchParams() : new URLSearchParams({ tab: t }));
  };

  const totalPages = Math.max(1, Math.ceil(total / SIZE));

  const handleCreate = async () => {
    setCreating(true);
    try {
      const templateId = consumePendingTemplate() ?? undefined;
      const d = await createResume('我的简历', templateId);
      navigate(`/editor/${d.id}`);
    } catch (err) {
      alert(getErrorMessage(err, '创建失败'));
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('确定删除该简历？')) return;
    try {
      await deleteResume(id);
      load();
    } catch (err) {
      alert(getErrorMessage(err, '删除失败'));
    }
  };

  const handleCopyShare = async (text: string, key: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedKey(key);
      setTimeout(() => setCopiedKey(null), 1500);
    } catch {
      alert('复制失败，请手动复制');
    }
  };

  const handleRevokeShare = async (key: string) => {
    if (!window.confirm('确定撤销该分享？')) return;
    try {
      await revokeShare(key);
      loadShares();
    } catch (err) {
      alert(getErrorMessage(err, '撤销失败'));
    }
  };

  return (
    <main className="max-w-4xl mx-auto py-8 px-6">
      <h1 className="text-xl font-semibold text-gray-800 mb-4">工作台</h1>
      <NavTabs
        items={TABS}
        value={tab}
        onChange={switchTab}
        right={
          tab === 'resumes' ? (
            <>
              <form
                onSubmit={(e) => { e.preventDefault(); setKeyword(keywordInput.trim()); setPage(1); }}
                className="flex items-center gap-2"
              >
                <input
                  value={keywordInput}
                  onChange={(e) => setKeywordInput(e.target.value)}
                  placeholder="搜索简历标题"
                  className="border border-gray-300 rounded-md px-3 py-2 text-sm w-44 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <button type="submit" className="px-3 py-2 text-sm border border-gray-300 rounded-md text-gray-600 hover:bg-gray-50">搜索</button>
                {keyword && (
                  <button type="button" onClick={() => { setKeywordInput(''); setKeyword(''); setPage(1); }} className="text-sm text-gray-400 hover:text-gray-600">清除</button>
                )}
              </form>
              <button
                onClick={handleCreate}
                disabled={creating}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors disabled:opacity-60"
              >
                {creating ? '创建中...' : '+ 新建简历'}
              </button>
            </>
          ) : tab === 'templates' ? (
            <button
              onClick={() => navigate('/templates/builder')}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
            >
              + 新建模板
            </button>
          ) : undefined
        }
      />

      {tab === 'resumes' ? (
        <>
          {loading ? (
            <div className="text-center text-gray-400 py-20">加载中...</div>
          ) : items.length === 0 ? (
            <div className="text-center py-20">
              <p className="text-gray-400 mb-4">还没有简历，点击右上角「+ 新建简历」开始</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {items.map((it) => (
                <div
                  key={it.id}
                  className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md transition-shadow cursor-pointer group"
                  onClick={() => navigate(`/editor/${it.id}`)}
                >
                  <div className="flex items-start justify-between">
                    <div className="min-w-0">
                      <h2 className="font-medium text-gray-800 truncate">{it.title || '未命名简历'}</h2>
                      <p className="text-xs text-gray-400 mt-2">{it.updatedAt ? `更新于 ${it.updatedAt}` : '—'}</p>
                    </div>
                    <span className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600 flex-shrink-0 ml-3">
                      {it.templateId}
                    </span>
                  </div>
                  <div className="flex items-center justify-between mt-4">
                    <span className="text-sm text-blue-600 group-hover:underline">打开编辑 →</span>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDelete(it.id); }}
                      className="text-xs text-gray-400 hover:text-red-500 transition-colors"
                    >
                      删除
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-4 mt-8">
              <button
                disabled={page <= 1}
                onClick={() => setPage(page - 1)}
                className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md disabled:opacity-40"
              >
                上一页
              </button>
              <span className="text-sm text-gray-500">{page} / {totalPages}</span>
              <button
                disabled={page >= totalPages}
                onClick={() => setPage(page + 1)}
                className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md disabled:opacity-40"
              >
                下一页
              </button>
            </div>
          )}
        </>
      ) : tab === 'shares' ? (
        sharesLoading ? (
          <div className="text-center text-gray-400 py-20">加载中...</div>
        ) : shares.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-gray-400">还没有分享记录，进入简历编辑器点击「分享」生成</p>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {shares.map((s) => (
              <div key={s.id} className="bg-white rounded-xl border border-gray-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-gray-800 font-mono truncate">{s.shareKey}</p>
                    <p className="text-xs text-gray-400 mt-1.5 truncate">{s.url || '—'}</p>
                    <p className="text-xs text-gray-400 mt-1.5">
                      浏览 {s.viewCount} 次
                      {s.expireAt ? ` · 至 ${s.expireAt}` : ' · 永久有效'}
                      {" · "}
                      <span className={s.status === 1 ? 'text-green-600' : 'text-gray-400'}>
                        {s.status === 1 ? '有效' : '已撤销'}
                      </span>
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {s.url && (
                      <button
                        onClick={() => window.open(s.url, '_blank')}
                        className="px-2.5 py-1 text-xs text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50"
                      >
                        打开
                      </button>
                    )}
                    {s.url && (
                      <button
                        onClick={() => handleCopyShare(s.url || s.shareKey, s.id)}
                        className="px-2.5 py-1 text-xs text-blue-600 border border-blue-200 rounded-md hover:bg-blue-50"
                      >
                        {copiedKey === s.id ? '已复制' : '复制'}
                      </button>
                    )}
                    {s.status === 1 && (
                      <button
                        onClick={() => handleRevokeShare(s.shareKey)}
                        className="px-2.5 py-1 text-xs text-red-500 border border-red-200 rounded-md hover:bg-red-50"
                      >
                        撤销
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )
      ) : (
        <MyTemplateList />
      )}
    </main>
  );
}
