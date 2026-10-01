import { renderRichText } from '../../utils/textRenderer';
import type { ResumeData } from '../../types/resume';
import type { SchemaSectionType, TemplateSchema } from '../../types/schema';

interface Props {
  data: ResumeData;
  schema: TemplateSchema;
}

const DEFAULT_TITLES: Record<string, string> = {
  summary: '个人简介',
  experience: '工作经历',
  internship: '实习经历',
  education: '教育经历',
  skills: '专业技能',
  projects: '项目经历',
  certificates: '证书荣誉',
  languages: '语言能力',
};

/** 用户自定义模板的通用渲染（schema.sections 驱动）。 */
export function CustomSchemaTemplate({ data, schema }: Props) {
  const { personal, education, experience, internship, skills, projects,
    certificates, languages, customSections, accentColor } = data;
  const color = accentColor || '#2563eb';
  const sections = (schema.sections ?? []).filter((s) => s.show !== false && s.type !== 'personal');
  const twoCol = schema.columns === 2;
  const theme = schema.theme ?? {};
  const titleStyle = theme.titleStyle ?? 'bar';
  const sectionStyle = theme.sectionStyle ?? 'plain';
  const showAvatar = theme.showAvatar !== false;
  const showContacts = theme.showContacts !== false;
  const headerStyle = theme.headerStyle ?? 'left';
  const secCls = sectionStyle === 'card' ? 'mb-5 rounded-lg p-3 bg-gray-50' : 'mb-5';

  const title = (sec: { title?: string; type: SchemaSectionType }) =>
    sec.title || DEFAULT_TITLES[sec.type] || '';

  const SectionTitle = ({ text }: { text: string }) => {
    if (titleStyle === 'underline') {
      return (
        <h3 className="text-xs font-bold tracking-widest uppercase mb-3 pb-1 border-b" style={{ color, borderColor: color + '40' }}>
          {text}
        </h3>
      );
    }
    if (titleStyle === 'capsule') {
      return (
        <div className="mb-3">
          <span className="inline-block text-xs font-bold tracking-widest uppercase px-2.5 py-0.5 rounded-full text-white" style={{ backgroundColor: color }}>
            {text}
          </span>
        </div>
      );
    }
    if (titleStyle === 'bar') {
      return (
        <div className="flex items-center gap-2 mb-3">
          <div className="w-1 h-4 rounded-full" style={{ backgroundColor: color }} />
          <h3 className="text-xs font-bold tracking-widest uppercase" style={{ color }}>{text}</h3>
        </div>
      );
    }
    return (
      <h3 className="text-xs font-bold tracking-widest uppercase mb-3" style={{ color }}>{text}</h3>
    );
  };

  const renderSection = (sec: { id: string; type: SchemaSectionType; title?: string }) => {
    const t = title(sec);
    switch (sec.type) {
      case 'summary':
        return personal.summary ? (
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
            <p className="text-xs text-gray-600 leading-relaxed">{renderRichText(personal.summary)}</p>
          </div>
        ) : null;

      case 'experience':
      case 'internship': {
        const list = sec.type === 'experience' ? experience : internship;
        return (list ?? []).length === 0 ? null : (
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
            {(list ?? []).map((item) => (
              <div key={item.id} className="mb-3">
                <div className="flex justify-between items-baseline">
                  <span className="font-semibold text-gray-800 text-sm">{item.position || '职位'}</span>
                  <span className="text-xs text-gray-400 ml-2 whitespace-nowrap">
                    {item.startDate}{item.startDate && ' – '}{item.current ? '至今' : item.endDate}
                  </span>
                </div>
                {item.company && <p className="text-xs mt-0.5" style={{ color }}>{item.company}</p>}
                {item.description && (
                  <p className="text-xs text-gray-500 mt-1 leading-relaxed">{renderRichText(item.description)}</p>
                )}
              </div>
            ))}
          </div>
        );
      }

      case 'education':
        return (education ?? []).length === 0 ? null : (
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
            {(education ?? []).map((edu) => (
              <div key={edu.id} className="mb-2">
                <div className="flex justify-between items-baseline">
                  <span className="font-semibold text-gray-800 text-sm">{edu.school || '学校名称'}</span>
                  <span className="text-xs text-gray-400 ml-2 whitespace-nowrap">
                    {edu.startDate}{edu.startDate && ' – '}{edu.endDate}
                  </span>
                </div>
                <p className="text-xs text-gray-500 mt-0.5">{edu.degree} {edu.major}</p>
              </div>
            ))}
          </div>
        );

      case 'skills':
        return (skills ?? []).length === 0 ? null : (
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
            <div className="flex flex-wrap gap-1.5">
              {(skills ?? []).map((s) => (
                <span key={s.id} className="text-xs px-2 py-0.5 border rounded text-gray-600"
                  style={{ borderColor: color + '60' }}>{s.name || '技能'}</span>
              ))}
            </div>
          </div>
        );

      case 'projects':
        return (projects ?? []).length === 0 ? null : (
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
            {(projects ?? []).map((proj) => (
              <div key={proj.id} className="mb-3">
                <div className="flex justify-between items-baseline">
                  <span className="font-semibold text-gray-800 text-sm">{proj.name || '项目名称'}</span>
                  <span className="text-xs text-gray-400 ml-2 whitespace-nowrap">
                    {proj.startDate}{proj.startDate && ' – '}{proj.endDate}
                  </span>
                </div>
                {proj.role && <p className="text-xs mt-0.5" style={{ color }}>{proj.role}</p>}
                {proj.description && (
                  <p className="text-xs text-gray-500 mt-1">{renderRichText(proj.description)}</p>
                )}
              </div>
            ))}
          </div>
        );

      case 'certificates':
        return (certificates ?? []).length === 0 ? null : (
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
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
          <div key={sec.id} className={secCls}>
            <SectionTitle text={t} />
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
          <div key={sec.id} className={secCls}>
            <SectionTitle text={cs.title || '自定义板块'} />
            <div className="text-xs text-gray-600 leading-relaxed">{renderRichText(cs.content)}</div>
          </div>
        );
      }

      default:
        return null;
    }
  };

  const leftTypes: SchemaSectionType[] = ['experience', 'internship', 'projects', 'custom'];
  const isLeft = (s: { column?: 'left' | 'right'; type: SchemaSectionType }) => {
    if (s.column === 'left') return true;
    if (s.column === 'right') return false;
    return leftTypes.includes(s.type);
  };
  const left = sections.filter(isLeft);
  const right = sections.filter((s) => !isLeft(s));

  return (
    <div data-template="custom" className="bg-white w-full h-full p-10 font-sans text-[13px] leading-relaxed" style={{ minHeight: '297mm' }}>
      {/* 头部 */}
      <div
        className={headerStyle === 'center' ? 'flex flex-col items-center text-center mb-6 pb-6 border-b' : 'flex items-start gap-5 mb-6 pb-6 border-b'}
        style={{ borderColor: color }}
      >
        {showAvatar && personal.avatar && (
          <img
            src={personal.avatar}
            alt="avatar"
            className={headerStyle === 'center' ? 'w-20 h-20 rounded-full object-cover mb-3' : 'w-20 h-20 rounded-full object-cover flex-shrink-0'}
          />
        )}
        <div className={headerStyle === 'center' ? '' : 'flex-1'}>
          <h1 className="text-3xl font-bold text-gray-900">{personal.name || '姓名'}</h1>
          {personal.title && <p className="text-base mt-1 font-medium" style={{ color }}>{personal.title}</p>}
          {showContacts && (
            <div className={`flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-gray-500 ${headerStyle === 'center' ? 'justify-center' : ''}`}>
              {personal.email && <span>✉ {personal.email}</span>}
              {personal.phone && <span>☎ {personal.phone}</span>}
              {personal.location && <span>📍 {personal.location}</span>}
              {personal.website && <span>🔗 {personal.website}</span>}
            </div>
          )}
        </div>
      </div>

      {twoCol ? (
        <div className="flex gap-8">
          <div className="flex-1">{left.map(renderSection)}</div>
          <div className="w-44 flex-shrink-0">{right.map(renderSection)}</div>
        </div>
      ) : (
        sections.map(renderSection)
      )}
    </div>
  );
}
