import { useResumeStore } from '../../store/resumeStore';
import { BoldHint } from './BoldHint';

interface Props {
  /** 对应 CustomSection.id */
  customId: string;
}

export function CustomSectionEditor({ customId }: Props) {
  const { data, updateCustomSection } = useResumeStore();
  const section = data.customSections.find((s) => s.id === customId);

  if (!section) return <p className="text-xs text-gray-400">板块数据丢失，请重新添加。</p>;

  return (
    <div>
      {/* 板块标题可编辑 */}
      <div className="mb-3">
        <label className="block text-xs text-gray-500 mb-1">板块名称</label>
        <input
          className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm font-medium focus:outline-none focus:border-blue-400"
          value={section.title}
          placeholder="如：荣誉奖项 / 社团活动 / 自我评价..."
          onChange={(e) => updateCustomSection(customId, { title: e.target.value })}
        />
      </div>

      {/* 内容支持加粗 */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">内容</label>
        <textarea
          className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400 resize-none"
          rows={6}
          value={section.content}
          placeholder={'• **国家奖学金** 一等奖（2023）\n• 校级 **优秀学生干部**\n• 担任 **学生会主席**，组织多次大型活动'}
          onChange={(e) => updateCustomSection(customId, { content: e.target.value })}
        />
        <BoldHint />
      </div>
    </div>
  );
}
