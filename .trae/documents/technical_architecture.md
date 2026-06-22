# 技术架构文档: Subs Check APK UI Mockup

## 技术选型
- 纯HTML/CSS/JS实现，无需框架
- 使用CSS变量实现Material Design 3设计系统
- 使用Google Material Symbols图标
- 使用Google Fonts获取独特字体

## 设计系统

### 颜色系统
- 主色: 紫色/靛蓝色 (#6750A4 - Material Design 3 Primary)
- 主色变体: #7F67BE
- 表面色: #FFFFFF (卡片), #F5F5F5 (背景)
- 成功色: #386A20 (存活状态)
- 信息色: #0061A4 (最终状态)
- 错误色: #BA1A1A
- 文本色: #1C1B1F (主要), #49454F (次要)

### 字体
- 标题: 'Outfit' - 现代几何无衬线字体
- 正文: 'Nunito Sans' - 圆润易读的无衬线字体
- 代码: 'JetBrains Mono' - 等宽字体用于日志

### 组件规范
- 卡片圆角: 16px (MD3标准)
- 按钮圆角: 20px (MD3 pill形状)
- 阴影: 0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.14)
- 间距系统: 4px基础单位

## 实现方案

### 结构
- 模拟手机框架作为容器
- 状态栏模拟(时间、电池、信号)
- 应用栏
- 可滚动内容区域
- 固定底部按钮

### 动画
- 进度条脉冲动画
- 日志条目淡入效果
- 卡片悬停微妙提升效果
- 加载状态指示器

### 响应式
- 固定手机宽度模拟(390px)
- 居中显示在页面上

## 文件结构
```
e:\subs-check-apk\
├── index.html          # 主页面
├── style.css           # 样式
└── script.js           # 交互逻辑
```
