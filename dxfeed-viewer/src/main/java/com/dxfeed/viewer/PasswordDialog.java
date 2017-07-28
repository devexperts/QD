/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.viewer;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.intellij.uiDesigner.core.*;

public class PasswordDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField usernameTextField;
    private JPasswordField passwordTextField;
    private JLabel titleLabel;
    private JLabel warningLabel;
    boolean isOK = false;

    public PasswordDialog() {
        setContentPane(contentPane);
        setModal(true);
        Dimension buttonDim = new Dimension(80, 30);
        buttonOK.setPreferredSize(buttonDim);
        buttonCancel.setPreferredSize(buttonDim);
        setPreferredSize(new Dimension(300, 220));
        getRootPane().setDefaultButton(buttonOK);

        usernameTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateTextFields();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateTextFields();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateTextFields();
            }
        });


        passwordTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateTextFields();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateTextFields();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateTextFields();
            }
        });

        passwordTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                passwordTextField.selectAll();
                super.focusGained(e);    //To change body of overridden methods use File | Settings | File Templates.
            }
        });


        buttonOK.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

// call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

// call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
// add your code here
        dispose();
        isOK = true;
    }

    private void onCancel() {
// add your code here if necessary
        dispose();
    }

    private void validateTextFields() {
        buttonOK.setEnabled(usernameTextField.getText().length() > 0 && passwordTextField.getPassword().length > 0);
    }

    public static Credentials showPasswordDialog(String title, Credentials credentials, JFrame parent) {
        return showPasswordDialog(title, credentials, parent, null);
    }

    public static Credentials showPasswordDialog(String title, Credentials credentials, JFrame parent, String warning) {
        Credentials result = null;
        PasswordDialog dialog = new PasswordDialog();
        dialog.titleLabel.setText(title);
        if (credentials != null) {
            dialog.usernameTextField.setText(credentials.getUsername());
            dialog.passwordTextField.setText(credentials.getPassword());
        }
        dialog.pack();
        dialog.setTitle("Please log in");
        if (warning == null) {
            dialog.warningLabel.setVisible(false);
        } else {
            dialog.warningLabel.setVisible(true);
            dialog.warningLabel.setText(warning);
        }
        dialog.setLocationRelativeTo(parent);
        if (parent != null) {
            dialog.setLocation((int) parent.getLocationOnScreen().getX() + parent.getWidth() / 2 - dialog.getSize().width / 2, (int)parent.getLocationOnScreen().getY() + parent.getHeight() / 2 - dialog.getSize().height / 2);
        }
        dialog.usernameTextField.grabFocus();
        dialog.setVisible(true);
        if (dialog.isOK)
            result = new Credentials(dialog.usernameTextField.getText(), String.valueOf(dialog.passwordTextField.getPassword()));

        return result;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$()
    {
        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(panel1, gbc);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setEnabled(false);
        buttonOK.setText("Connect");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(buttonOK, gbc);
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(buttonCancel, gbc);
        final JToolBar.Separator toolBar$Separator1 = new JToolBar.Separator();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(toolBar$Separator1, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        contentPane.add(panel3, gbc);
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null));
        titleLabel = new JLabel();
        titleLabel.setIcon(new ImageIcon(getClass().getResource("/com/dxfeed/viewer/icons/password.png")));
        titleLabel.setText("Connect to:");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.gridheight = 2;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(titleLabel, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Username: ");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(label1, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("Password: ");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(label2, gbc);
        usernameTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(usernameTextField, gbc);
        passwordTextField = new JPasswordField();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(passwordTextField, gbc);
        final JToolBar.Separator toolBar$Separator2 = new JToolBar.Separator();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(toolBar$Separator2, gbc);
        warningLabel = new JLabel();
        warningLabel.setFocusable(false);
        warningLabel.setFont(new Font(warningLabel.getFont().getName(), warningLabel.getFont().getStyle(), 10));
        warningLabel.setForeground(new Color(-4521975));
        warningLabel.setText("Wrong username/password/ipfAddress");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.WEST;
        panel3.add(warningLabel, gbc);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }
}
