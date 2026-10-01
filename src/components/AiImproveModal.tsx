import { useEffect, useMemo, useRef, useState } from 'react';
import { aiImprove, aiImproveAsync, streamTaskResult } from '../api/ai';
import { getResume, updateResume } from '../api/resumes';
import { AI_ASYNC_ENABLED, getErrorMessage } from '../api/client';
import { applyAcceptedDiff, diffResume, isEnhancing } from '../utils/resumeDiff';
import type { ResumeDiffItem } from '../utils/resumeDiff';
import type { ResumeData } from '../types/resume';
import { ProgressBar } from './ProgressBar';

interface Props {
  resumeId: string;
  currentData: ResumeData;
  onClose: () => void;
}

/**
 * AI 全文改稿确认：生成改进稿 → 逐条 diff（默认接受增强项）→ 应用选中的改动。
 */
export function AiImproveModal({ resumeId, currentData, onClose }: Props) {
  const [improved, setImproved] = useState<ResumeData | null>(null);
  const [error, setError] = useState('');
  const [accepted, setAccepted] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState(false);
  const started = useRef(false);

  const items: ResumeDiffItem[] = useMemo(
    () => (improved ? diffResume(currentData, improved) : []),
    [currentData, improved],
  );

  useEffect(() => {
    // StrictMode 下 effect 会在开发模式执行两次，用 ref 保证只发起一次请求
    if (started.current) return;
    started.current = true;
    let cancel: (() => void) | undefined;
    if (AI_ASYNC_ENABLED) {
      aiImproveAsync({ resumeId })
        .then((sub) => {
          cancel = streamTaskResult(
            sub.taskId,
            (task) => {
              if (task.status === 'SUCCESS' && task.result) {
                onImproved(task.result as ResumeData);
              } else {
                setError(task.error || 'AI 改稿失败');
              }
            },
            () => setError('结果推送中断，请重试'),
          );
        })
        .catch((err) => setError(getErrorMessage(err, 'AI 改稿失败')));
    } else {
      aiImprove({ resumeId })
        .then(onImproved)
        .catch((err) => setError(getErrorMessage(err, 'AI 改稿失败')));
    }
    return () => cancel?.();
  }, [resumeId]);

  const onImproved = (data: ResumeData) => {
    setImproved(data);
    // 默认接受「有内容的增强项」，清空/删除类改动默认不勾选，避免误删
    const enhancing = diffResume(currentData, data).filter(isEnhancing);
    setAccepted(new Set(enhancing.map((i) => i.key)));
  };

  const toggle = (key: string) => {
    setAccepted((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const apply = async () => {
    if (!improved) return;
    setApplying(true);
    setError('');
    try {
      const fresh = await getResume(resumeId);
      const merged = applyAcceptedDiff(currentData, improved, accepted);
      await updateResume(resumeId, merged, fresh.version);
      window.location.reload();
    } catch (err) {
      setError(getErrorMessage(err, '应用失败'));
      setApplying(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-xl w-[640px] max-w-[92vw] max-h-[86vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h3 className="font-medium text-gray-800">AI 改稿确认</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-lg leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-5">
          {error && <p className="text-sm text-red-500 mb-3">{error}</p>}
          {!error && improved === null && <ProgressBar label="AI 正在生成改进稿，请稍候..." className="py-4" />}
          {improved && items.length === 0 && <p className="text-sm text-gray-400">没有可应用的改动</p>}

          {improved && items.map((it) => (
            <div key={it.key} className="mb-3 p-3 border border-gray-100 rounded-lg">
              <label className="flex items-start gap-2 cursor-pointer select-none">
                <input
                  type="checkbox"
                  checked={accepted.has(it.key)}
                  onChange={() => toggle(it.key)}
                  className="mt-1"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-xs text-gray-500 mb-1.5">{it.label}</p>
                  {it.oldText && (
                    <p className="text-sm text-red-500 line-through whitespace-pre-wrap break-words">{it.oldText}</p>
                  )}
                  {it.revisedText && (
                    <p className="text-sm text-green-600 whitespace-pre-wrap break-words">{it.revisedText}</p>
                  )}
                  {!it.oldText && <p className="text-sm text-gray-400">（新增）</p>}
                  {!it.revisedText && <p className="text-sm text-gray-400">（删除）</p>}
                </div>
              </label>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-end gap-3 px-5 py-4 border-t border-gray-200">
          <button
            onClick={onClose}
            className="px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md"
          >
            取消
          </button>
          <button
            onClick={apply}
            disabled={applying || !improved || items.length === 0}
            className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            {applying ? '应用中...' : `应用选中的改动 (${accepted.size})`}
          </button>
        </div>
      </div>
    </div>
  );
}