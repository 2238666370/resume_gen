import { useRef, useState } from 'react';
import { useResumeStore } from '../store/resumeStore';
import { exportToPDF, exportToPNG, exportJSON, parseResumeJSON } from '../utils/export';

const TEMPLATES: { id: 'classic' | 'modern' | 'minimal'; name: string; desc: string }[] = [
  { id: 'classic', name: '经典', desc: '传统横版，简洁专业' },
  { id: 'modern', name: '现代', desc: '侧边栏布局，时尚个性' },
  { id: 'minimal', name: '简约', desc: '极简双栏，干净清爽' },
];

const COLORS = [
  '#2563eb', '#7c3aed', '#db2777', '#dc2626',
  '#ea580c', '#16a34a', '#0891b2', '#374151',
];

interface Props {
  resumeRef: React.RefObject<HTMLDivElement | null>;
}

export function Toolbar({ resumeRef }: Props) {
  const { data, setTemplate, setAccentColor, resetData, loadData } = useResumeStore();
  const [exporting, setExporting] = useState<'pdf' | 'png' | null>(null);
  const [showTemplates, setShowTemplates] = useState(false);
  const [showColors, setShowColors] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const colorInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleExport = async (type: 'pdf' | 'png') => {
    if (!resumeRef.current) {
      showToast('未找到简历内容，请刷新页面后重试', '#ef4444');
      return;
    }
    setExporting(type);
    try {
      if (type === 'pdf') {
        await exportToPDF(resumeRef.current, data.personal.name || 'resume');
        showToast('📄 在打印对话框中选择「另存为 PDF」即可导出，支持搜索和复制', '#3b82f6', 4000);
      } else {
        await exportToPNG(resumeRef.current, data.personal.name || 'resume');
        showToast('✓ 图片已导出，查看浏览器下载', '#22c55e');
      }
    } catch (err: any) {
      console.error('导出失败:', err);
      showToast(`导出失败: ${err.message || '未知错误，请检查浏览器控制台'}`, '#ef4444');
    } finally {
      setExporting(null);
    }
  };

  const handleSaveJSON = () => {
    exportJSON(data, data.personal.name || 'resume');
    showToast('✓ 已保存为 JSON 文件');
  };

  const handleImport = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const imported = await parseResumeJSON(file);
      loadData(imported);
      showToast('✓ 简历数据已导入', '#22c55e');
    } catch (err: any) {
      showToast(err.message || '导入失败', '#ef4444');
    } finally {
      // 清空 input 以允许重复导入同一文件
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const showToast = (text: string, bg = '#22c55e', duration = 2000) => {
    const toast = document.createElement('div');
    toast.textContent = text;
    toast.style.cssText = `position:fixed;top:20px;left:50%;transform:translateX(-50%);background:${bg};color:white;padding:8px 20px;border-radius:8px;font-size:14px;z-index:9999;pointer-events:none;`;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), duration);
  };

  return (
    <header className="h-14 bg-white border-b border-gray-200 flex items-center px-4 gap-3 shadow-sm z-20 relative">
      {/* Hidden file input for import */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".json"
        className="hidden"
        onChange={handleFileChange}
      />

      {/* Logo */}
      <div className="flex items-center gap-2 mr-2">
        <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center text-white text-xs font-bold">R</div>
        <span className="font-semibold text-gray-800 text-sm">简历生成器v1.0</span>
      </div>

      <div className="w-px h-6 bg-gray-200" />

      {/* Template Switcher */}
      <div className="relative">
        <button
          onClick={() => { setShowTemplates(!showTemplates); setShowColors(false); }}
          className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
            <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
          </svg>
          模板
          <span className="text-xs text-blue-600 font-medium">
            {TEMPLATES.find(t => t.id === data.templateId)?.name}
          </span>
        </button>
        {showTemplates && (
          <div className="absolute top-10 left-0 bg-white border border-gray-200 rounded-xl shadow-xl p-3 z-50 flex gap-3 w-72">
            {TEMPLATES.map((t) => (
              <button
                key={t.id}
                onClick={() => { setTemplate(t.id); setShowTemplates(false); }}
                className={`flex-1 rounded-lg border-2 p-2 text-center transition-all ${
                  data.templateId === t.id ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <div className="w-full aspect-[3/4] bg-gray-100 rounded mb-1.5 overflow-hidden flex flex-col p-1 gap-0.5">
                  <div className="h-3 rounded" style={{ backgroundColor: t.id === 'modern' ? '#2563eb' : '#e5e7eb' }} />
                  <div className="flex gap-0.5 flex-1">
                    {t.id === 'modern' && <div className="w-5 rounded" style={{ backgroundColor: '#2563eb22' }} />}
                    <div className="flex-1 flex flex-col gap-0.5 pt-0.5">
                      <div className="h-1 bg-gray-200 rounded w-full" />
                      <div className="h-1 bg-gray-200 rounded w-4/5" />
                      <div className="h-1 bg-gray-200 rounded w-3/5 mt-0.5" />
                      <div className="h-1 bg-gray-200 rounded w-full mt-0.5" />
                      <div className="h-1 bg-gray-200 rounded w-4/5" />
                    </div>
                  </div>
                </div>
                <p className="text-xs font-medium text-gray-700">{t.name}</p>
                <p className="text-[10px] text-gray-400">{t.desc}</p>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Color Picker */}
      <div className="relative">
        <button
          onClick={() => { setShowColors(!showColors); setShowTemplates(false); }}
          className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors"
        >
          <div className="w-4 h-4 rounded-full border border-gray-300" style={{ backgroundColor: data.accentColor }} />
          配色
        </button>
        {showColors && (
          <div className="absolute top-10 left-0 bg-white border border-gray-200 rounded-xl shadow-xl p-3 z-50 w-48">
            <div className="grid grid-cols-4 gap-2 mb-3">
              {COLORS.map((c) => (
                <button
                  key={c}
                  onClick={() => { setAccentColor(c); setShowColors(false); }}
                  className={`w-8 h-8 rounded-full transition-transform hover:scale-110 ${data.accentColor === c ? 'ring-2 ring-offset-2 ring-blue-500' : ''}`}
                  style={{ backgroundColor: c }}
                />
              ))}
            </div>
            <div className="flex items-center gap-2">
              <label className="text-xs text-gray-500">自定义:</label>
              <input
                ref={colorInputRef}
                type="color"
                value={data.accentColor}
                onChange={(e) => setAccentColor(e.target.value)}
                className="w-8 h-8 rounded cursor-pointer border-0 p-0"
              />
              <span className="text-xs text-gray-400 font-mono">{data.accentColor}</span>
            </div>
          </div>
        )}
      </div>

      <div className="flex-1" />

      {/* Import JSON */}
      <button
        onClick={handleImport}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        导入
      </button>

      {/* Save JSON */}
      <button
        onClick={handleSaveJSON}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 transition-colors"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/>
          <polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/>
        </svg>
        保存
      </button>

      {/* Reset */}
      {showResetConfirm ? (
        <div className="flex items-center gap-2 text-sm">
          <span className="text-red-500">确定清空所有内容？</span>
          <button onClick={() => { resetData(); setShowResetConfirm(false); }} className="px-3 py-1 bg-red-500 text-white rounded-md hover:bg-red-600 text-xs">确定</button>
          <button onClick={() => setShowResetConfirm(false)} className="px-3 py-1 bg-gray-200 text-gray-600 rounded-md hover:bg-gray-300 text-xs">取消</button>
        </div>
      ) : (
        <button
          onClick={() => setShowResetConfirm(true)}
          className="px-3 py-1.5 text-sm text-gray-400 hover:text-red-400 hover:bg-red-50 rounded-md transition-colors"
        >
          重置
        </button>
      )}

      {/* Export PNG */}
      <button
        onClick={() => handleExport('png')}
        disabled={!!exporting}
        className="flex items-center gap-1.5 px-4 py-1.5 text-sm bg-green-500 text-white rounded-md hover:bg-green-600 transition-colors disabled:opacity-60"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
          <polyline points="21 15 16 10 5 21"/>
        </svg>
        {exporting === 'png' ? '导出中...' : '导出图片'}
      </button>

      {/* Export PDF */}
      <button
        onClick={() => handleExport('pdf')}
        disabled={!!exporting}
        className="flex items-center gap-1.5 px-4 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-60"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
          <polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/>
          <line x1="9" y1="15" x2="15" y2="15"/>
        </svg>
        {exporting === 'pdf' ? '导出中...' : '导出 PDF'}
      </button>

      {/* Click outside to close */}
      {(showTemplates || showColors) && (
        <div
          className="fixed inset-0 z-40"
          onClick={() => { setShowTemplates(false); setShowColors(false); }}
        />
      )}
    </header>
  );
}
