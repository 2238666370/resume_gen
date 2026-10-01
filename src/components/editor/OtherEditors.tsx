import { useResumeStore } from '../../store/resumeStore';

export function CertificatesEditor() {
  const { data, addCertificate, updateCertificate, removeCertificate } = useResumeStore();
  const { certificates } = data;

  return (
    <div>
      {certificates.map((cert) => (
        <div key={cert.id} className="mb-3 p-3 bg-gray-50 rounded-lg border border-gray-100 group">
          <div className="flex gap-3 mb-2">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">证书名称</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={cert.name}
                placeholder="如：CET-6"
                onChange={(e) => updateCertificate(cert.id, { name: e.target.value })}
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">颁发机构</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={cert.issuer}
                placeholder="教育部"
                onChange={(e) => updateCertificate(cert.id, { issuer: e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-3 items-end">
            <div className="flex-1">
              <label className="block text-xs text-gray-500 mb-1">获得时间</label>
              <input
                className="w-full border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
                value={cert.date}
                placeholder="2023.06"
                onChange={(e) => updateCertificate(cert.id, { date: e.target.value })}
              />
            </div>
            <button
              onClick={() => removeCertificate(cert.id)}
              className="mb-1.5 text-xs text-red-400 opacity-0 group-hover:opacity-100 transition-opacity hover:text-red-600 pb-1"
            >
              删除
            </button>
          </div>
        </div>
      ))}
      <button
        onClick={addCertificate}
        className="w-full py-2 border-2 border-dashed border-gray-200 rounded-lg text-sm text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
      >
        + 添加证书/荣誉
      </button>
    </div>
  );
}

export function LanguagesEditor() {
  const { data, addLanguage, updateLanguage, removeLanguage } = useResumeStore();
  const { languages } = data;

  return (
    <div>
      <div className="space-y-2 mb-3">
        {languages.map((lang) => (
          <div key={lang.id} className="flex gap-3 group items-center">
            <input
              className="flex-1 border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
              value={lang.name}
              placeholder="语言，如：英语"
              onChange={(e) => updateLanguage(lang.id, { name: e.target.value })}
            />
            <input
              className="w-28 border border-gray-200 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:border-blue-400"
              value={lang.level}
              placeholder="CET-6 / 流利"
              onChange={(e) => updateLanguage(lang.id, { level: e.target.value })}
            />
            <button
              onClick={() => removeLanguage(lang.id)}
              className="text-xs text-red-300 opacity-0 group-hover:opacity-100 transition-opacity hover:text-red-500"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
      <button
        onClick={addLanguage}
        className="w-full py-2 border-2 border-dashed border-gray-200 rounded-lg text-sm text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
      >
        + 添加语言
      </button>
    </div>
  );
}
