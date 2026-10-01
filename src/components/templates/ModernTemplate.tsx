import { renderRichText } from '../../utils/textRenderer';
import type { ResumeData } from '../../types/resume';

interface Props {
  data: ResumeData;
}

export function ModernTemplate({ data }: Props) {
  const {
    personal, education, experience, internship, skills, projects,
    certificates, languages, customSections, sections, accentColor,
  } = data;

  const visibleSections = [...sections]
    .filter((s) => s.visible && s.type !== 'personal')
    .sort((a, b) => a.order - b.order);

  const SectionTitle = ({ title }: { title: string }) => (
    <div className="flex items-center gap-2 mb-4">
      <div className="w-1 h-5 rounded-full" style={{ backgroundColor: accentColor }} />
      <h2 className="text-sm font-bold text-gray-700 tracking-wider uppercase">{title}</h2>
    </div>
  );

  /** 经验类板块（工作/实习）渲染 */
  const renderExpSection = (
    title: string,
    list: typeof experience,
    keyPrefix: string
  ) =>
    (list ?? []).length === 0 ? null : (
      <div key={keyPrefix} className="mb-6">
        <SectionTitle title={title} />
        <div className="space-y-4">
          {(list ?? []).map((exp) => (
            <div key={exp.id} className="relative pl-4 border-l-2" style={{ borderColor: accentColor + '40' }}>
              <div
                className="absolute -left-[5px] top-1.5 w-2 h-2 rounded-full"
                style={{ backgroundColor: accentColor }}
              />
              <div className="flex justify-between items-start">
                <div>
                  <p className="font-semibold text-gray-800">{exp.position || '职位'}</p>
                  <p className="text-xs" style={{ color: accentColor }}>{exp.company}</p>
                </div>
                <span className="text-xs text-gray-400 bg-gray-100 rounded-full px-2 py-0.5 whitespace-nowrap ml-2">
                  {exp.startDate}{exp.startDate && ' – '}{exp.current ? '至今' : exp.endDate}
                </span>
              </div>
              {exp.description && (
                <p className="text-xs text-gray-600 mt-1.5 leading-relaxed">
                  {renderRichText(exp.description)}
                </p>
              )}
            </div>
          ))}
        </div>
      </div>
    );

  const renderSection = (sec: (typeof visibleSections)[0]) => {
    switch (sec.type) {
      case 'experience':
        return renderExpSection('工作经历', experience, 'experience');
      case 'internship':
        return renderExpSection('实习经历', internship, 'internship');

      case 'education':
        return (education ?? []).length === 0 ? null : (
          <div key="education" className="mb-6">
            <SectionTitle title="教育经历" />
            {(education ?? []).map((edu) => (
              <div key={edu.id} className="flex justify-between items-start mb-3">
                <div>
                  <p className="font-semibold text-gray-800">{edu.school || '学校名称'}</p>
                  <p className="text-xs text-gray-500">{edu.degree} {edu.major}</p>
                  {edu.gpa && <p className="text-xs text-gray-400">GPA: {edu.gpa}</p>}
                  {edu.description && (
                    <p className="text-xs text-gray-500 mt-0.5">{renderRichText(edu.description)}</p>
                  )}
                </div>
                <span className="text-xs text-gray-400 whitespace-nowrap ml-2">
                  {edu.startDate}{edu.startDate && ' – '}{edu.endDate}
                </span>
              </div>
            ))}
          </div>
        );

      case 'projects':
        return (projects ?? []).length === 0 ? null : (
          <div key="projects" className="mb-6">
            <SectionTitle title="项目经历" />
            <div className="space-y-3">
              {(projects ?? []).map((proj) => (
                <div key={proj.id} className="rounded-lg p-3" style={{ backgroundColor: accentColor + '08' }}>
                  <div className="flex justify-between items-center">
                    <p className="font-semibold text-gray-800">{proj.name || '项目名称'}</p>
                    <span className="text-xs text-gray-400 whitespace-nowrap ml-2">
                      {proj.startDate}{proj.startDate && ' – '}{proj.endDate}
                    </span>
                  </div>
                  {proj.role && <p className="text-xs mt-0.5" style={{ color: accentColor }}>{proj.role}</p>}
                  {proj.description && (
                    <p className="text-xs text-gray-600 mt-1">{renderRichText(proj.description)}</p>
                  )}
                  {proj.link && <a href={proj.link} className="text-xs mt-0.5 block" style={{ color: accentColor }}>{proj.link}</a>}
                </div>
              ))}
            </div>
          </div>
        );

      case 'certificates':
        return (certificates ?? []).length === 0 ? null : (
          <div key="certificates" className="mb-6">
            <SectionTitle title="证书荣誉" />
            {(certificates ?? []).map((cert) => (
              <div key={cert.id} className="flex justify-between mb-1">
                <span className="text-sm text-gray-700">{cert.name}</span>
                <span className="text-xs text-gray-400">{cert.issuer} {cert.date}</span>
              </div>
            ))}
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

  return (
    <div data-template="modern" className="bg-white w-full h-full font-sans text-[13px]" style={{ minHeight: '297mm' }}>
      <div className="flex h-full">
        {/* Sidebar */}
        <div className="w-64 flex-shrink-0 p-8 text-white" style={{ backgroundColor: accentColor, minHeight: '297mm' }}>
          {personal.avatar && (
            <div className="mb-4 flex justify-center">
              <img src={personal.avatar} alt="avatar" className="w-24 h-24 rounded-full object-cover border-4 border-white/30" />
            </div>
          )}
          <h1 className="text-xl font-bold text-white">{personal.name || '姓名'}</h1>
          {personal.title && <p className="text-sm mt-1 text-white/80">{personal.title}</p>}

          <div className="mt-5 space-y-2">
            {personal.email    && <div className="flex items-center gap-2 text-xs text-white/80"><span>✉</span><span className="break-all">{personal.email}</span></div>}
            {personal.phone    && <div className="flex items-center gap-2 text-xs text-white/80"><span>☎</span><span>{personal.phone}</span></div>}
            {personal.location && <div className="flex items-center gap-2 text-xs text-white/80"><span>📍</span><span>{personal.location}</span></div>}
            {personal.website  && <div className="flex items-center gap-2 text-xs text-white/80"><span>🔗</span><span className="break-all">{personal.website}</span></div>}
          </div>

          {(skills ?? []).length > 0 && (
            <div className="mt-6">
              <h3 className="text-xs font-bold tracking-widest uppercase text-white/60 mb-2">技能</h3>
              <div className="space-y-2">
                {(skills ?? []).map((skill) => (
                  <div key={skill.id}>
                    <div className="flex justify-between text-xs text-white/80 mb-0.5">
                      <span>{skill.name}</span>
                    </div>
                    <div className="h-1.5 bg-white/20 rounded-full overflow-hidden">
                      <div className="h-full rounded-full bg-white/80" style={{ width: `${(skill.level / 5) * 100}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {(languages ?? []).length > 0 && (
            <div className="mt-6">
              <h3 className="text-xs font-bold tracking-widest uppercase text-white/60 mb-2">语言</h3>
              {(languages ?? []).map((lang) => (
                <div key={lang.id} className="flex justify-between text-xs text-white/80 mb-1">
                  <span>{lang.name}</span>
                  <span className="text-white/50">{lang.level}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Main Content */}
        <div className="flex-1 p-8">
          {personal.summary && (
            <div className="mb-6 p-4 rounded-lg" style={{ backgroundColor: accentColor + '0D' }}>
              <p className="text-sm text-gray-600 leading-relaxed">{renderRichText(personal.summary)}</p>
            </div>
          )}
          {visibleSections
            .filter((s) => !['personal', 'skills', 'languages'].includes(s.type))
            .map((sec) => renderSection(sec))}
        </div>
      </div>
    </div>
  );
}
