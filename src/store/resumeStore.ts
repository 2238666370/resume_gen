import { create } from 'zustand';
import type {
  ResumeData,
  TemplateId,
  EducationItem,
  ExperienceItem,
  InternshipItem,
  SkillItem,
  ProjectItem,
  CertificateItem,
  LanguageItem,
  CustomSection,
  ResumeSection,
  PersonalInfo,
} from '../types/resume';

const defaultSections: ResumeSection[] = [
  { id: 'personal',    type: 'personal',    visible: true,  order: 0 },
  { id: 'experience',  type: 'experience',  visible: true,  order: 1 },
  { id: 'internship',  type: 'internship',  visible: false, order: 2 },
  { id: 'education',   type: 'education',   visible: true,  order: 3 },
  { id: 'skills',      type: 'skills',      visible: true,  order: 4 },
  { id: 'projects',    type: 'projects',    visible: true,  order: 5 },
  { id: 'certificates',type: 'certificates',visible: false, order: 6 },
  { id: 'languages',   type: 'languages',   visible: false, order: 7 },
];

const defaultData: ResumeData = {
  title: '未命名简历',
  templateId: 'classic',
  accentColor: '#2563eb',
  personal: {
    name: '', title: '', email: '', phone: '',
    location: '', website: '', avatar: '', summary: '',
  },
  education: [],
  experience: [],
  internship: [],
  skills: [],
  projects: [],
  certificates: [],
  languages: [],
  customSections: [],
  sections: defaultSections,
};

interface ResumeStore {
  data: ResumeData;
  setTemplate:         (id: TemplateId) => void;
  setAccentColor:      (color: string) => void;
  updatePersonal:      (personal: Partial<PersonalInfo>) => void;
  addEducation:        () => void;
  updateEducation:     (id: string, item: Partial<EducationItem>) => void;
  removeEducation:     (id: string) => void;
  addExperience:       () => void;
  updateExperience:    (id: string, item: Partial<ExperienceItem>) => void;
  removeExperience:    (id: string) => void;
  addInternship:       () => void;
  updateInternship:    (id: string, item: Partial<InternshipItem>) => void;
  removeInternship:    (id: string) => void;
  addSkill:            () => void;
  updateSkill:         (id: string, item: Partial<SkillItem>) => void;
  removeSkill:         (id: string) => void;
  addProject:          () => void;
  updateProject:       (id: string, item: Partial<ProjectItem>) => void;
  removeProject:       (id: string) => void;
  addCertificate:      () => void;
  updateCertificate:   (id: string, item: Partial<CertificateItem>) => void;
  removeCertificate:   (id: string) => void;
  addLanguage:         () => void;
  updateLanguage:      (id: string, item: Partial<LanguageItem>) => void;
  removeLanguage:      (id: string) => void;
  /** 新增自定义板块（同时创建对应 section 条目） */
  addCustomSection:    () => void;
  updateCustomSection: (id: string, item: Partial<CustomSection>) => void;
  removeCustomSection: (id: string) => void;
  setSections:         (sections: ResumeSection[]) => void;
  toggleSection:       (id: string) => void;
  resetData:           () => void;
  /** 从 JSON 导入完整数据（用于恢复备份） */
  loadData:            (data: ResumeData) => void;
}

const uid = () => Math.random().toString(36).slice(2, 9);

const newExp = (): ExperienceItem => ({
  id: uid(), company: '', position: '',
  startDate: '', endDate: '', current: false, description: '',
});

export const useResumeStore = create<ResumeStore>()(
  (set) => ({
    data: defaultData,

      setTemplate:    (id)    => set((s) => ({ data: { ...s.data, templateId: id } })),
      setAccentColor: (color) => set((s) => ({ data: { ...s.data, accentColor: color } })),
      updatePersonal: (p)     => set((s) => ({ data: { ...s.data, personal: { ...s.data.personal, ...p } } })),

      // Education
      addEducation: () => set((s) => ({
        data: { ...s.data, education: [...s.data.education, {
          id: uid(), school: '', degree: '', major: '',
          startDate: '', endDate: '', gpa: '', description: '',
        }]},
      })),
      updateEducation: (id, item) => set((s) => ({
        data: { ...s.data, education: s.data.education.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeEducation: (id) => set((s) => ({
        data: { ...s.data, education: s.data.education.filter(e => e.id !== id) },
      })),

      // Experience
      addExperience: () => set((s) => ({
        data: { ...s.data, experience: [...s.data.experience, newExp()] },
      })),
      updateExperience: (id, item) => set((s) => ({
        data: { ...s.data, experience: s.data.experience.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeExperience: (id) => set((s) => ({
        data: { ...s.data, experience: s.data.experience.filter(e => e.id !== id) },
      })),

      // Internship（与工作经历同构）
      addInternship: () => set((s) => ({
        data: { ...s.data, internship: [...(s.data.internship ?? []), newExp()] },
      })),
      updateInternship: (id, item) => set((s) => ({
        data: { ...s.data, internship: (s.data.internship ?? []).map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeInternship: (id) => set((s) => ({
        data: { ...s.data, internship: (s.data.internship ?? []).filter(e => e.id !== id) },
      })),

      // Skills
      addSkill: () => set((s) => ({
        data: { ...s.data, skills: [...s.data.skills, { id: uid(), name: '', level: 3 }] },
      })),
      updateSkill: (id, item) => set((s) => ({
        data: { ...s.data, skills: s.data.skills.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeSkill: (id) => set((s) => ({
        data: { ...s.data, skills: s.data.skills.filter(e => e.id !== id) },
      })),

      // Projects
      addProject: () => set((s) => ({
        data: { ...s.data, projects: [...s.data.projects, {
          id: uid(), name: '', role: '', startDate: '', endDate: '', description: '', link: '',
        }]},
      })),
      updateProject: (id, item) => set((s) => ({
        data: { ...s.data, projects: s.data.projects.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeProject: (id) => set((s) => ({
        data: { ...s.data, projects: s.data.projects.filter(e => e.id !== id) },
      })),

      // Certificates
      addCertificate: () => set((s) => ({
        data: { ...s.data, certificates: [...s.data.certificates, { id: uid(), name: '', issuer: '', date: '', link: '' }] },
      })),
      updateCertificate: (id, item) => set((s) => ({
        data: { ...s.data, certificates: s.data.certificates.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeCertificate: (id) => set((s) => ({
        data: { ...s.data, certificates: s.data.certificates.filter(e => e.id !== id) },
      })),

      // Languages
      addLanguage: () => set((s) => ({
        data: { ...s.data, languages: [...s.data.languages, { id: uid(), name: '', level: '' }] },
      })),
      updateLanguage: (id, item) => set((s) => ({
        data: { ...s.data, languages: s.data.languages.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeLanguage: (id) => set((s) => ({
        data: { ...s.data, languages: s.data.languages.filter(e => e.id !== id) },
      })),

      // Custom Sections — section.id === customSection.id
      addCustomSection: () => set((s) => {
        const newId = uid();
        return {
          data: {
            ...s.data,
            customSections: [
              ...s.data.customSections,
              { id: newId, title: '自定义板块', content: '' },
            ],
            sections: [
              ...s.data.sections,
              { id: newId, type: 'custom', customId: newId, visible: true, order: s.data.sections.length },
            ],
          },
        };
      }),
      updateCustomSection: (id, item) => set((s) => ({
        data: { ...s.data, customSections: s.data.customSections.map(e => e.id === id ? { ...e, ...item } : e) },
      })),
      removeCustomSection: (id) => set((s) => ({
        data: {
          ...s.data,
          customSections: s.data.customSections.filter(e => e.id !== id),
          sections: s.data.sections.filter(sec => sec.id !== id),
        },
      })),

      setSections:    (sections) => set((s) => ({ data: { ...s.data, sections } })),
      toggleSection:  (id)       => set((s) => ({
        data: {
          ...s.data,
          sections: s.data.sections.map(sec => sec.id === id ? { ...sec, visible: !sec.visible } : sec),
        },
      })),
      resetData: () => set({ data: defaultData }),
      loadData: (data) => set({ data }),
  })
);
