import { useEffect, useRef, useState } from 'react';
import { aiSuggest, aiSuggestAsync, streamTaskResult } from '../api/ai';
import type { ResumeSuggestion } from '../api/ai';
import { AI_ASYNC_ENABLED, getErrorMessage } from '../api/client';
import { ProgressBar } from './ProgressBar';

interface Props {
  resumeId: string;
  onClose: () => void;
  onImprove?: () => void;
}

/**
 * 全文 AI 诊断建议（只诊断不改稿），逐条给出问题与改进建议。
 */
export function AiSuggestModal({ resumeId, onClose, onImprove }: Props) {
  const [items, setItems] = useState<ResumeSuggestion[] | null>(null);
  const [error, setError] = useState('');
  const started = useRef(false);

  useEffect(() => {
    // StrictMode 下 effect 会在开发模式执行两次，用 ref 保证只发起一次请求
    if (started.current) return;
    started.current = true;
    let cancel: (() => void) | undefined;
    if (AI_ASYNC_ENABLED) {
      aiSuggestAsync({ resumeId })
        .then((sub) => {
          cancel = streamTaskResult(
            sub.taskId,
            (task) => {
              if (task.status === 'SUCCESS' && task.result) {
                setItems(task.result as ResumeSuggestion[]);
              } else {
                setError(task.error || 'AI 建议失败');
              }
            },
            () => setError('结果推送中断，请重试'),
          );
        })
        .catch((err) => setError(getErrorMessage(err, 'AI 建议失败')));
    } else {
      aiSuggest({ resumeId })
        .then(setItems)
        .catch((err) => setError(getErrorMessage(err, 'AI 建议失败')));
    }
    return () => cancel?.();
  }, [resumeId]);

  const priorityColor = (p?: string) =>
    p === '高' ? 'text-red-500' : p === '中' ? 'text-orange-500' : 'text-gray-400';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/30"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-xl shadow-xl w-[560px] max-w-[92vw] max-h-[80vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h3 className="font-medium text-gray-800">AI 简历建议</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
        </div>
        <div className="flex-1 overflow-y-auto p-5">
          {error && <p className="text-sm text-red-500">{error}</p>}
          {!error && items === null && <ProgressBar label="AI 正在分析简历，请稍候..." className="py-4" />}
          {items && items.length === 0 && <p className="text-sm text-gray-400">没有建议</p>}
          {items && items.map((it, i) => (
            <div key={i} className="mb-3 p-3 border border-gray-100 rounded-lg">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">{it.section || '综合'}</span>
                <span className={`text-xs font-medium ${priorityColor(it.priority)}`}>{it.priority || ''}</span>
              </div>
              <p className="text-sm text-gray-700">{it.issue}</p>
              <p className="text-xs text-gray-500 mt-1">{it.advice}</p>
            </div>
          ))}
        </div>
        {onImprove && items && items.length > 0 && (
          <div className="flex justify-end px-5 py-4 border-t border-gray-200">
            <button
              onClick={onImprove}
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              执行修改
            </button>
          </div>
        )}
      </div>
    </div>
  );
}