import { useEffect, useState } from 'react';
import { getProfile, updateProfile } from '../api/profile';
import type { ProfileVO } from '../api/types';
import { getErrorMessage } from '../api/client';

const FIELDS: { key: keyof ProfileVO; label: string; placeholder?: string }[] = [
  { key: 'name', label: '姓名', placeholder: '真实姓名' },
  { key: 'title', label: '求职意向', placeholder: '如：前端开发工程师' },
  { key: 'phone', label: '电话' },
  { key: 'email', label: '邮箱' },
  { key: 'location', label: '所在地' },
  { key: 'website', label: '个人主页' },
  { key: 'avatar', label: '头像 URL' },
];

interface Props {
  /** 保存成功回调（弹窗场景用于关闭，内联场景可忽略）。 */
  onSaved?: () => void;
}

/**
 * 个人信息表单（F1）：从 ProfileModal 抽取，供「我的 · 个人信息」内联与弹窗复用。
 */
export function ProfileForm({ onSaved }: Props) {
  const [form, setForm] = useState<ProfileVO>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    getProfile()
      .then(setForm)
      .catch((err) => setError(getErrorMessage(err, '加载失败')))
      .finally(() => setLoading(false));
  }, []);

  const handleSave = () => {
    setSaving(true);
    setError('');
    setSaved(false);
    updateProfile(form)
      .then(() => {
        setSaved(true);
        onSaved?.();
      })
      .catch((err) => setError(getErrorMessage(err, '保存失败')))
      .finally(() => setSaving(false));
  };

  if (loading) {
    return <p className="text-sm text-gray-400 text-center py-10">加载中...</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      {error && <p className="text-sm text-red-500">{error}</p>}

      {FIELDS.map((f) => (
        <label key={f.key} className="flex items-center gap-3 text-sm text-gray-600">
          <span className="w-20 flex-shrink-0">{f.label}</span>
          <input
            type="text"
            value={form[f.key] || ''}
            placeholder={f.placeholder}
            onChange={(e) => setForm((prev) => ({ ...prev, [f.key]: e.target.value }))}
            className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </label>
      ))}

      <label className="flex items-start gap-3 text-sm text-gray-600">
        <span className="w-20 flex-shrink-0 pt-2">个人简介</span>
        <textarea
          value={form.summary || ''}
          placeholder="一句话介绍自己"
          rows={3}
          onChange={(e) => setForm((prev) => ({ ...prev, summary: e.target.value }))}
          className="flex-1 border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        />
      </label>

      <div className="flex items-center justify-end gap-3 mt-2">
        {saved && <span className="text-sm text-green-600">已保存</span>}
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-60"
        >
          {saving ? '保存中...' : '保存'}
        </button>
      </div>
    </div>
  );
}
