import { renderRichText } from '../../utils/textRenderer';
import type { ResumeData } from '../../types/resume';

interface Props {
  data: ResumeData;
}

export function MinimalTemplate({ data }: Props) {
  const {
    personal, education, experience, internship, skills, projects,
    certificates, languages, customSections, sections, accentColor,
  } = data;

  const visibleSections = [...sections]
    .filter((s) => s.visible && s.type !== 'personal')
    .sort((a, b) => a.order - b.order);

  const SectionTitle = ({ title }: { title: string }) => (
    <h2 className="text-xs font-bold tracking-[0.2em] uppercase text-gray-400 mb-3">{title}</h2>
  );

  /** 工作/实习共用渲染 */
  const renderExpList = (list: typeof experience) =>
    (list ?? []).map((exp) => (
      <div key={exp.id} className="mb-4">
        <div className="flex justify-between items-baseline">
          <span className="font-semibold text-gray-900">{exp.position || '职位'}</span>
          <span className="text-xs text-gray-400 ml-2 whitespace-nowrap">
            {exp.startDate}{exp.startDate && ' – '}{exp.current ? '至今' : exp.endDate}
          </span>
        </div>
        {exp.company && <p className="text-xs mt-0.5" style={{ color: accentColor }}>{exp.company}</p>}
        {exp.description && (
          <p className="text-xs text-gray-500 mt-1.5 leading-relaxed">{renderRichText(exp.description)}</p>
        )}
      </div>
    ));

  const renderSection = (sec: (typeof visibleSections)[0]) => {
    switch (sec.type) {
      case 'experience':
        return (experience ?? []).length === 0 ? null : (
          <div key="experience" className="mb-6">
            <SectionTitle title="工作经历" />
            {renderExpList(experience)}
          </div>
        );

      case 'internship':
        return (internship ?? []).length === 0 ? null : (
          <div key="internship" className="mb-6">
            <SectionTitle title="实习经历" />
            {renderExpList(internship)}
          </div>
        );

      case 'education':
        return (education ?? []).length === 0 ? null : (
          <div key="education" className="mb-6">
            <SectionTitle title="教育经历" />
            {(education ?? []).map((edu) => (
              <div key={edu.id} className="mb-3">
                <div className="flex justify-between items-baseline">
                  <span className="font-semibold text-gray-900">{edu.school || '学校名称'}</span>
                  <span className="text-xs text-gray-400 ml-2 whitespace-nowrap">
                    {edu.startDate}{edu.startDate && ' – '}{edu.endDate}
                  </span>
                </div>
                <p className="text-xs text-gray-500 mt-0.5">{edu.degree} {edu.major}</p>
                {edu.description && (
                  <p className="text-xs text-gray-400 mt-0.5">{renderRichText(edu.description)}</p>
                )}
              </div>
            ))}
          </div>
        );

      case 'skills':
        return (skills ?? []).length === 0 ? null : (
          <div key="skills" className="mb-6">
            <SectionTitle title="专业技能" />
            <div className="flex flex-wrap gap-1.5">
              {(skills ?? []).map((skill) => (
                <span
                  key={skill.id}
                  className="text-xs px-2 py-0.5 border rounded text-gray-600"
                  style={{ borderColor: accentColor + '60' }}
                >
                  {skill.name || '技能'}
                </span>
              ))}
            </div>
          </div>
        );

      case 'projects':
        return (projects ?? []).length === 0 ? null : (
          <div key="projects" className="mb-6">
            <SectionTitle title="项目经历" />
            {(projects ?? []).map((proj) => (
              <div key={proj.id} className="mb-3">
                <div className="flex justify-between items-baseline">
                  <span className="font-semibold text-gray-900">{proj.name || '项目名称'}</span>
                  <span className="text-xs text-gray-400 ml-2 whitespace-nowrap">
                    {proj.startDate}{proj.startDate && ' – '}{proj.endDate}
                  </span>
                </div>
                {proj.role && <p className="text-xs mt-0.5" style={{ color: accentColor }}>{proj.role}</p>}
                {proj.description && (
                  <p className="text-xs text-gray-500 mt-1">{renderRichText(proj.description)}</p>
                )}
                {proj.link && <a href={proj.link} className="text-xs mt-0.5 block text-gray-400">{proj.link}</a>}
              </div>
            ))}
          </div>
        );

      case 'certificates':
        return (certificates ?? []).length === 0 ? null : (
          <div key="certificates" className="mb-6">
            <SectionTitle title="证书荣誉" />
            {(certificates ?? []).map((cert) => (
              <div key={cert.id} className="flex justify-between mb-1">
                <span className="text-xs text-gray-700">{cert.name}</span>
                <span className="text-xs text-gray-400">{cert.issuer} {cert.date}</span>
              </div>
            ))}
          </div>
        );

      case 'languages':
        return (languages ?? []).length === 0 ? null : (
          <div key="languages" className="mb-6">
            <SectionTitle title="语言能力" />
            <div className="flex flex-wrap gap-3">
              {(languages ?? []).map((lang) => (
                <span key={lang.id} className="text-xs text-gray-600">
                  {lang.name}{lang.level && ` · ${lang.level}`}
                </span>
              ))}
            </div>
          </div>
        );

      case 'custom': {
        const cs = (customSections ?? []).find((c) => c.id === sec.id);
        if (!cs || !cs.content) return null;
        return (
          <div key={sec.id} className="mb-6">
            <SectionTitle title={cs.title || '自定义板块'} />
            <div className="text-xs text-gray-600 leading-relaxed">
              {renderRichText(cs.content)}
            </div>
          </div>
        );
      }

      default:
        return null;
    }
  };

  const leftTypes = ['experience', 'internship', 'projects', 'custom'];
  const rightTypes = ['education', 'skills', 'languages', 'certificates'];

  return (
    <div data-template="minimal" className="bg-white w-full h-full p-12 font-sans text-[13px] leading-relaxed" style={{ minHeight: '297mm' }}>
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-end justify-between">
          <div>
            <h1 className="text-4xl font-light tracking-tight text-gray-900">{personal.name || '姓名'}</h1>
            {personal.title && (
              <p className="text-sm mt-1 tracking-wider" style={{ color: accentColor }}>{personal.title}</p>
            )}
          </div>
          {personal.avatar && (
            <img src={personal.avatar} alt="avatar" className="w-14 h-14 rounded-sm object-cover" />
          )}
        </div>
        <div className="flex flex-wrap gap-x-5 gap-y-1 mt-3 text-xs text-gray-400">
          {personal.email    && <span>{personal.email}</span>}
          {personal.phone    && <span>{personal.phone}</span>}
          {personal.location && <span>{personal.location}</span>}
          {personal.website  && <span>{personal.website}</span>}
        </div>
        {personal.summary && (
          <p className="mt-3 text-xs text-gray-500 leading-relaxed max-w-xl">
            {renderRichText(personal.summary)}
          </p>
        )}
      </div>

      <div className="h-px bg-gray-200 mb-6" />

      <div className="flex gap-10">
        <div className="flex-1">
          {visibleSections.filter(s => leftTypes.includes(s.type)).map(sec => renderSection(sec))}
        </div>
        <div className="w-44 flex-shrink-0">
          {visibleSections.filter(s => rightTypes.includes(s.type)).map(sec => renderSection(sec))}
        </div>
      </div>
    </div>
  );
}
