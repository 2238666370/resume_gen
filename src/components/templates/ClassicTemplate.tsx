import { renderRichText } from '../../utils/textRenderer';
import type { ResumeData } from '../../types/resume';

interface Props {
  data: ResumeData;
}

const SectionTitle = ({ title, color }: { title: string; color: string }) => (
  <div className="mb-3">
    <h2 className="text-base font-bold tracking-widest uppercase" style={{ color }}>
      {title}
    </h2>
    <div className="h-px mt-1" style={{ backgroundColor: color, opacity: 0.4 }} />
  </div>
);

const SkillBar = ({ level, color }: { level: number; color: string }) => (
  <div className="flex gap-1 items-center">
    {[1, 2, 3, 4, 5].map((i) => (
      <div
        key={i}
        className="h-2 w-5 rounded-sm"
        style={{ backgroundColor: i <= level ? color : '#e5e7eb' }}
      />
    ))}
  </div>
);

export function ClassicTemplate({ data }: Props) {
  const {
    personal, education, experience, internship, skills, projects,
    certificates, languages, customSections, sections, accentColor,
  } = data;
  const visibleSections = [...sections].filter((s) => s.visible).sort((a, b) => a.order - b.order);

  const renderSection = (sec: (typeof visibleSections)[0]) => {
    switch (sec.type) {
      case 'experience':
        return (experience ?? []).length === 0 ? null : (
          <div key="experience" className="mb-5">
            <SectionTitle title="工作经历" color={accentColor} />
            {(experience ?? []).map((exp) => (
              <div key={exp.id} className="mb-4">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="font-bold text-gray-800">{exp.position || '职位'}</span>
                    {exp.company && <span className="text-gray-500 ml-2">· {exp.company}</span>}
                  </div>
                  <span className="text-xs text-gray-400 whitespace-nowrap ml-2">
                    {exp.startDate}{exp.startDate && ' — '}{exp.current ? '至今' : exp.endDate}
                  </span>
                </div>
                {exp.description && (
                  <p className="text-sm text-gray-600 mt-1 leading-relaxed">
                    {renderRichText(exp.description)}
                  </p>
                )}
              </div>
            ))}
          </div>
        );

      case 'internship':
        return (internship ?? []).length === 0 ? null : (
          <div key="internship" className="mb-5">
            <SectionTitle title="实习经历" color={accentColor} />
            {(internship ?? []).map((exp) => (
              <div key={exp.id} className="mb-4">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="font-bold text-gray-800">{exp.position || '岗位'}</span>
                    {exp.company && <span className="text-gray-500 ml-2">· {exp.company}</span>}
                  </div>
                  <span className="text-xs text-gray-400 whitespace-nowrap ml-2">
                    {exp.startDate}{exp.startDate && ' — '}{exp.current ? '至今' : exp.endDate}
                  </span>
                </div>
                {exp.description && (
                  <p className="text-sm text-gray-600 mt-1 leading-relaxed">
                    {renderRichText(exp.description)}
                  </p>
                )}
              </div>
            ))}
          </div>
        );

      case 'education':
        return (education ?? []).length === 0 ? null : (
          <div key="education" className="mb-5">
            <SectionTitle title="教育经历" color={accentColor} />
            {(education ?? []).map((edu) => (
              <div key={edu.id} className="mb-3">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="font-bold text-gray-800">{edu.school || '学校名称'}</span>
                    {edu.degree && <span className="text-gray-500 ml-2">· {edu.degree}</span>}
                    {edu.major && <span className="text-gray-500"> {edu.major}</span>}
                  </div>
                  <span className="text-xs text-gray-400 whitespace-nowrap ml-2">
                    {edu.startDate}{edu.startDate && ' — '}{edu.endDate}
                  </span>
                </div>
                {edu.gpa && <p className="text-sm text-gray-500">GPA: {edu.gpa}</p>}
                {edu.description && (
                  <p className="text-sm text-gray-600 mt-1">{renderRichText(edu.description)}</p>
                )}
              </div>
            ))}
          </div>
        );

      case 'skills':
        return (skills ?? []).length === 0 ? null : (
          <div key="skills" className="mb-5">
            <SectionTitle title="专业技能" color={accentColor} />
            <div className="grid grid-cols-2 gap-2">
              {(skills ?? []).map((skill) => (
                <div key={skill.id} className="flex justify-between items-center">
                  <span className="text-sm text-gray-700">{skill.name || '技能'}</span>
                  <SkillBar level={skill.level} color={accentColor} />
                </div>
              ))}
            </div>
          </div>
        );

      case 'projects':
        return (projects ?? []).length === 0 ? null : (
          <div key="projects" className="mb-5">
            <SectionTitle title="项目经历" color={accentColor} />
            {(projects ?? []).map((proj) => (
              <div key={proj.id} className="mb-3">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="font-bold text-gray-800">{proj.name || '项目名称'}</span>
                    {proj.role && <span className="text-gray-500 ml-2">· {proj.role}</span>}
                  </div>
                  <span className="text-xs text-gray-400 whitespace-nowrap ml-2">
                    {proj.startDate}{proj.startDate && ' — '}{proj.endDate}
                  </span>
                </div>
                {proj.description && (
                  <p className="text-sm text-gray-600 mt-1">{renderRichText(proj.description)}</p>
                )}
                {proj.link && <a href={proj.link} className="text-xs mt-0.5 block" style={{ color: accentColor }}>{proj.link}</a>}
              </div>
            ))}
          </div>
        );

      case 'certificates':
        return (certificates ?? []).length === 0 ? null : (
          <div key="certificates" className="mb-5">
            <SectionTitle title="证书荣誉" color={accentColor} />
            {(certificates ?? []).map((cert) => (
              <div key={cert.id} className="flex justify-between mb-1">
                <span className="text-sm text-gray-700">{cert.name}</span>
                <span className="text-xs text-gray-400">{cert.issuer} {cert.date}</span>
              </div>
            ))}
          </div>
        );

      case 'languages':
        return (languages ?? []).length === 0 ? null : (
          <div key="languages" className="mb-5">
            <SectionTitle title="语言能力" color={accentColor} />
            <div className="flex flex-wrap gap-3">
              {(languages ?? []).map((lang) => (
                <span key={lang.id} className="text-sm text-gray-700">
                  {lang.name}{lang.level && ` (${lang.level})`}
                </span>
              ))}
            </div>
          </div>
        );

      case 'custom': {
        const cs = (customSections ?? []).find((c) => c.id === sec.id);
        if (!cs || !cs.content) return null;
        return (
          <div key={sec.id} className="mb-5">
            <SectionTitle title={cs.title || '自定义板块'} color={accentColor} />
            <div className="text-sm text-gray-600 leading-relaxed">
              {renderRichText(cs.content)}
            </div>
          </div>
        );
      }

      default:
        return null;
    }
  };

  return (
    <div data-template="classic" className="bg-white w-full h-full p-10 font-sans text-[13px] leading-relaxed" style={{ minHeight: '297mm' }}>
      {/* Header */}
      <div className="flex items-start gap-5 mb-8 pb-6 border-b" style={{ borderColor: accentColor }}>
        {personal.avatar && (
          <img src={personal.avatar} alt="avatar" className="w-20 h-20 rounded-full object-cover flex-shrink-0" />
        )}
        <div className="flex-1">
          <h1 className="text-3xl font-bold text-gray-900">{personal.name || '姓名'}</h1>
          {personal.title && <p className="text-base mt-1 font-medium" style={{ color: accentColor }}>{personal.title}</p>}
          <div className="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-gray-500">
            {personal.email    && <span>✉ {personal.email}</span>}
            {personal.phone    && <span>☎ {personal.phone}</span>}
            {personal.location && <span>📍 {personal.location}</span>}
            {personal.website  && <span>🔗 {personal.website}</span>}
          </div>
          {personal.summary && (
            <p className="mt-3 text-sm text-gray-600 leading-relaxed">
              {renderRichText(personal.summary)}
            </p>
          )}
        </div>
      </div>

      {/* Sections */}
      {visibleSections.filter(s => s.type !== 'personal').map((sec) => renderSection(sec))}
    </div>
  );
}
