import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listMarketTemplates, setPendingTemplate, useTemplate } from '../api/templates';
import type { TemplateVO } from '../api/types';
import { getErrorMessage } from '../api/client';
import { ResumeTemplate } from './templates/ResumeTemplate';
import { SAMPLE_RESUME } from '../data/sampleResume';
import { useAuthStore } from '../store/authStore';

const SIZE = 12;

/**
 * 模板市场面板（F1）：从 TemplateMarketPage 抽取，供「发现 · 模板市场」Tab 复用，不含页面壳。
 */
export function TemplateMarketPanel() {
  const navigate = useNavigate();
  const token = useAuthStore((s) => s.token);
  const [items, setItems] = useState<TemplateVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState<TemplateVO | null>(null);
  const [using, setUsing] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listMarketTemplates({ page, size: SIZE, keyword: search });
      setItems(res.records);
      setTotal(res.total);
    } catch (err) {
      setError(getErrorMessage(err, '加载失败'));
    } finally {
      setLoading(false);
    }
  }, [page, search]);

  useEffect(() => { load(); }, [load]);

  const totalPages = Math.max(1, Math.ceil(total / SIZE));

  const handleUse = async (t: TemplateVO) => {
    if (!token) {
      navigate('/login', { replace: true });
      return;
    }
    setUsing(t.id);
    try {
      await useTemplate(t.id);
      setPendingTemplate(t.code);
      navigate('/', { replace: true });
    } catch (err) {
      alert(getErrorMessage(err, '使用失败'));
      setUsing(null);
    }
  };

  return (
    <>
      <div className="flex gap-2 mb-6">
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { setPage(1); setSearch(keyword.trim()); } }}
          placeholder="搜索模板名称"
          className="flex-1 max-w-xs border border-gray-200 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-400"
        />
        <button onClick={() => { setPage(1); setSearch(keyword.trim()); }} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">搜索</button>
      </div>

      {error && <p className="text-sm text-red-500 mb-4">{error}</p>}

      {loading ? (
        <div className="text-center text-gray-400 py-20">加载中...</div>
      ) : items.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-400">暂无可用的市场模板</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          {items.map((t) => (
            <div key={t.id} className="bg-white rounded-xl border border-gray-200 p-4 hover:shadow-md transition-shadow">
              <div className="w-full aspect-[3/4] bg-gray-100 rounded mb-3 overflow-hidden">
                <div className="w-full h-full scale-[0.55] origin-top-left" style={{ width: '180%', height: '180%' }}>
                  <ResumeTemplate data={{ ...SAMPLE_RESUME, templateId: t.code }} schema={t.schema} />
                </div>
              </div>
              <h3 className="font-medium text-gray-800 text-sm truncate">{t.name}</h3>
              <p className="text-xs text-gray-400 mt-1">{t.category || '未分类'} · 使用 {t.useCount ?? 0} 次</p>
              <div className="flex items-center gap-2 mt-3">
                <button onClick={() => setPreview(t)} className="flex-1 px-3 py-1.5 text-xs text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">预览</button>
                <button
                  onClick={() => handleUse(t)}
                  disabled={using === t.id}
                  className="flex-1 px-3 py-1.5 text-xs bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-60"
                >
                  {using === t.id ? '应用中...' : '使用'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-4 mt-8">
          <button disabled={page <= 1} onClick={() => setPage(page - 1)} className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md disabled:opacity-40">上一页</button>
          <span className="text-sm text-gray-500">{page} / {totalPages}</span>
          <button disabled={page >= totalPages} onClick={() => setPage(page + 1)} className="px-3 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-md disabled:opacity-40">下一页</button>
        </div>
      )}

      {preview && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-6" onClick={() => setPreview(null)}>
          <div className="bg-white rounded-xl max-w-3xl w-full max-h-[90vh] overflow-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between px-5 py-3 border-b border-gray-200">
              <span className="font-semibold text-gray-800">{preview.name}</span>
              <button onClick={() => setPreview(null)} className="text-gray-400 hover:text-gray-600">✕</button>
            </div>
            <div className="p-5 overflow-auto">
              <div className="bg-white shadow-lg rounded-sm overflow-hidden" style={{ minHeight: '800px' }}>
                <ResumeTemplate data={{ ...SAMPLE_RESUME, templateId: preview.code }} schema={preview.schema} />
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
