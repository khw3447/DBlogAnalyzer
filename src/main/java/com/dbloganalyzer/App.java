package com.dbloganalyzer;

import com.dbloganalyzer.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class App {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to the default cross-platform look and feel.
        }
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
