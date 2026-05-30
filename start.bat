@echo off
chcp 65001 >nul
title 简历生成器
cd /d "%~dp0"

:: 检查 dist 是否存在
if not exist "dist\index.html" (
    echo [错误] 未找到构建产物，请先运行 npm run build
    pause
    exit /b 1
)

echo.
echo   📄 简历生成器启动中...
echo   ════════════════════════════
echo   浏览器将自动打开，如未打开请访问：
echo   http://localhost:2048
echo.
echo   关闭此窗口即可停止服务。
echo.

:: 启动 Node.js 服务器
node local-server.cjs

:: 如果 node 启动失败
if errorlevel 1 (
    echo.
    echo [错误] 启动失败，请确认已安装 Node.js
    echo 下载地址：https://nodejs.org/
    pause
)
