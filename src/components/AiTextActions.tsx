import { useEffect, useRef, useState } from 'react';
import { aiExpand, aiExpandAsync, aiRewrite, aiRewriteAsync, streamTaskResult } from '../api/ai';
import type { ResumeExpandResult, ResumeRewriteResult } from '../api/ai';
import { AI_ASYNC_ENABLED, getErrorMessage } from '../api/client';
import { ProgressBar } from './ProgressBar';

interface Props {
  text: string;
  section: string;
  onApply: (text: string) => void;
}

/**
 * 编辑器内嵌的 AI 润色/扩写按钮：对单段文本调用 R6 接口，展示结果后可替换。
 */
export function AiTextActions({ text, section, onApply }: Props) {
  const [loading, setLoading] = useState<'rewrite' | 'expand' | null>(null);
  const [result, setResult] = useState<ResumeRewriteResult | ResumeExpandResult | null>(null);
  const [error, setError] = useState('');

  const cancelStream = useRef<(() => void) | null>(null);
  useEffect(() => () => cancelStream.current?.(), []);

  const run = async (kind: 'rewrite' | 'expand') => {
    if (!text.trim()) {
      setError('请先输入内容');
      return;
    }
    setError('');
    setResult(null);
    setLoading(kind);
    const payload = { text, section };

    if (AI_ASYNC_ENABLED) {
      try {
        const sub = kind === 'rewrite' ? await aiRewriteAsync(payload) : await aiExpandAsync(payload);
        cancelStream.current?.();
        cancelStream.current = streamTaskResult(
          sub.taskId,
          (task) => {
            setLoading(null);
            if (task.status === 'SUCCESS' && task.result) {
              setResult(task.result as ResumeRewriteResult | ResumeExpandResult);
            } else {
              setError(task.error || 'AI 处理失败');
            }
          },
          () => {
            setLoading(null);
            setError('结果推送中断，请重试');
          },
        );
      } catch (err) {
        setLoading(null);
        setError(getErrorMessage(err, 'AI 处理失败'));
      }
      return;
    }

    try {
      const r = kind === 'rewrite' ? await aiRewrite(payload) : await aiExpand(payload);
      setResult(r);
    } catch (err) {
      setError(getErrorMessage(err, 'AI 处理失败'));
    } finally {
      setLoading(null);
    }
  };

  const apply = () => {
    if (!result) return;
    const newText = 'revised' in result ? result.revised : result.expanded;
    if (newText) onApply(newText);
    setResult(null);
  };

  return (
    <div className="mt-2">
      <div className="flex items-center gap-2">
        <button
          onClick={() => run('rewrite')}
          disabled={!!loading}
          className="text-xs px-2 py-1 rounded border border-blue-200 text-blue-600 hover:bg-blue-50 disabled:opacity-50"
        >
          AI 润色
        </button>
        <button
          onClick={() => run('expand')}
          disabled={!!loading}
          className="text-xs px-2 py-1 rounded border border-blue-200 text-blue-600 hover:bg-blue-50 disabled:opacity-50"
        >
          AI 扩写
        </button>
      </div>
      {loading && (
        <ProgressBar className="mt-2" label={`AI 正在${loading === 'rewrite' ? '润色' : '扩写'}，请稍候...`} />
      )}
      {error && <p className="text-xs text-red-500 mt-1.5">{error}</p>}
      {result && (
        <div className="mt-2 p-2.5 bg-blue-50 rounded border border-blue-100">
          <p className="text-xs text-gray-700 whitespace-pre-wrap">
            {'revised' in result ? result.revised : result.expanded}
          </p>
          <div className="flex items-center gap-2 mt-2">
            <button onClick={apply} className="text-xs px-2.5 py-1 rounded bg-blue-600 text-white hover:bg-blue-700">
              替换
            </button>
            <button onClick={() => setResult(null)} className="text-xs px-2.5 py-1 rounded text-gray-500 hover:bg-gray-100">
              取消
            </button>
          </div>
        </div>
      )}
    </div>
  );
}