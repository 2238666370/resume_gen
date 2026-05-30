import { useRef } from 'react';
import { useResumeStore } from './store/resumeStore';
import { Toolbar } from './components/Toolbar';
import { SectionsPanel } from './components/editor/SectionsPanel';
import { ClassicTemplate } from './components/templates/ClassicTemplate';
import { ModernTemplate } from './components/templates/ModernTemplate';
import { MinimalTemplate } from './components/templates/MinimalTemplate';

function App() {
  const { data } = useResumeStore();
  const resumeRef = useRef<HTMLDivElement>(null);

  const renderTemplate = () => {
    switch (data.templateId) {
      case 'modern': return <ModernTemplate data={data} />;
      case 'minimal': return <MinimalTemplate data={data} />;
      default: return <ClassicTemplate data={data} />;
    }
  };

  return (
    <div className="h-screen flex flex-col bg-gray-50 overflow-hidden">
      <Toolbar resumeRef={resumeRef} />

      <div className="flex flex-1 overflow-hidden">
        {/* Left Panel – Editor */}
        <aside className="w-80 flex-shrink-0 bg-white border-r border-gray-200 overflow-y-auto">
          <div className="p-3">
            <p className="text-xs text-gray-400 mb-3 text-center">拖拽左侧 ⠿ 可调整模块顺序，开关控制显示</p>
            <SectionsPanel />
          </div>
        </aside>

        {/* Center – Resume Preview */}
        <main className="flex-1 overflow-auto bg-gray-100 flex justify-center py-8 px-4">
          <div className="w-full max-w-[794px]">
            {/* A4 shadow wrapper */}
            <div
              className="bg-white shadow-2xl rounded-sm"
              style={{ minHeight: '1123px' }}
              ref={resumeRef}
            >
              {renderTemplate()}
            </div>
            <p className="text-center text-xs text-gray-400 mt-4">
              内容实时预览 · 数据已自动保存至本地
            </p>
          </div>
        </main>
      </div>
    </div>
  );
}

export default App;
