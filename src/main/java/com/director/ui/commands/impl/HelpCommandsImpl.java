package com.director.ui.commands.impl;

import com.director.ui.commands.HelpCommands;
import com.director.ui.dialogs.DialogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

public final class HelpCommandsImpl implements HelpCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelpCommandsImpl.class);

    private final DialogManager dialogManager;

    public HelpCommandsImpl(DialogManager dialogManager) {
        this.dialogManager = dialogManager;
    }

    @Override
    public void showShortcutHelp() {
        dialogManager.showShortcutsDialog();
    }

    @Override
    public void openUserManual() {
        LOGGER.info("[Help] Open user manual");
        try {
            Desktop.getDesktop().browse(new URI("https://director.github.io/docs"));
        } catch (Exception e) {
            LOGGER.error("[Help] 打开用户手册失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void openVideoTutorial() {
        LOGGER.info("[Help] Open video tutorial");
        try {
            Desktop.getDesktop().browse(new URI("https://www.youtube.com/@theopote"));
        } catch (Exception e) {
            LOGGER.error("[Help] 打开视频教程失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void reportBug() {
        LOGGER.info("[Help] Report bug");
    }

    @Override
    public void showAboutDialog() {
        dialogManager.showAboutDialog();
    }
}
