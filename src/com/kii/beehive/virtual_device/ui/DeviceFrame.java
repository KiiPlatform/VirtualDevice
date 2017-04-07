package com.kii.beehive.virtual_device.ui;

import com.kii.beehive.virtual_device.Device;
import org.json.JSONObject;
import sun.applet.Main;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.temporal.ValueRange;
import java.util.ArrayList;

/**
 * Created by Evan on 2016/12/8.
 */
public class DeviceFrame implements Device.OnCommandReceivedListener, Device.OnStatesChangedListener {
    private JPanel mainPanel;
    private JTextPane commandPane;
    private JTable statesTable;
    private JButton stopButton;
    private JCheckBox updateOnChangeCheckbox;
    private JLabel infoLabel;
    private JSpinner uploadStateSpinner;
    public Device device;
    MainFrame parentFrame;


    public DeviceFrame(Device device, MainFrame parentFrame) {
        this.device = device;
        this.parentFrame = parentFrame;
        device.setOnCommandReceivedListener(this);
        device.setOnStatesChangedListener(this);
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                device.stop();
                device.setOnCommandReceivedListener(null);
                device.setOnStatesChangedListener(null);
                parentFrame.getTabbedPane().remove(mainPanel);
            }
        });
        updateOnChangeCheckbox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                device.setUploadStateOnChanged(updateOnChangeCheckbox.isSelected());
            }
        });

        infoLabel.setText(device.getDeviceType() + " " + device.getGlobalThingID());
        updateOnChangeCheckbox.setSelected(device.isUploadStateOnChanged());

        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0;
            }

            @Override
            public void setValueAt(Object aValue, int row, int column) {
                if (column != 1) {
                    return;
                }
                String key = getValueAt(row, 0).toString();
                ArrayList<String> keyList = new ArrayList<>();
                keyList.add(key);
                ArrayList<String> valueList = new ArrayList<>();
                valueList.add(aValue.toString());
                if (device.setStates(keyList, valueList)) {
                    super.setValueAt(aValue, row, column);
                }
            }
        };
        statesTable.setModel(tableModel);
        updateStatesTable();
        SpinnerModel uploadStateModel = new SpinnerNumberModel(0, 0, 3600, 10);
        uploadStateSpinner.setModel(uploadStateModel);
        uploadStateSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int time = Integer.parseInt(uploadStateSpinner.getValue().toString());
                device.setUploadStatePeriod(time);
            }
        });
    }

    void updateStatesTable() {
        DefaultTableModel tableModel = (DefaultTableModel) statesTable.getModel();
        tableModel.setRowCount(0);
        JSONObject states = device.getStates();
        String[] names = JSONObject.getNames(states);
        for (String name : names) {
            String[] row = new String[]{name, states.get(name).toString()};
            tableModel.addRow(row);
        }
        statesTable.invalidate();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    @Override
    public void onStatesChanged(String fieldName) {
        updateStatesTable();
    }

    @Override
    public void onCommandReceived(String command) {
        Document doc = commandPane.getDocument();
        try {
            doc.insertString(doc.getLength(), "******************Received: " + System.currentTimeMillis() + " ***************\n", null);
            doc.insertString(doc.getLength(), command + "\n", null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }


}
