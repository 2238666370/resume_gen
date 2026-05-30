export type TemplateId = 'classic' | 'modern' | 'minimal';

export interface PersonalInfo {
  name: string;
  title: string;
  email: string;
  phone: string;
  location: string;
  website: string;
  avatar: string;
  summary: string;
}

export interface EducationItem {
  id: string;
  school: string;
  degree: string;
  major: string;
  startDate: string;
  endDate: string;
  gpa: string;
  description: string;
}

export interface ExperienceItem {
  id: string;
  company: string;
  position: string;
  startDate: string;
  endDate: string;
  current: boolean;
  description: string;
}

/** 实习经历结构与工作经历相同 */
export type InternshipItem = ExperienceItem;

export interface SkillItem {
  id: string;
  name: string;
  level: number; // 1-5
}

export interface ProjectItem {
  id: string;
  name: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  link: string;
}

export interface CertificateItem {
  id: string;
  name: string;
  issuer: string;
  date: string;
  link: string;
}

export interface LanguageItem {
  id: string;
  name: string;
  level: string;
}

/** 自定义板块：标题和富文本内容均可自定义 */
export interface CustomSection {
  id: string;
  title: string;
  content: string;
}

export type SectionType =
  | 'personal'
  | 'education'
  | 'experience'
  | 'internship'
  | 'skills'
  | 'projects'
  | 'certificates'
  | 'languages'
  | 'custom';

export interface ResumeSection {
  id: string;
  type: SectionType;
  /** 自定义板块用此字段关联 CustomSection.id */
  customId?: string;
  visible: boolean;
  order: number;
}

export interface ResumeData {
  templateId: TemplateId;
  accentColor: string;
  personal: PersonalInfo;
  education: EducationItem[];
  experience: ExperienceItem[];
  internship: InternshipItem[];
  skills: SkillItem[];
  projects: ProjectItem[];
  certificates: CertificateItem[];
  languages: LanguageItem[];
  customSections: CustomSection[];
  sections: ResumeSection[];
}
