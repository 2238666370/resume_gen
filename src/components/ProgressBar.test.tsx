import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProgressBar } from './ProgressBar';

describe('ProgressBar', () => {
  it('有 label 时展示提示文案', () => {
    render(<ProgressBar label="AI 正在生成题库，请稍候..." />);
    expect(screen.getByText('AI 正在生成题库，请稍候...')).toBeInTheDocument();
  });

  it('无 label 时不渲染文案', () => {
    const { container } = render(<ProgressBar />);
    expect(container.querySelector('p')).toBeNull();
  });

  it('附加 className 生效', () => {
    const { container } = render(<ProgressBar className="mt-3" />);
    expect(container.firstChild).toHaveClass('mt-3');
  });
});
