#!/bin/bash

# Addax Admin 开发环境启动脚本

echo "🚀 启动 Addax Admin 开发环境..."

# 检查是否在正确的目录
if [ ! -f "pom.xml" ]; then
    echo "❌ 错误: 请在项目根目录运行此脚本"
    exit 1
fi

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到 Java 环境"
    exit 1
fi

# 检查 Node.js 环境
if ! command -v node &> /dev/null; then
    echo "❌ 错误: 未找到 Node.js 环境"
    exit 1
fi

# 安装前端依赖
echo "📦 安装前端依赖..."
if [ ! -d "frontend/node_modules" ]; then
    yarn install
fi

# 启动前端开发服务器
echo "🎨 启动前端开发服务器..."
yarn dev:frontend &
FRONTEND_PID=$!

# 返回根目录
cd ..

# 启动后端服务器
echo "⚙️  启动后端服务器..."
cd backend
mvn spring-boot:run &
BACKEND_PID=$!

# 创建停止脚本
cat > ../stop-dev.sh << EOF
#!/bin/bash
echo "🛑 停止开发环境..."
kill $FRONTEND_PID 2>/dev/null
kill $BACKEND_PID 2>/dev/null
echo "✅ 开发环境已停止"
EOF
chmod +x ../stop-dev.sh

echo ""
echo "✅ 开发环境启动完成!"
echo "📊 前端地址: http://localhost:5173"
echo "🔧 后端地址: http://localhost:8080"
echo "🛑 停止服务: ./stop-dev.sh"
echo ""
echo "按 Ctrl+C 停止所有服务"

# 等待用户中断
wait