import { useResumeStore } from '../../store/resumeStore';
import { AiTextActions } from '../AiTextActions';

export function PersonalEditor() {
  const { data, updatePersonal } = useResumeStore();
  const { personal } = data;

  const field = (label: string, key: keyof typeof personal, placeholder = '') => (
    <div className="mb-3">
      <label className="block text-xs font-medium text-gray-500 mb-1">{label}</label>
      <input
        className="w-full border border-gray-200 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:border-blue-400 transition-colors"
        value={(personal[key] as string) || ''}
        placeholder={placeholder}
        onChange={(e) => updatePersonal({ [key]: e.target.value })}
      />
    </div>
  );

  return (
    <div>
      <div className="flex gap-3 mb-3">
        <div className="flex-1">{field('姓名', 'name', '请输入姓名')}</div>
        <div className="flex-1">{field('职位/求职意向', 'title', '如：前端工程师')}</div>
      </div>
      <div className="flex gap-3 mb-3">
        <div className="flex-1">{field('邮箱', 'email', 'example@email.com')}</div>
        <div className="flex-1">{field('电话', 'phone', '13800000000')}</div>
      </div>
      <div className="flex gap-3 mb-3">
        <div className="flex-1">{field('所在城市', 'location', '北京')}</div>
        <div className="flex-1">{field('个人网站/GitHub', 'website', 'https://')}</div>
      </div>
      <div className="mb-3">
        <label className="block text-xs font-medium text-gray-500 mb-1">头像 URL</label>
        <input
          className="w-full border border-gray-200 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:border-blue-400 transition-colors"
          value={personal.avatar || ''}
          placeholder="输入头像图片链接"
          onChange={(e) => updatePersonal({ avatar: e.target.value })}
        />
      </div>
      <div className="mb-3">
        <label className="block text-xs font-medium text-gray-500 mb-1">个人简介</label>
        <textarea
          className="w-full border border-gray-200 rounded-md px-3 py-1.5 text-sm focus:outline-none focus:border-blue-400 transition-colors resize-none"
          rows={3}
          value={personal.summary || ''}
          placeholder="简短介绍自己的背景、技能与目标..."
          onChange={(e) => updatePersonal({ summary: e.target.value })}
        />
        <AiTextActions text={personal.summary || ''} section="personal.summary" onApply={(v) => updatePersonal({ summary: v })} />
      </div>
    </div>
  );
}
