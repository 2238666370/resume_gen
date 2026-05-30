import { useResumeStore } from '../../store/resumeStore';

export function EducationEditor() {
  const { data, addEducation, updateEducation, removeEducation } = useResumeStore();
  const { education } = data;

  return (
    <div>
      {education.map((edu, idx) => (
        <div key={edu.id} className="mb-4 p-3 bg-gray-50 rounded-lg border border-gray-100 group relative">
          <div className="flex justify-between items-center mb-2">
            <span className="text-xs font-semibold text-gray-400">第 {idx + 1} 段经历</span>
            <button
              onClick={() => removeEducation(edu.id)}
              className="text-xs text-red-400 opacity-0 group-hover:opacity-100 transition-opacity hover:text-red-600"
            >
              删除
            </button>
          </div>
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">学校名称</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={edu.school}
                placeholder="如：北京大学"
                onChange={(e) => updateEducation(edu.id, { school: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">学历</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={edu.degree}
                placeholder="本科/硕士/博士"
                onChange={(e) => updateEducation(edu.id, { degree: e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">专业</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={edu.major}
                placeholder="计算机科学与技术"
                onChange={(e) => updateEducation(edu.id, { major: e.target.value })}
              />
            </div>
            <div className="w-20">
              <label className="block text-xs text-gray-500 mb-1">GPA</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={edu.gpa}
                placeholder="3.8"
                onChange={(e) => updateEducation(edu.id, { gpa: e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">开始时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={edu.startDate}
                placeholder="2020.09"
                onChange={(e) => updateEducation(edu.id, { startDate: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">结束时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={edu.endDate}
                placeholder="2024.06"
                onChange={(e) => updateEducation(edu.id, { endDate: e.target.value })}
              />
            </div>
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">描述（荣誉、活动等）</label>
            <textarea
              className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400 resize-none"
              rows={2}
              value={edu.description}
              placeholder="如：获得国家奖学金，担任班长..."
              onChange={(e) => updateEducation(edu.id, { description: e.target.value })}
            />
          </div>
        </div>
      ))}
      <button
        onClick={addEducation}
        className="w-full py-2 border-2 border-dashed border-gray-200 rounded-lg text-sm text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
      >
        + 添加教育经历
      </button>
    </div>
  );
}
