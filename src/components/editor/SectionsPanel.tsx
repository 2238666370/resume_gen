import { useState } from 'react';
import {
  DndContext, closestCenter, KeyboardSensor,
  PointerSensor, useSensor, useSensors, type DragEndEvent,
} from '@dnd-kit/core';
import {
  arrayMove, SortableContext, sortableKeyboardCoordinates,
  useSortable, verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useResumeStore } from '../../store/resumeStore';
import { PersonalEditor } from './PersonalEditor';
import { EducationEditor } from './EducationEditor';
import { ExperienceEditor } from './ExperienceEditor';
import { InternshipEditor } from './InternshipEditor';
import { SkillsEditor } from './SkillsEditor';
import { ProjectsEditor } from './ProjectsEditor';
import { CertificatesEditor, LanguagesEditor } from './OtherEditors';
import { CustomSectionEditor } from './CustomSectionEditor';
import type { ResumeSection } from '../../types/resume';

const SECTION_LABELS: Record<string, string> = {
  personal:     '👤 个人信息',
  education:    '🎓 教育经历',
  experience:   '💼 工作经历',
  internship:   '🏢 实习经历',
  skills:       '⚡ 专业技能',
  projects:     '🚀 项目经历',
  certificates: '🏆 证书荣誉',
  languages:    '🌐 语言能力',
  custom:       '📝 自定义板块',
};

function SortableSection({ section }: { section: ResumeSection }) {
  const { data, toggleSection, removeCustomSection } = useResumeStore();
  const [expanded, setExpanded] = useState(section.type === 'personal');

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: section.id });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  // 自定义板块的实际标题（从 customSections 读取）
  const customSection = section.type === 'custom'
    ? data.customSections.find((c) => c.id === section.id)
    : null;

  const label = customSection
    ? `📝 ${customSection.title || '自定义板块'}`
    : (SECTION_LABELS[section.type] ?? section.type);

  const renderEditor = () => {
    if (!section.visible) return null;
    switch (section.type) {
      case 'personal':     return <PersonalEditor />;
      case 'education':    return <EducationEditor />;
      case 'experience':   return <ExperienceEditor />;
      case 'internship':   return <InternshipEditor />;
      case 'skills':       return <SkillsEditor />;
      case 'projects':     return <ProjectsEditor />;
      case 'certificates': return <CertificatesEditor />;
      case 'languages':    return <LanguagesEditor />;
      case 'custom':       return <CustomSectionEditor customId={section.id} />;
      default:             return null;
    }
  };

  return (
    <div ref={setNodeRef} style={style} className="mb-2">
      <div className={`border rounded-lg overflow-hidden transition-shadow ${isDragging ? 'shadow-lg' : 'shadow-sm'}`}>
        {/* Header */}
        <div className="flex items-center bg-white px-3 py-2.5 gap-2">
          {/* Drag Handle */}
          <div
            {...attributes}
            {...listeners}
            className="cursor-grab active:cursor-grabbing text-gray-300 hover:text-gray-500 transition-colors flex-shrink-0"
            title="拖拽排序"
          >
            <svg width="12" height="16" viewBox="0 0 12 16" fill="currentColor">
              <circle cx="3" cy="3" r="1.5" /><circle cx="9" cy="3" r="1.5" />
              <circle cx="3" cy="8" r="1.5" /><circle cx="9" cy="8" r="1.5" />
              <circle cx="3" cy="13" r="1.5" /><circle cx="9" cy="13" r="1.5" />
            </svg>
          </div>

          {/* Visibility Toggle */}
          <button
            onClick={() => toggleSection(section.id)}
            className={`w-8 h-4 rounded-full transition-colors flex-shrink-0 ${section.visible ? 'bg-blue-500' : 'bg-gray-200'}`}
            title={section.visible ? '点击隐藏' : '点击显示'}
          >
            <div className={`w-3 h-3 bg-white rounded-full transition-transform mx-0.5 ${section.visible ? 'translate-x-3.5' : 'translate-x-0'}`} />
          </button>

          {/* Title */}
          <button
            className="flex-1 text-left text-sm font-medium text-gray-700 truncate"
            onClick={() => section.visible && setExpanded(!expanded)}
          >
            {label}
          </button>

          {/* Remove custom section */}
          {section.type === 'custom' && (
            <button
              onClick={() => removeCustomSection(section.id)}
              className="text-xs text-red-300 hover:text-red-500 transition-colors flex-shrink-0 px-1"
              title="删除此自定义板块"
            >
              ✕
            </button>
          )}

          {/* Expand arrow */}
          {section.visible && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-gray-400 hover:text-gray-600 transition-transform flex-shrink-0"
              style={{ transform: expanded ? 'rotate(180deg)' : 'rotate(0)' }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
          )}
        </div>

        {/* Editor */}
        {section.visible && expanded && (
          <div className="px-3 pb-3 pt-1 border-t border-gray-100 bg-white">
            {renderEditor()}
          </div>
        )}
      </div>
    </div>
  );
}

export function SectionsPanel() {
  const { data, setSections, addCustomSection } = useResumeStore();
  const { sections } = data;
  const sortedSections = [...sections].sort((a, b) => a.order - b.order);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIdx = sortedSections.findIndex((s) => s.id === active.id);
    const newIdx = sortedSections.findIndex((s) => s.id === over.id);
    const reordered = arrayMove(sortedSections, oldIdx, newIdx).map((s, i) => ({ ...s, order: i }));
    setSections(reordered);
  };

  return (
    <div>
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={sortedSections.map((s) => s.id)} strategy={verticalListSortingStrategy}>
          {sortedSections.map((sec) => (
            <SortableSection key={sec.id} section={sec} />
          ))}
        </SortableContext>
      </DndContext>

      {/* Add custom section button */}
      <button
        onClick={addCustomSection}
        className="w-full mt-2 py-2 border-2 border-dashed border-purple-200 rounded-lg text-sm text-purple-400 hover:border-purple-400 hover:text-purple-600 hover:bg-purple-50 transition-colors"
      >
        + 添加自定义板块
      </button>
    </div>
  );
}
