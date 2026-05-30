/** Electron IPC 桥接类型声明 */
interface ElectronAPI {
  invoke(channel: string, ...args: unknown[]): Promise<unknown>;
}

declare global {
  interface Window {
    electron?: ElectronAPI;
  }
}

export {};
