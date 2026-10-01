import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useResumeStore } from '../store/resumeStore';
import { exportToPDF, exportToPNG, exportJSON, parseResumeJSON } from '../utils/export';
import { ShareModal } from './ShareModal';
import { AiSuggestModal } from './AiSuggestModal';
import { AiImproveModal } from './AiImproveModal';
import { AiScoreModal } from './AiScoreModal';
import { ResumeHistoryModal } from './ResumeHistoryModal';
import { DropdownMenu } from './ui/DropdownMenu';
import { listTemplates } from '../api/templates';
import type { TemplateVO } from '../api/types';

const COLORS = [
  '#2563eb', '#7c3aed', '#db2777', '#dc2626',
  '#ea580c', '#16a34a', '#0891b2', '#374151',
];

interface Props {
  resumeRef: React.RefObject<HTMLDivElement | null>;
}

/**
 * 编辑器工具栏（F1）：12 个平铺按钮收敛为「返回 / 样式 / 历史 / 分享 / AI / 文件 / 导出」7 个入口。
 */
export function Toolbar({ resumeRef }: Props) {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data, setTemplate, setAccentColor, resetData, loadData } = useResumeStore();
  const [exporting, setExporting] = useState<'pdf' | 'png' | null>(null);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [showShare, setShowShare] = useState(false);
  const [showAiSuggest, setShowAiSuggest] = useState(false);
  const [showAiImprove, setShowAiImprove] = useState(false);
  const [showAiScore, setShowAiScore] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [templates, setTemplates] = useState<TemplateVO[]>([]);
  const colorInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    listTemplates()
      .then(setTemplates)
      .catch(() => {
        /* 模板拉取失败时静默降级为硬编码空列表，不影响编辑 */
      });
  }, []);

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

      {/* 左侧：返回 */}
      <button
        onClick={() => navigate('/')}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors shrink-0"
        title="返回工作台"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M19 12H5"/><polyline points="12 19 5 12 12 5"/>
        </svg>
        返回
      </button>

      <div className="w-px h-6 bg-gray-200" />

      <div className="flex items-center gap-2 shrink-0">
        <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center text-white text-xs font-bold">R</div>
        <span className="font-semibold text-gray-800 text-sm hidden sm:inline">简历生成器</span>
      </div>

      <div className="w-px h-6 bg-gray-200 hidden sm:block" />

      {/* 中部：结构区（样式 / 历史 / 分享） */}
      <DropdownMenu
        label={<span className="flex items-center gap-1.5">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
            <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
          </svg>
          样式
        </span>}
        align="left"
        panelClassName="p-3 w-72"
      >
        <div className="flex gap-3 mb-3">
          {templates.map((t) => (
            <button
              key={t.code}
              type="button"
              onClick={() => setTemplate(t.code)}
              className={`flex-1 rounded-lg border-2 p-2 text-center transition-all ${
                data.templateId === t.code ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="w-full aspect-[3/4] bg-gray-100 rounded mb-1.5 overflow-hidden flex flex-col p-1 gap-0.5">
                <div className="h-3 rounded" style={{ backgroundColor: t.code === 'modern' ? '#2563eb' : '#e5e7eb' }} />
                <div className="flex gap-0.5 flex-1">
                  {t.code === 'modern' && <div className="w-5 rounded" style={{ backgroundColor: '#2563eb22' }} />}
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
              <p className="text-[10px] text-gray-400">{t.category}</p>
            </button>
          ))}
        </div>

        <div className="border-t border-gray-100 pt-3">
          <p className="text-xs text-gray-500 mb-2">配色</p>
          <div className="grid grid-cols-8 gap-2 mb-3">
            {COLORS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setAccentColor(c)}
                className={`w-7 h-7 rounded-full transition-transform hover:scale-110 ${data.accentColor === c ? 'ring-2 ring-offset-2 ring-blue-500' : ''}`}
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
              className="w-7 h-7 rounded cursor-pointer border-0 p-0"
            />
            <span className="text-xs text-gray-400 font-mono">{data.accentColor}</span>
          </div>
        </div>
      </DropdownMenu>

      <button
        onClick={() => setShowHistory(true)}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors shrink-0"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
        </svg>
        历史
      </button>

      <button
        onClick={() => setShowShare(true)}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-md transition-colors shrink-0"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
          <line x1="8.6" y1="13.5" x2="15.4" y2="17.5"/><line x1="8.6" y1="10.5" x2="15.4" y2="6.5"/>
        </svg>
        分享
      </button>

      <div className="flex-1" />

      {/* 右侧：操作区（AI / 文件 / 导出） */}
      <DropdownMenu
        label={<span className="flex items-center gap-1.5">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z"/>
          </svg>
          AI
        </span>}
        items={[
          { key: 'suggest', label: 'AI 建议', onClick: () => setShowAiSuggest(true) },
          { key: 'score', label: 'AI 评分', onClick: () => setShowAiScore(true) },
        ]}
      />

      <DropdownMenu
        label="文件"
        items={[
          { key: 'import', label: '导入 JSON', onClick: handleImport },
          { key: 'save', label: '保存 JSON', onClick: handleSaveJSON },
          { key: 'reset', label: '重置内容', onClick: () => setShowResetConfirm(true), danger: true },
        ]}
      />

      <DropdownMenu
        primary
        disabled={!!exporting}
        label={<span className="flex items-center gap-1.5">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          {exporting ? '导出中...' : '导出'}
        </span>}
        items={[
          { key: 'png', label: '导出图片', onClick: () => handleExport('png'), disabled: !!exporting },
          { key: 'pdf', label: '导出 PDF', onClick: () => handleExport('pdf'), disabled: !!exporting },
        ]}
      />

      {showResetConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowResetConfirm(false)}>
          <div className="bg-white rounded-xl shadow-2xl p-6 w-[360px] max-w-[92vw]" onClick={(e) => e.stopPropagation()}>
            <p className="text-sm text-gray-800 mb-5">确定清空所有内容？该操作不可撤销。</p>
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowResetConfirm(false)} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-md">取消</button>
              <button
                onClick={() => { resetData(); setShowResetConfirm(false); }}
                className="px-4 py-2 text-sm bg-red-500 text-white rounded-md hover:bg-red-600"
              >
                确定清空
              </button>
            </div>
          </div>
        </div>
      )}

      {showShare && id && <ShareModal resumeId={id} onClose={() => setShowShare(false)} />}
      {showAiSuggest && id && (
        <AiSuggestModal
          resumeId={id}
          onClose={() => setShowAiSuggest(false)}
          onImprove={() => {
            setShowAiSuggest(false);
            setShowAiImprove(true);
          }}
        />
      )}
      {showAiImprove && id && (
        <AiImproveModal
          resumeId={id}
          currentData={data}
          onClose={() => setShowAiImprove(false)}
        />
      )}
      {showAiScore && id && (
        <AiScoreModal
          resumeId={id}
          onClose={() => setShowAiScore(false)}
        />
      )}
      {showHistory && id && (
        <ResumeHistoryModal
          resumeId={id}
          onClose={() => setShowHistory(false)}
        />
      )}
    </header>
  );
}
