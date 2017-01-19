package com.kii.virtualdevice.ui;

import com.kii.virtualdevice.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Evan on 2016/12/7.
 */
public class MainFrame {
    private JPanel mainPanel;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JTabbedPane tabbedPane;
    private JTextField tokenField;
    private JTree deviceTree;
    private JButton refreshDeviceListButton;
    private JButton startButton;
    private JButton deleteButton;
    private JComboBox typeComboBox;
    private JComboBox fwComboBox;
    private JButton createButton;
    private JScrollPane treeScrollPane;
    InfiniteProgressPanel glasspane = new InfiniteProgressPanel();
    JFrame parentFrame;
    String token;
    String userID;
    String firmwareVersion;

    java.util.List<Device> myDevices = null;

    public MainFrame() {

        typeComboBox.setEditable(false);
        for (int i = 0; i < Config.SupportedTypes.length(); i++) {
            typeComboBox.addItem(Config.SupportedTypes.optJSONObject(i).optString("type"));
        }

        loginButton.addActionListener(loginAction);
        refreshDeviceListButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (token == null) {
                    JOptionPane.showMessageDialog(parentFrame, "Please login first",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                startGlassPane();
                new Thread() {
                    @Override
                    public void run() {
                        myDevices = Device.listDevices(token);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                displayDeviceTree();
                                glasspane.stop();
                            }
                        });
                    }
                }.start();
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) deviceTree.getLastSelectedPathComponent();
                if (node == null) {
                    JOptionPane.showMessageDialog(parentFrame, "Choose a device first",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                TreeNode[] path = node.getPath();
                if (path.length < 2) {
                    return;
                }
                DefaultMutableTreeNode deviceNode = (DefaultMutableTreeNode) path[1];
                Device device = (Device) deviceNode.getUserObject();
                startGlassPane();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            device.delete();
                            myDevices = Device.listDevices(token);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                displayDeviceTree();
                                glasspane.stop();
                            }
                        });
                    }
                }.start();
            }
        });

        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (token == null) {
                    JOptionPane.showMessageDialog(parentFrame, "Please login first",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int index = typeComboBox.getSelectedIndex();
                final String type;
                type = Config.SupportedTypes.optJSONObject(index).optString("type");
                startGlassPane();
                new Thread() {
                    @Override
                    public void run() {
                        Device.createNewDevice(userID, token, type, Config.SupportedTypes.optJSONObject(index).optString("firmwareVersion"));
                        myDevices = Device.listDevices(token);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                displayDeviceTree();
                                glasspane.stop();
                            }
                        });
                    }
                }.start();
            }
        });

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) deviceTree.getLastSelectedPathComponent();
                if (node == null) {
                    JOptionPane.showMessageDialog(parentFrame, "Choose a device first",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                TreeNode[] path = node.getPath();
                if (path.length < 2) {
                    return;
                }
                DefaultMutableTreeNode deviceNode = (DefaultMutableTreeNode) path[1];
                Device device = (Device) deviceNode.getUserObject();
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    String title = tabbedPane.getTitleAt(i);
                    if (title.equals(device.getVendorThingID())) {
                        JOptionPane.showMessageDialog(parentFrame, "Already running",
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                startGlassPane();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            device.start();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                DeviceFrame frame = new DeviceFrame(device, MainFrame.this);
                                tabbedPane.add(device.getVendorThingID(), frame.getMainPanel());
                                glasspane.stop();
                            }
                        });
                    }
                }.start();
            }
        });

        displayDeviceTree();
    }

    ActionListener loginAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            startGlassPane();
            new Thread() {
                @Override
                public void run() {
                    token = KiiUtils.loginKiiCloud(usernameField.getText(), new String(passwordField.getPassword())).optString("access_token");
                    userID = KiiUtils.loginKiiCloud(usernameField.getText(), new String(passwordField.getPassword())).optString("id");
                    if (token != null) {
                        myDevices = Device.listDevices(token);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                usernameField.setEnabled(false);
                                passwordField.setEnabled(false);
                                loginButton.setEnabled(false);
                                tokenField.setText(token);
                                displayDeviceTree();
                            }
                        });
                    }
                    glasspane.stop();
                }
            }.start();
        }
    };

    void displayDeviceTree() {
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("Devices");
        if (myDevices != null) {
            for (Device device : myDevices) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(device);
                DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(device.getThingType());
                node.add(leaf);
                leaf = new DefaultMutableTreeNode("ThingID: " + device.getThingID());
                node.add(leaf);
                top.add(node);
            }
        }
        DefaultTreeModel model = new DefaultTreeModel(top);
        deviceTree.setModel(model);
    }

    public void startGlassPane() {
        glasspane.setBounds(0, 0, parentFrame.getWidth(), parentFrame.getHeight());
        glasspane.start();
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("MainFrame");
        MainFrame main = new MainFrame();
        main.parentFrame = frame;
        frame.setContentPane(main.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(800, 640));
        frame.pack();
        frame.setVisible(true);
        frame.setGlassPane(main.glasspane);
    }
}
