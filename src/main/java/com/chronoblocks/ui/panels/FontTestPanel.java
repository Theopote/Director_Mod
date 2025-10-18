package com.chronoblocks.ui.panels;

import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字体测试面板
 * 用于验证中文和Emoji字体是否正常加载和显示
 * 
 * <h3>功能</h3>
 * <ul>
 *   <li>测试基本字符显示（英文、数字）</li>
 *   <li>测试中文字符显示</li>
 *   <li>测试Emoji图标显示</li>
 *   <li>测试混合文本显示</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FontTestPanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(FontTestPanel.class);
    
    public FontTestPanel() {
        LOGGER.info("[FontTestPanel] 字体测试面板已创建");
    }
    
    @Override
    public String getPanelName() {
        return "FontTest";
    }
    
    @Override
    public String getWindowName() {
        return "字体测试面板###FontTestPanel";
    }
    
    @Override
    protected int getWindowFlags() {
        return imgui.flag.ImGuiWindowFlags.AlwaysAutoResize;
    }
    
    @Override
    protected void doRender() {
        // 标题
        ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f, "🧪 ChronoBlocks 字体测试");
        ImGui.separator();
        
        // 基本字符测试
        if (ImGui.collapsingHeader("基本字符测试", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            ImGui.text("English: ABCDEFG abcdefg 0123456789");
            ImGui.text("Numbers: !@#$%^&*()_+-=[]{}|;':\",./<>?");
            ImGui.unindent();
        }
        
        ImGui.spacing();
        
        // 中文测试
        if (ImGui.collapsingHeader("中文字符测试", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            ImGui.text("简体中文: 容器管理系统");
            ImGui.text("常用汉字: 你好世界，这是一个测试。");
            ImGui.text("标点符号: 、。？！《》【】");
            ImGui.unindent();
        }
        
        ImGui.spacing();
        
        // Emoji测试 - 容器面板使用的（带Unicode码点）
        if (ImGui.collapsingHeader("Emoji图标测试（容器面板）", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            
            ImGui.textColored(1.0f, 1.0f, 0.4f, 1.0f, "核心操作图标:");
            ImGui.text("📦 Package (容器) - U+1F4E6");
            ImGui.text("➕ Heavy Plus (新建) - U+2795");
            ImGui.text("🧱 Brick (方块) - U+1F9F1");
            ImGui.text("🗑 Wastebasket (删除) - U+1F5D1");
            ImGui.text("📋 Clipboard (复制) - U+1F4CB");
            ImGui.text("↶ Anticlockwise (撤销) - U+21B6");
            ImGui.text("↷ Clockwise (重做) - U+21B7");
            
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            ImGui.textColored(1.0f, 1.0f, 0.4f, 1.0f, "容器类型图标:");
            ImGui.text("📁 Folder (分组) - U+1F4C1");
            ImGui.text("🏢 Office (建筑) - U+1F3E2");
            ImGui.text("🗺️ Map (区域) - U+1F5FA");
            ImGui.text("✨ Sparkles (粒子) - U+2728");
            ImGui.text("🎬 Clapper (动画) - U+1F3AC");
            
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            
            // 显示状态说明
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "提示:");
            ImGui.textWrapped("如果某些Emoji显示为问号(?)，说明对应的Unicode范围未被加载。");
            ImGui.textWrapped("U+1F3xx 和 U+1F4xx 和 U+1F5xx 范围需要完整的Emoji字体支持。");
            
            ImGui.unindent();
        }
        
        ImGui.spacing();
        
        // 更多Emoji测试
        if (ImGui.collapsingHeader("其他Emoji测试")) {
            ImGui.indent();
            
            ImGui.text("表情: 😀 😁 😂 😊 🥰 😎 🤔 😴");
            ImGui.text("手势: 👍 👎 👏 🙏 ✌️ 👌 🤝");
            ImGui.text("符号: ⭐ ❤️ 💎 🔥 💧 ⚡ 🌟 ✅");
            ImGui.text("箭头: ⬆️ ⬇️ ⬅️ ➡️ ↖️ ↗️ ↘️ ↙️");
            ImGui.text("工具: 🔧 🔨 ⚙️ 🛠️ 🔩 ⚒️ 🪛");
            
            ImGui.unindent();
        }
        
        ImGui.spacing();
        
        // 混合文本测试
        if (ImGui.collapsingHeader("混合文本测试", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            
            ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, "📦 容器管理系统 v4.0");
            ImGui.text("➕ 新建容器 | 🧱 从世界采集 | 🗑 删除选中");
            ImGui.text("↶ 撤销 | ↷ 重做");
            ImGui.separator();
            ImGui.text("容器类型: 📁 分组 | 🏢 建筑 | 🗺️ 区域");
            ImGui.text("动画效果: ✨ 粒子 | 🎬 动画");
            
            ImGui.unindent();
        }
        
        ImGui.spacing();
        
        // 交互测试
        if (ImGui.collapsingHeader("交互测试")) {
            ImGui.indent();
            
            if (ImGui.button("📦 测试按钮 1")) {
                LOGGER.info("✓ Emoji按钮1点击成功");
            }
            ImGui.sameLine();
            if (ImGui.button("➕ 测试按钮 2")) {
                LOGGER.info("✓ Emoji按钮2点击成功");
            }
            ImGui.sameLine();
            if (ImGui.button("🧱 测试按钮 3")) {
                LOGGER.info("✓ Emoji按钮3点击成功");
            }
            
            ImGui.spacing();
            
            if (ImGui.button("🗑 删除")) {
                LOGGER.info("✓ 删除按钮点击");
            }
            ImGui.sameLine();
            if (ImGui.button("📋 复制")) {
                LOGGER.info("✓ 复制按钮点击");
            }
            ImGui.sameLine();
            if (ImGui.button("↶ 撤销")) {
                LOGGER.info("✓ 撤销按钮点击");
            }
            ImGui.sameLine();
            if (ImGui.button("↷ 重做")) {
                LOGGER.info("✓ 重做按钮点击");
            }
            
            ImGui.unindent();
        }
        
        ImGui.spacing();
        ImGui.separator();
        
        // 说明文字
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "提示:");
        ImGui.textWrapped("如果看到问号(?)而不是Emoji图标，说明系统字体未正确加载。");
        ImGui.textWrapped("请查看日志中的字体加载信息，并确保系统已安装Emoji字体。");
        
        ImGui.spacing();
        
        // 关闭按钮
        if (ImGui.button("关闭测试面板")) {
            setVisible(false);
        }
    }
    
    @Override
    public int getPriority() {
        return 100;  // 优先级较高，便于调试
    }
}

