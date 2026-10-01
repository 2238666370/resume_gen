import { useEffect, useState } from 'react';
import { listResumes } from '../api/resumes';
import { publishPost } from '../api/community';
import type { ResumeListItem } from '../api/types';

interface Props {
  onClose: () => void;
  onPublished: () => void;
}

/** 发布帖子弹窗：选择简历 + 标题/摘要/标签，走规则引擎审核。 */
export function PublishPostModal({ onClose, onPublished }: Props) {
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [resumeId, setResumeId] = useState('');
  const [title, setTitle] = useState('');
  const [summary, setSummary] = useState('');
  const [tags, setTags] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; msg: string } | null>(null);

  useEffect(() => {
    listResumes(1, 50)
      .then((r) => {
        setResumes(r.records);
        if (r.records.length > 0) setResumeId(r.records[0].id);
      })
      .catch(() => setResumes([]));
  }, []);

  const submit = async () => {
    if (!resumeId) {
      setResult({ ok: false, msg: '请先选择要发布的简历' });
      return;
    }
    if (!title.trim()) {
      setResult({ ok: false, msg: '请填写标题' });
      return;
    }
    setSubmitting(true);
    try {
      const post = await publishPost({
        resumeId,
        title: title.trim(),
        summary: summary.trim() || undefined,
        tags: tags.split(/[,，]/).map((t) => t.trim()).filter(Boolean),
      });
      const ok = post.auditStatus === 1;
      setResult({
        ok,
        msg: ok ? '发布成功，已上线社区' : `未通过审核：${post.auditReason || '内容违规'}`,
      });
      if (ok) onPublished();
    } catch (err) {
      setResult({ ok: false, msg: (err as Error)?.message || '发布失败' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-2xl w-[420px] max-w-[92vw] p-6" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-semibold text-gray-800 mb-4">发布到社区</h2>

        <label className="block text-sm text-gray-500 mb-1">选择简历</label>
        <select
          value={resumeId}
          onChange={(e) => setResumeId(e.target.value)}
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {resumes.length === 0 && <option value="">暂无简历</option>}
          {resumes.map((r) => (
            <option key={r.id} value={r.id}>{r.title || '未命名简历'}</option>
          ))}
        </select>

        <label className="block text-sm text-gray-500 mb-1">标题</label>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={100}
          placeholder="一句话介绍这份简历"
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />

        <label className="block text-sm text-gray-500 mb-1">摘要（可选）</label>
        <textarea
          value={summary}
          onChange={(e) => setSummary(e.target.value)}
          maxLength={500}
          rows={3}
          placeholder="简要说明亮点"
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm mb-3 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        />

        <label className="block text-sm text-gray-500 mb-1">标签（逗号分隔，可选）</label>
        <input
          value={tags}
          onChange={(e) => setTags(e.target.value)}
          placeholder="如：前端, React, 3年经验"
          className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />

        {result && (
          <p className={`text-sm mb-3 ${result.ok ? 'text-green-600' : 'text-red-500'}`}>{result.msg}</p>
        )}

        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-300 rounded-md hover:bg-gray-50">
            关闭
          </button>
          <button
            onClick={submit}
            disabled={submitting}
            className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-60"
          >
            {submitting ? '审核中...' : '发布'}
          </button>
        </div>
      </div>
    </div>
  );
}