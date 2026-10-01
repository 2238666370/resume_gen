import { useCallback, useEffect, useState } from 'react';
import { getAiHistory, listAiHistory } from '../api/ai';
import type { AiHistoryRecord } from '../api/ai';
import { getErrorMessage } from '../api/client';

const TASK_LABELS: Record<string, string> = {
  interview: '题库生成',
  improve: '全文改稿',
  rewrite: '润色',
  expand: '扩写',
  suggest: '建议',
  'resume.score': '简历评分',
};

const FILTERS: { value: string; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'interview', label: '题库' },
  { value: 'improve', label: '改稿' },
  { value: 'rewrite', label: '润色' },
  { value: 'expand', label: '扩写' },
  { value: 'suggest', label: '建议' },
  { value: 'resume.score', label: '评分' },
];

function pretty(text?: string): string {
  if (!text) return '—';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

/**
 * AI 生成记录面板（F1）：从 AiHistoryPage 抽取，供「AI 助手 · 生成记录」Tab 复用，不含页面壳。
 */
export function AiHistoryPanel() {
  const [history, setHistory] = useState<AiHistoryRecord[]>([]);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState<AiHistoryRecord | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setHistory(await listAiHistory(filter || undefined, 100));
    } catch (err) {
      setError(getErrorMessage(err, '加载历史记录失败'));
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  const openDetail = async (id: string) => {
    setDetailLoading(true);
    setDetail(null);
    try {
      setDetail(await getAiHistory(id));
    } catch (err) {
      setError(getErrorMessage(err, '加载详情失败'));
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <>
      <div className="flex items-center gap-1 mb-6 flex-wrap">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
              filter === f.value ? 'bg-blue-600 text-white' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-500 mb-4">{error}</p>}

      {loading ? (
        <div className="text-center text-gray-400 py-16">加载中...</div>
      ) : history.length === 0 ? (
        <div className="text-center text-gray-400 py-16">暂无 AI 生成记录</div>
      ) : (
        <div className="flex flex-col gap-3">
          {history.map((h) => (
            <div key={h.id} className="bg-white rounded-xl border border-gray-200 p-5">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600">
                      {TASK_LABELS[h.taskType] || h.taskType}
                    </span>
                    <span className="text-sm text-gray-500 truncate">
                      {h.resumeTitle || '未关联简历'}
                    </span>
                  </div>
                  <p className="text-xs text-gray-400 mt-2">
                    {h.createdAt || ''}
                    {h.tokenUsage != null ? ` · ${h.tokenUsage} tokens` : ''}
                  </p>
                </div>
                <button
                  onClick={() => openDetail(h.id)}
                  className="text-xs text-blue-600 hover:text-blue-700 flex-shrink-0"
                >
                  查看详情
                </button>
              </div>
              {h.summary && (
                <p className="text-sm text-gray-600 mt-3 whitespace-pre-wrap break-words line-clamp-3">{h.summary}</p>
              )}
            </div>
          ))}
        </div>
      )}

      {(detail || detailLoading) && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-6 z-50" onClick={() => setDetail(null)}>
          <div
            className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[80vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-3 border-b border-gray-100">
              <span className="font-medium text-gray-800 text-sm">
                {detail ? `${TASK_LABELS[detail.taskType] || detail.taskType} · ${detail.resumeTitle || '未关联简历'}` : ''}
              </span>
              <button onClick={() => setDetail(null)} className="text-gray-400 hover:text-gray-600 text-sm">关闭</button>
            </div>
            <div className="px-5 py-4 overflow-auto">
              {detailLoading ? (
                <div className="text-center text-gray-400 py-10">加载中...</div>
              ) : (
                <pre className="text-xs text-gray-700 whitespace-pre-wrap break-words leading-relaxed">{pretty(detail?.output)}</pre>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
