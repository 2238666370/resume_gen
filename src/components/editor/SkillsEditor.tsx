import { useResumeStore } from '../../store/resumeStore';

export function SkillsEditor() {
  const { data, addSkill, updateSkill, removeSkill } = useResumeStore();
  const { skills } = data;

  return (
    <div>
      <div className="space-y-2 mb-3">
        {skills.map((skill) => (
          <div key={skill.id} className="flex items-center gap-3 group">
            <input
              className="flex-1 border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
              value={skill.name}
              placeholder="技能名称，如：React"
              onChange={(e) => updateSkill(skill.id, { name: e.target.value })}
            />
            <div className="flex gap-0.5">
              {[1, 2, 3, 4, 5].map((lvl) => (
                <button
                  key={lvl}
                  onClick={() => updateSkill(skill.id, { level: lvl })}
                  className="w-5 h-5 rounded-sm transition-colors"
                  style={{
                    backgroundColor: lvl <= skill.level ? '#2563eb' : '#e5e7eb',
                  }}
                  title={`熟练度 ${lvl}/5`}
                />
              ))}
            </div>
            <button
              onClick={() => removeSkill(skill.id)}
              className="text-xs text-red-300 opacity-0 group-hover:opacity-100 transition-opacity hover:text-red-500 flex-shrink-0"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
      <button
        onClick={addSkill}
        className="w-full py-2 border-2 border-dashed border-gray-200 rounded-lg text-sm text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
      >
        + 添加技能
      </button>
      <p className="text-xs text-gray-400 mt-1.5 text-center">点击色块调整熟练度（1–5）</p>
    </div>
  );
}
