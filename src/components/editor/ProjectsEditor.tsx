import { useResumeStore } from '../../store/resumeStore';
import { BoldHint } from './BoldHint';
import { AiTextActions } from '../AiTextActions';

export function ProjectsEditor() {
  const { data, addProject, updateProject, removeProject } = useResumeStore();
  const { projects } = data;

  return (
    <div>
      {projects.map((proj, idx) => (
        <div key={proj.id} className="mb-4 p-3 bg-gray-50 rounded-lg border border-gray-100 group">
          <div className="flex justify-between items-center mb-2">
            <span className="text-xs font-semibold text-gray-400">项目 {idx + 1}</span>
            <button
              onClick={() => removeProject(proj.id)}
              className="text-xs text-red-400 opacity-0 group-hover:opacity-100 transition-opacity hover:text-red-600"
            >
              删除
            </button>
          </div>
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">项目名称</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={proj.name}
                placeholder="如：个人博客系统"
                onChange={(e) => updateProject(proj.id, { name: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">担任角色</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={proj.role}
                placeholder="前端负责人"
                onChange={(e) => updateProject(proj.id, { role: e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">开始时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={proj.startDate}
                placeholder="2023.03"
                onChange={(e) => updateProject(proj.id, { startDate: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">结束时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={proj.endDate}
                placeholder="2023.06"
                onChange={(e) => updateProject(proj.id, { endDate: e.target.value })}
              />
            </div>
          </div>
          <div className="mb-2">
            <label className="block text-xs text-gray-500 mb-1">项目链接</label>
            <input
              className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
              value={proj.link}
              placeholder="https://github.com/..."
              onChange={(e) => updateProject(proj.id, { link: e.target.value })}
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">项目描述</label>
            <textarea
              className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400 resize-none"
              rows={3}
              value={proj.description}
              placeholder="• 技术栈：**React**/Node.js/MySQL&#10;• 核心功能：...&#10;• 成果：性能提升 **40%**"
              onChange={(e) => updateProject(proj.id, { description: e.target.value })}
            />
            <BoldHint />
            <AiTextActions text={proj.description} section="projects" onApply={(v) => updateProject(proj.id, { description: v })} />
          </div>
        </div>
      ))}
      <button
        onClick={addProject}
        className="w-full py-2 border-2 border-dashed border-gray-200 rounded-lg text-sm text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
      >
        + 添加项目经历
      </button>
    </div>
  );
}
