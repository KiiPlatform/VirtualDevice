package com.kii.virtualdevice.ui;

import com.kii.virtualdevice.Device;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

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
    private JLabel vendorThingIDLabel;
    private JLabel thingIDLabel;
    private JSpinner genRandomStateSpinner;
    public Device device;
    MainFrame parentFrame;
    String[] aliasNames;
    int[] aliasPos;


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

        infoLabel.setText(device.getThingType() + "-" + device.getFirmwareVersion());
        vendorThingIDLabel.setText("VendorThingID: " + device.getVendorThingID());
        thingIDLabel.setText("ThingID: " + device.getThingID());
        updateOnChangeCheckbox.setSelected(device.isUploadStateOnChanged());

        DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Field", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                for (int i : aliasPos) {
                    if (i == row) {
                        return false;
                    }
                }

                return column != 0;
            }

            @Override
            public void setValueAt(Object aValue, int row, int column) {
                if (column != 1) {
                    return;
                }
                int index = 0;
                for (int i = 0; i < aliasPos.length; i++) {
                    if (row > aliasPos[i]) {
                        index = i;
                    }
                }
                String alias = aliasNames[index];
                String key = getValueAt(row, 0).toString();
                ArrayList<String> keyList = new ArrayList<>();
                keyList.add(key);
                ArrayList<String> valueList = new ArrayList<>();
                valueList.add(aValue.toString());
                if (device.setStates(alias, keyList, valueList)) {
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

        SpinnerModel genStateModel = new SpinnerNumberModel(0, 0, 3600, 10);
        genRandomStateSpinner.setModel(genStateModel);
        genRandomStateSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int time = Integer.parseInt(genRandomStateSpinner.getValue().toString());
                device.setGenRandomStatePeriod(time);
            }
        });
    }

    void updateStatesTable() {
        DefaultTableModel tableModel = (DefaultTableModel) statesTable.getModel();
        tableModel.setRowCount(0);
        JSONObject states = device.getStates();
        aliasNames = JSONObject.getNames(states);
        aliasPos = new int[aliasNames.length];
        int count = 0;
        for (int i = 0; i < aliasNames.length; i++) {
            String name = aliasNames[i];
            String[] row = new String[]{name, ""};
            tableModel.addRow(row);
            JSONObject aliasState = states.optJSONObject(name);
            aliasPos[i] = count;
            count += 1 + aliasState.length();
            String[] names = JSONObject.getNames(aliasState);
            for (String key : names) {
                row = new String[]{key, aliasState.get(key).toString()};
                tableModel.addRow(row);
            }
        }
        statesTable.invalidate();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    @Override
    public void onStatesChanged() {
        updateStatesTable();
    }

    @Override
    public void onCommandReceived(String command) {
        Document doc = commandPane.getDocument();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
            doc.insertString(doc.getLength(), "******************Received: " + sdf.format(new Date()) + " ***************\n", null);
            doc.insertString(doc.getLength(), command + "\n", null);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }


}
