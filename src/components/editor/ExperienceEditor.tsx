import { useResumeStore } from '../../store/resumeStore';
import { BoldHint } from './BoldHint';
import { AiTextActions } from '../AiTextActions';

export function ExperienceEditor() {
  const { data, addExperience, updateExperience, removeExperience } = useResumeStore();
  const { experience } = data;

  return (
    <div>
      {experience.map((exp, idx) => (
        <div key={exp.id} className="mb-4 p-3 bg-gray-50 rounded-lg border border-gray-100 group">
          <div className="flex justify-between items-center mb-2">
            <span className="text-xs font-semibold text-gray-400">第 {idx + 1} 段经历</span>
            <button
              onClick={() => removeExperience(exp.id)}
              className="text-xs text-red-400 opacity-0 group-hover:opacity-100 transition-opacity hover:text-red-600"
            >
              删除
            </button>
          </div>
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">公司名称</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={exp.company}
                placeholder="如：阿里巴巴"
                onChange={(e) => updateExperience(exp.id, { company: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">职位</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={exp.position}
                placeholder="如：前端工程师"
                onChange={(e) => updateExperience(exp.id, { position: e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-3 mb-2 items-end">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">开始时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={exp.startDate}
                placeholder="2022.07"
                onChange={(e) => updateExperience(exp.id, { startDate: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">结束时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={exp.current ? '至今' : exp.endDate}
                disabled={exp.current}
                placeholder="2024.01"
                onChange={(e) => updateExperience(exp.id, { endDate: e.target.value })}
              />
            </div>
            <div className="flex items-center gap-1.5 pb-1.5">
              <input
                type="checkbox"
                id={`current_${exp.id}`}
                checked={exp.current}
                onChange={(e) => updateExperience(exp.id, { current: e.target.checked })}
                className="w-3.5 h-3.5 cursor-pointer"
              />
              <label htmlFor={`current_${exp.id}`} className="text-xs text-gray-500 whitespace-nowrap cursor-pointer">至今</label>
            </div>
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">工作内容（支持分行描述）</label>
            <textarea
              className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400 resize-none"
              rows={4}
              value={exp.description}
              placeholder="• 负责 **XX系统** 前端开发&#10;• 优化页面性能，提升加载速度 **30%**"
              onChange={(e) => updateExperience(exp.id, { description: e.target.value })}
            />
            <BoldHint />
            <AiTextActions text={exp.description} section="experience" onApply={(v) => updateExperience(exp.id, { description: v })} />
          </div>
        </div>
      ))}
      <button
        onClick={addExperience}
        className="w-full py-2 border-2 border-dashed border-gray-200 rounded-lg text-sm text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
      >
        + 添加工作经历
      </button>
    </div>
  );
}
