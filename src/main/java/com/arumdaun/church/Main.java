package com.arumdaun.church;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.SwingUtilities;
import javax.swing.AbstractCellEditor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

public class Main extends JFrame {
    private static final Path LEGACY_DATA_FILE = Paths.get("data", "cemetery-data.ser");
    private static final Path CLIENT_DATA_FILE = Paths.get("data", "client-data.ser");
    private static final Path CEMETERY_DATA_FILE = Paths.get("data", "cemetery-records.ser");
    private final CemeterySystem system;

    private final JTextArea logArea = new JTextArea();

    private final DefaultTableModel lotTableModel = new DefaultTableModel(
            new Object[] { "Lot Id", "Status", "Price", "Lot Information" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable lotTable = new JTable(lotTableModel);

    private final DefaultTableModel lotStatusTableModel = new DefaultTableModel(
            new Object[] { "Lot Id", "Price", "Status", "Client Id", "Client Name", "Balance" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable lotStatusTable = new JTable(lotStatusTableModel);

    private final DefaultTableModel clientTableModel = new DefaultTableModel(
            new Object[] { "Client Id", "Client Name", "Lots Owned", "Phone 1", "Phone 2", "Status", "Delete" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 6;
        }
    };
    private final JTable clientTable = new JTable(clientTableModel);

    private final DefaultTableModel paymentTableModel = new DefaultTableModel(
            new Object[] { "Lot Id", "Client Id", "Client Name", "Type", "Method", "Amount", "Date" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable paymentTable = new JTable(paymentTableModel);
    private final JCheckBox showDeletedClientsCheckBox = new JCheckBox("Show Deleted Clients");
    private JFrame registrationFrame;
    private JTabbedPane tabs;
    private JPanel purchaseCancellationTab;
    private JRadioButton purchaseModeButton;
    private JRadioButton cancelModeButton;
    private JButton transactionButton;

    private final JTextField clientIdField = new JTextField("100", 10);
    private final JTextField koreanNameField = new JTextField("김민수철", 30);
    private final JTextField englishSurnameField = new JTextField("Kim", 24);
    private final JTextField englishGivenNameField = new JTextField("Minsoo", 24);
    private final JTextField englishMiddleNameField = new JTextField("Chul", 24);
    private final JTextField phone1Field = new JTextField("010-123-4567", 20);
    private final JTextField phone2Field = new JTextField("555-0101", 20);
    private final JTextField streetAddressField = new JTextField("", 30);
    private final JTextField cityField = new JTextField("", 24);
    private final JTextField stateField = new JTextField("", 5);
    private final JTextField zipCodeField = new JTextField("", 10);

    private final JTextField paymentAmountField = new JTextField("$2,500.00", 12);
    private final JTextField purchaseDateField = new JTextField("", 12);
    private final JTextField transactionClientIdField = new JTextField("100", 10);
    private final JComboBox<String> purchaseLotCombo = new JComboBox<>();
    private final JLabel transactionClientNameLabel = new JLabel(" ");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main frame = new Main();
            frame.setVisible(true);
        });
    }

    public Main() {
        system = loadSystem();
        system.reconcileLotOwnership();
        if (system.isEmpty()) {
            seedDemoData();
            saveSystem();
        } else {
            saveSystem();
        }
        clientIdField.setEditable(false);
        clientIdField.setText(String.valueOf(system.getNextClientId()));
        setTitle("Arumdaun Church Cemetery Management");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));

        tabs = new JTabbedPane();
        tabs.addTab("Lots", new OperationsTab(this));
        tabs.addTab("Lot Statuses", new LotStatusTab(this));
        tabs.addTab("Clients", new ClientsTab(this));
        tabs.addTab("Payment History", new PaymentHistoryTab(this));
        purchaseCancellationTab = new PurchaseCancellationTab(this);
        tabs.addTab("Purchase & Cancellation", purchaseCancellationTab);
        add(tabs, BorderLayout.CENTER);

        refreshLotList();
        refreshClientTable();
        refreshPaymentTable();
        refreshPurchaseSelectors();
        refreshLotStatusTable();
    }

    private CemeterySystem loadSystem() {
        try {
            if (Files.exists(CLIENT_DATA_FILE) && Files.exists(CEMETERY_DATA_FILE)) {
                return readSeparatedSystem();
            }
            if (Files.exists(LEGACY_DATA_FILE)) {
                CemeterySystem legacySystem = readLegacySystem();
                saveSystem(legacySystem);
                return legacySystem;
            }
        } catch (IOException | ClassNotFoundException | ClassCastException exception) {
            System.err.println("Unable to load saved cemetery data: " + exception.getMessage());
        }
        return new CemeterySystem();
    }

    private void saveSystem() {
        saveSystem(system);
    }

    private void saveSystem(CemeterySystem value) {
        try {
            Path parent = CLIENT_DATA_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(CLIENT_DATA_FILE))) {
                output.writeObject(new Client.ClientData(value.clients, value.nextClientId));
            }
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(CEMETERY_DATA_FILE))) {
                output.writeObject(new CemeteryData(value.lots, value.purchases, value.payments));
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "Unable to save cemetery data: " + exception.getMessage(),
                    "Persistence error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private CemeterySystem readSeparatedSystem() throws IOException, ClassNotFoundException {
        Client.ClientData clientData;
        CemeteryData cemeteryData;
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(CLIENT_DATA_FILE))) {
            clientData = (Client.ClientData) input.readObject();
        }
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(CEMETERY_DATA_FILE))) {
            cemeteryData = (CemeteryData) input.readObject();
        }
        CemeterySystem result = new CemeterySystem();
        result.clients.putAll(clientData.getClients());
        result.nextClientId = clientData.getNextClientId();
        result.lots.putAll(cemeteryData.lots);
        result.purchases.addAll(cemeteryData.purchases);
        result.payments.addAll(cemeteryData.payments);
        return result;
    }

    private CemeterySystem readLegacySystem() throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(LEGACY_DATA_FILE))) {
            return (CemeterySystem) input.readObject();
        }
    }

    JPanel createLotPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Cemetery Lots"));
        lotTable.setRowHeight(28);
        lotTable.setAutoCreateRowSorter(true);
        lotTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        lotTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        lotTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        lotTable.getColumnModel().getColumn(3).setPreferredWidth(320);
        lotTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1) {
                    openLotInTransactionTab(lotTable, lotTableModel);
                }
            }
        });
        panel.add(new JScrollPane(lotTable), BorderLayout.CENTER);
        return panel;
    }

    private void openLotInTransactionTab(JTable sourceTable, DefaultTableModel sourceModel) {
        int selectedRow = sourceTable.getSelectedRow();
        if (selectedRow < 0 || transactionButton == null) {
            return;
        }
        int modelRow = sourceTable.convertRowIndexToModel(selectedRow);
        String lotNumber = sourceModel.getValueAt(modelRow, 0).toString();
        CemeteryLot lot = system.getLot(lotNumber);
        if (lot == null) {
            return;
        }
        boolean purchaseMode = !lot.isSold();
        if (purchaseMode) {
            transactionClientIdField.setText("");
            purchaseModeButton.setSelected(true);
        } else {
            transactionClientIdField.setText(String.valueOf(lot.getClientId()));
            cancelModeButton.setSelected(true);
        }
        updateTransactionButton(transactionButton, purchaseMode);
        purchaseLotCombo.setSelectedItem(lotNumber);
        updateTransactionClientName();
        tabs.setSelectedComponent(purchaseCancellationTab);
    }

    JPanel createLotStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Lot Statuses"));
        lotStatusTable.setAutoCreateRowSorter(true);
        lotStatusTable.setRowHeight(28);
        lotStatusTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1) {
                    openLotInTransactionTab(lotStatusTable, lotStatusTableModel);
                }
            }
        });
        panel.add(new JScrollPane(lotStatusTable), BorderLayout.CENTER);
        return panel;
    }

    JPanel createPurchaseCancellationPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        purchaseModeButton = new JRadioButton("Purchase a lot", true);
        cancelModeButton = new JRadioButton("Cancel a purchased lot");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(purchaseModeButton);
        modeGroup.add(cancelModeButton);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(purchaseModeButton);
        modePanel.add(cancelModeButton);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        fields.add(modePanel, constraints);
        addField(fields, constraints, 0, 1, "Payment or Refund Amount:", paymentAmountField);
        addField(fields, constraints, 0, 2, "교적번호:", transactionClientIdField);
        addField(fields, constraints, 0, 3, "Name:", transactionClientNameLabel);
        addField(fields, constraints, 0, 4, "Lot No:", purchaseLotCombo);
        addField(fields, constraints, 0, 5, "Transaction Date (YYYY-MM-DD):", purchaseDateField);

        transactionButton = new JButton("Purchase");
        transactionButton.addActionListener(e -> processPurchaseCancellation(purchaseModeButton.isSelected()));
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        fields.add(transactionButton, constraints);
        transactionClientIdField.addActionListener(e -> {
            updateTransactionClientName();
            updateTransactionButton(transactionButton, purchaseModeButton.isSelected());
        });
        purchaseModeButton.addActionListener(e -> {
            updateTransactionButton(transactionButton, true);
            refreshPurchaseLotSelector();
        });
        cancelModeButton.addActionListener(e -> {
            updateTransactionButton(transactionButton, false);
        });
        panel.add(fields, BorderLayout.CENTER);
        return panel;
    }

    private void updateTransactionClientName() {
        Client client = findTransactionClient();
        transactionClientNameLabel
                .setText(client == null ? " " : client.getDisplayName());
    }

    private void updateTransactionButton(JButton button, boolean purchaseMode) {
        button.setText(purchaseMode ? "Purchase" : "Cancel");
        purchaseLotCombo.removeAllItems();
        if (purchaseMode) {
            refreshPurchaseLotSelector();
        } else {
            Client client = findTransactionClient();
            if (client != null) {
                for (PurchaseRecord purchase : system.getActivePurchasesForClient(client.getClientId())) {
                    for (CemeteryLot lot : purchase.getLots()) {
                        purchaseLotCombo.addItem(lot.getLotNumber());
                    }
                }
            }
        }
    }

    private void processPurchaseCancellation(boolean purchaseMode) {
        try {
            Client client = findTransactionClient();
            String selectedLot = (String) purchaseLotCombo.getSelectedItem();
            String dateValue = purchaseDateField.getText().trim();
            double amount = parseRequiredAmount(paymentAmountField.getText().trim());
            if (client == null || selectedLot == null) {
                throw new IllegalArgumentException("Select a client and one lot.");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            LocalDate transactionDate = parseRequiredDate(dateValue);
            if (purchaseMode) {
                openRegistrationWindow();
                system.purchaseLots(client.getClientId(), List.of(selectedLot), amount, transactionDate);
                saveSystem();
            } else {
                system.cancelLot(client.getClientId(), selectedLot, transactionDate);
                saveSystem();
            }
            refreshLotList();
            refreshPaymentTable();
            refreshPurchaseSelectors();
            refreshLotStatusTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Transaction failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Client findTransactionClient() {
        try {
            return system.getClient(Integer.parseInt(transactionClientIdField.getText().trim()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    JPanel createClientsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        clientTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        clientTable.setAutoCreateRowSorter(true);
        clientTable.setRowHeight(28);
        clientTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        clientTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        clientTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        clientTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        clientTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        clientTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        clientTable.getColumnModel().getColumn(6).setPreferredWidth(70);
        clientTable.getColumnModel().getColumn(6).setCellRenderer(new DeleteButtonRenderer());
        clientTable.getColumnModel().getColumn(6).setCellEditor(new DeleteButtonEditor());
        clientTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int clickedColumn = clientTable.columnAtPoint(event.getPoint());
                if (event.getClickCount() == 1 && clickedColumn != 6) {
                    openSelectedClient();
                }
            }
        });
        showDeletedClientsCheckBox.addActionListener(e -> refreshClientTable());
        panel.add(new JScrollPane(clientTable), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(showDeletedClientsCheckBox);
        controls.add(new JLabel(
                "Click a row to open details. Use the trash icon in that row to soft-delete the client."));
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private void softDeleteClient(int clientId) {
        Client client = system.getClient(clientId);
        if (client == null || client.isDeleted()) {
            return;
        }
        system.softDeleteClient(clientId);
        refreshClientTable();
        refreshPurchaseSelectors();
        saveSystem();
    }

    private void refreshPurchaseSelectors() {
        refreshPurchaseLotSelector();
        updateTransactionClientName();
    }

    private void refreshPurchaseLotSelector() {
        purchaseLotCombo.removeAllItems();
        for (CemeteryLot lot : system.getAvailableLots()) {
            purchaseLotCombo.addItem(lot.getLotNumber());
        }
    }

    JPanel createPaymentHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        paymentTable.setAutoCreateRowSorter(true);
        paymentTable.setRowHeight(28);
        paymentTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        paymentTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        paymentTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        paymentTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        paymentTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        paymentTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        paymentTable.getColumnModel().getColumn(6).setPreferredWidth(110);
        panel.add(new JScrollPane(paymentTable), BorderLayout.CENTER);
        panel.add(new JLabel("Click column headers to sort by lot ID, client, payment/refund type, amount, or date."),
                BorderLayout.NORTH);
        return panel;
    }

    private void refreshClientTable() {
        clientTableModel.setRowCount(0);
        for (Client client : system.getClients(showDeletedClientsCheckBox.isSelected())) {
            clientTableModel.addRow(new Object[] {
                    client.getClientId(), client.getDisplayName(),
                    system.getOwnedLotIds(client.getClientId()), client.getPhone1(), client.getPhone2(),
                    client.isDeleted() ? "Deleted " + client.getDeletedDate() : "Active", "\uD83D\uDDD1"
            });
        }
    }

    private void refreshPaymentTable() {
        paymentTableModel.setRowCount(0);
        for (Payment payment : system.getPayments()) {
            Client client = system.getClient(payment.getClientId());
            paymentTableModel.addRow(new Object[] {
                    payment.getLotIds(), payment.getClientId(), client == null ? "" : client.getDisplayName(),
                    payment.getType(), payment.getMethod(), formatMoney(payment.getAmount()), payment.getDate()
            });
        }
    }

    private void openSelectedClient() {
        int selectedRow = clientTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        int modelRow = clientTable.convertRowIndexToModel(selectedRow);
        int clientId = (Integer) clientTableModel.getValueAt(modelRow, 0);
        Client client = system.getClient(clientId);
        if (client != null) {
            new ClientDetailFrame(client).setVisible(true);
        }
    }

    private void openRegistrationWindow() {
        if (registrationFrame == null) {
            registrationFrame = new JFrame("Register Client");
            registrationFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            registrationFrame.setSize(760, 700);
            registrationFrame.setLocationRelativeTo(this);
            registrationFrame.add(createClientPanel());
        }
        registrationFrame.setVisible(true);
        registrationFrame.toFront();
    }

    private class DeleteButtonRenderer extends JButton implements TableCellRenderer {
        DeleteButtonRenderer() {
            setText("\uD83D\uDDD1");
            setToolTipText("Soft delete this client");
            setFocusable(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            return this;
        }
    }

    private class DeleteButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("\uD83D\uDDD1");
        private int clientId;

        DeleteButtonEditor() {
            button.setToolTipText("Soft delete this client");
            button.setFocusable(false);
            button.addActionListener(e -> {
                softDeleteClient(clientId);
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                int row, int column) {
            int modelRow = table.convertRowIndexToModel(row);
            clientId = (Integer) clientTableModel.getValueAt(modelRow, 0);
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "\uD83D\uDDD1";
        }
    }

    private JPanel createClientPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Register Client"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        addField(panel, gbc, 0, 0, "Client ID:", clientIdField);
        addField(panel, gbc, 0, 1, "Korean 성명:", koreanNameField);
        addField(panel, gbc, 0, 2, "English Surname:", englishSurnameField);
        addField(panel, gbc, 0, 3, "English Given:", englishGivenNameField);
        addField(panel, gbc, 0, 4, "English Middle:", englishMiddleNameField);
        addField(panel, gbc, 0, 5, "Phone 1:", phone1Field);
        addField(panel, gbc, 0, 6, "Phone 2:", phone2Field);
        addField(panel, gbc, 0, 7, "Street Address:", streetAddressField);
        addField(panel, gbc, 0, 8, "City:", cityField);
        addField(panel, gbc, 0, 9, "State (2 letters):", stateField);
        addField(panel, gbc, 0, 10, "ZIP Code:", zipCodeField);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 2;
        JButton registerButton = new JButton("Register Client");
        registerButton.addActionListener(e -> registerClient());
        panel.add(registerButton, gbc);
        return panel;
    }

    JPanel createReportPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Reports"));
        JButton reportButton = new JButton("Print Reports");
        reportButton.addActionListener(e -> appendLog(system.getReportText()));
        panel.add(reportButton);
        return panel;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int startX, int row, String labelText,
            JComponent field) {
        gbc.gridx = startX;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = startX + 1;
        panel.add(field, gbc);
    }

    private void registerClient() {
        try {
            int clientId = system.getNextClientId();
            String koreanName = koreanNameField.getText().trim();
            String englishSurname = englishSurnameField.getText().trim();
            String englishGiven = englishGivenNameField.getText().trim();
            String englishMiddle = englishMiddleNameField.getText().trim();
            String phone1 = validatePhone(phone1Field.getText().trim(), "Phone 1");
            String phone2 = validateOptionalPhone(phone2Field.getText().trim(), "Phone 2");
            String streetAddress = streetAddressField.getText().trim();
            String city = cityField.getText().trim();
            String state = validateState(stateField.getText().trim());
            String zipCode = validateZipCode(zipCodeField.getText().trim());
            validateCompleteAddress(streetAddress, city, state, zipCode);

            if (koreanName.isEmpty() || englishSurname.isEmpty() || englishGiven.isEmpty()) {
                throw new IllegalArgumentException(
                        "Korean 성명, English surname, and English given name are required.");
            }

            Client client = system.addClient(clientId, koreanName, englishSurname, englishGiven, englishMiddle,
                    phone1, phone2, streetAddress, city, state, zipCode);
            clientIdField.setText(String.valueOf(system.getNextClientId()));
            refreshClientTable();
            refreshPurchaseSelectors();
            saveSystem();
            appendLog("Client registered successfully: " + client);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Registration failed", JOptionPane.ERROR_MESSAGE);
            appendLog("Client registration failed: " + ex.getMessage());
        }
    }

    private void refreshLotList() {
        lotTableModel.setRowCount(0);
        for (CemeteryLot lot : system.getLots()) {
            boolean sold = lot.isSold();
            lotTableModel.addRow(new Object[] {
                    lot.getLotNumber(), sold ? "Sold" : "Available", sold ? "" : formatMoney(lot.getPrice()),
                    lot.getInformation()
            });
        }
    }

    private void refreshLotStatusTable() {
        lotStatusTableModel.setRowCount(0);
        for (CemeteryLot lot : system.getLots()) {
            Client client = lot.getClientId() == null ? null : system.getClient(lot.getClientId());
            double balance = lot.isSold() ? system.getLotBalance(lot.getLotNumber()) : 0.0;
            lotStatusTableModel.addRow(new Object[] {
                    lot.getLotNumber(), formatMoney(lot.getPrice()), lot.isSold() ? "Sold" : "Available",
                    client == null ? "" : client.getClientId(),
                    client == null ? "" : client.getDisplayName(),
                    balance > 0.0 ? formatMoney(balance) : ""
            });
        }
    }

    private double parseRequiredAmount(String value) {
        if (value == null || !value.trim().startsWith("$")) {
            throw new IllegalArgumentException("Amount must begin with $. Example: $1,250.00");
        }
        String numericValue = value.trim().substring(1).replace(",", "").trim();
        if (numericValue.isEmpty()) {
            throw new IllegalArgumentException("Enter an amount after the $ sign.");
        }
        return Double.parseDouble(numericValue);
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private void appendLog(String message) {
        logArea.append(message + "\n");
    }

    private String validatePhone(String phone, String label) {
        if (!Pattern.matches("\\d{3}-\\d{3}-\\d{4}", phone)) {
            throw new IllegalArgumentException(label + " must be in nnn-nnn-nnnn format.");
        }
        return phone;
    }

    private String validateOptionalPhone(String phone, String label) {
        return phone.isEmpty() ? null : validatePhone(phone, label);
    }

    private String validateState(String state) {
        if (state.isEmpty()) {
            return "";
        }
        if (!Pattern.matches("[A-Za-z]{2}", state)) {
            throw new IllegalArgumentException("State must use a two-letter U.S. abbreviation.");
        }
        return state.toUpperCase(Locale.US);
    }

    private String validateZipCode(String zipCode) {
        if (zipCode.isEmpty()) {
            return "";
        }
        if (!Pattern.matches("\\d{5}(-\\d{4})?", zipCode)) {
            throw new IllegalArgumentException("ZIP Code must use 12345 or 12345-6789 format.");
        }
        return zipCode;
    }

    private void validateCompleteAddress(String streetAddress, String city, String state, String zipCode) {
        boolean addressStarted = !streetAddress.isEmpty() || !city.isEmpty() || !state.isEmpty() || !zipCode.isEmpty();
        if (addressStarted && (streetAddress.isEmpty() || city.isEmpty() || state.isEmpty() || zipCode.isEmpty())) {
            throw new IllegalArgumentException("Enter the complete U.S. address: street, city, state, and ZIP Code.");
        }
    }

    private LocalDate parseRequiredDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction date is required in YYYY-MM-DD format.");
        }
        return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private class ClientDetailFrame extends JFrame {
        private final Client client;
        private final JTextField koreanNameDetailField;
        private final JTextField englishSurnameDetailField;
        private final JTextField englishGivenDetailField;
        private final JTextField englishMiddleDetailField;
        private final JTextField phone1DetailField;
        private final JTextField phone2DetailField;
        private final JTextField streetDetailField;
        private final JTextField cityDetailField;
        private final JTextField stateDetailField;
        private final JTextField zipDetailField;
        private final JTextArea transactionArea;

        ClientDetailFrame(Client client) {
            this.client = client;
            koreanNameDetailField = new JTextField(client.getKoreanName(), 32);
            englishSurnameDetailField = new JTextField(client.getEnglishSurname(), 26);
            englishGivenDetailField = new JTextField(client.getEnglishGivenName(), 26);
            englishMiddleDetailField = new JTextField(client.getEnglishMiddleName(), 26);
            phone1DetailField = new JTextField(client.getPhone1(), 22);
            phone2DetailField = new JTextField(client.getPhone2() == null ? "" : client.getPhone2(), 22);
            streetDetailField = new JTextField(client.getStreetAddress(), 32);
            cityDetailField = new JTextField(client.getCity(), 24);
            stateDetailField = new JTextField(client.getState(), 5);
            zipDetailField = new JTextField(client.getZipCode(), 10);
            transactionArea = new JTextArea(system.getClientTransactionText(client.getClientId()), 12, 42);
            transactionArea.setEditable(false);
            transactionArea.setLineWrap(true);
            transactionArea.setWrapStyleWord(true);

            setTitle("Client Information - " + client.getClientId());
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setSize(980, 560);
            setLocationByPlatform(true);

            JPanel fields = new JPanel(new GridBagLayout());
            fields.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(5, 5, 5, 5);
            constraints.fill = GridBagConstraints.HORIZONTAL;
            addDetailField(fields, constraints, 0, "Client ID:",
                    new JTextField(String.valueOf(client.getClientId()), 12));
            ((JTextField) fields.getComponent(fields.getComponentCount() - 1)).setEditable(false);
            addDetailField(fields, constraints, 1, "Korean 성명:", koreanNameDetailField);
            addDetailField(fields, constraints, 2, "English Surname:", englishSurnameDetailField);
            addDetailField(fields, constraints, 3, "English Given:", englishGivenDetailField);
            addDetailField(fields, constraints, 4, "English Middle:", englishMiddleDetailField);
            addDetailField(fields, constraints, 5, "Phone 1:", phone1DetailField);
            addDetailField(fields, constraints, 6, "Phone 2 (optional):", phone2DetailField);

            addDetailField(fields, constraints, 7, "Street Address:", streetDetailField);
            addDetailField(fields, constraints, 8, "City:", cityDetailField);
            addDetailField(fields, constraints, 9, "State (2 letters):", stateDetailField);
            addDetailField(fields, constraints, 10, "ZIP Code:", zipDetailField);

            JButton saveButton = new JButton("Save Client Information");
            saveButton.addActionListener(e -> saveDetails());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.add(saveButton);

            add(fields, BorderLayout.CENTER);
            JPanel transactions = new JPanel(new BorderLayout());
            transactions.setBorder(BorderFactory.createTitledBorder("Client Transactions"));
            transactions.add(new JScrollPane(transactionArea), BorderLayout.CENTER);
            add(transactions, BorderLayout.EAST);
            add(buttons, BorderLayout.SOUTH);
        }

        private void addDetailField(JPanel panel, GridBagConstraints constraints, int row, String label,
                JTextField field) {
            constraints.gridx = 0;
            constraints.gridy = row;
            constraints.weightx = 0;
            constraints.weighty = 0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            panel.add(new JLabel(label), constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            panel.add(field, constraints);
        }

        private void saveDetails() {
            try {
                String koreanName = koreanNameDetailField.getText().trim();
                String englishSurname = englishSurnameDetailField.getText().trim();
                String englishGiven = englishGivenDetailField.getText().trim();
                if (koreanName.isEmpty() || englishSurname.isEmpty() || englishGiven.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Korean 성명, English surname, and English given name are required.");
                }
                String phone1 = validatePhone(phone1DetailField.getText().trim(), "Phone 1");
                String phone2 = validateOptionalPhone(phone2DetailField.getText().trim(), "Phone 2");
                String street = streetDetailField.getText().trim();
                String city = cityDetailField.getText().trim();
                String state = validateState(stateDetailField.getText().trim());
                String zipCode = validateZipCode(zipDetailField.getText().trim());
                boolean addressStarted = !street.isEmpty() || !city.isEmpty() || !state.isEmpty() || !zipCode.isEmpty();
                if (addressStarted && (street.isEmpty() || city.isEmpty() || state.isEmpty() || zipCode.isEmpty())) {
                    throw new IllegalArgumentException(
                            "Enter the complete U.S. address: street, city, state, and ZIP Code.");
                }
                client.updateDetails(koreanName, englishSurname, englishGiven,
                        englishMiddleDetailField.getText().trim(), phone1, phone2,
                        street, city, state, zipCode);
                saveSystem();
                refreshClientTable();
                appendLog("Client information updated: " + client);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Unable to save client",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void seedDemoData() {
        system.addLot("A-101", 2500.0, "North garden, section A");
        system.addLot("A-102", 2600.0, "Near the memorial walkway");
        system.addLot("A-103", 3100.0, "Quiet corner lot");
        system.addLot("B-201", 4800.0, "South garden, section B");
        system.addLot("B-202", 5000.0, "Large family lot");
        system.addClient(100, "김민수", "Kim", "Minsoo", "Chul", "010-111-2222", "555-0101");
        system.addClient(101, "박서준", "Park", "Seojun", "Young", "010-333-4444", "555-0102");
        system.purchaseLots(100, List.of("A-101"), 1000.0, LocalDate.of(2026, 8, 1));
        system.recordPayment(100, 500.0, "Cash", "", LocalDate.of(2026, 8, 5));
        system.purchaseLots(101, List.of("B-201"), 4800.0, LocalDate.of(2026, 8, 2));
    }

    private static class CemeteryData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, CemeteryLot> lots;
        private final List<PurchaseRecord> purchases;
        private final List<Payment> payments;

        CemeteryData(Map<String, CemeteryLot> lots, List<PurchaseRecord> purchases, List<Payment> payments) {
            this.lots = new LinkedHashMap<>(lots);
            this.purchases = new ArrayList<>(purchases);
            this.payments = new ArrayList<>(payments);
        }
    }

    static class CemeterySystem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, CemeteryLot> lots = new LinkedHashMap<>();
        private final Map<Integer, Client> clients = new LinkedHashMap<>();
        private final List<PurchaseRecord> purchases = new ArrayList<>();
        private final List<Payment> payments = new ArrayList<>();
        private int nextClientId = 102;

        public boolean isEmpty() {
            return clients.isEmpty() && lots.isEmpty() && purchases.isEmpty() && payments.isEmpty();
        }

        public void reconcileLotOwnership() {
            for (CemeteryLot lot : lots.values()) {
                lot.setSold(false);
                lot.setClientId(null);
            }
            for (PurchaseRecord purchase : purchases) {
                if (!purchase.isCancelled()) {
                    for (CemeteryLot purchasedLot : purchase.getLots()) {
                        CemeteryLot storedLot = lots.get(purchasedLot.getLotNumber());
                        if (storedLot != null) {
                            storedLot.setSold(true);
                            storedLot.setClientId(purchase.getClientId());
                        }
                    }
                }
            }
        }

        public int getNextClientId() {
            return nextClientId;
        }

        public Client getClient(int clientId) {
            return clients.get(clientId);
        }

        public List<Client> getClients() {
            return getClients(false);
        }

        public List<Client> getClients(boolean includeDeleted) {
            List<Client> result = new ArrayList<>(clients.values());
            if (!includeDeleted) {
                result.removeIf(Client::isDeleted);
            }
            result.sort(Comparator.comparing(Client::getEnglishFullName, String.CASE_INSENSITIVE_ORDER));
            return result;
        }

        public void softDeleteClient(int clientId) {
            Client client = clients.get(clientId);
            if (client == null) {
                throw new IllegalArgumentException("Client not found: " + clientId);
            }
            client.softDelete();
        }

        public List<Payment> getPayments() {
            return new ArrayList<>(payments);
        }

        public List<PurchaseRecord> getActivePurchasesForClient(int clientId) {
            List<PurchaseRecord> result = new ArrayList<>();
            for (PurchaseRecord purchase : purchases) {
                if (purchase.getClientId() == clientId && !purchase.isCancelled()) {
                    result.add(purchase);
                }
            }
            return result;
        }

        public String getOwnedLotIds(int clientId) {
            StringBuilder result = new StringBuilder();
            for (PurchaseRecord purchase : getActivePurchasesForClient(clientId)) {
                for (CemeteryLot lot : purchase.getLots()) {
                    if (result.length() > 0) {
                        result.append(", ");
                    }
                    result.append(lot.getLotNumber());
                }
            }
            return result.length() == 0 ? "None" : result.toString();
        }

        public boolean cancelLot(int clientId, String lotNumber, LocalDate cancelDate) {
            for (PurchaseRecord purchase : getActivePurchasesForClient(clientId)) {
                for (CemeteryLot lot : purchase.getLots()) {
                    if (lot.getLotNumber().equalsIgnoreCase(lotNumber)) {
                        return cancelPurchase(purchase.getPurchaseId(), cancelDate);
                    }
                }
            }
            throw new IllegalArgumentException("Selected lot was not found for this client.");
        }

        public double getLotBalance(String lotNumber) {
            for (PurchaseRecord purchase : purchases) {
                for (CemeteryLot lot : purchase.getLots()) {
                    if (lot.getLotNumber().equalsIgnoreCase(lotNumber) && !purchase.isCancelled()) {
                        return Math.max(0.0, purchase.getTotalPrice() - purchase.getAmountPaid());
                    }
                }
            }
            return 0.0;
        }

        public Client addClient(int clientId, String koreanName, String englishSurname, String englishGiven,
                String englishMiddle, String phone1, String phone2, String streetAddress, String city,
                String state, String zipCode) {
            Client client = new Client(clientId, koreanName, englishSurname, englishGiven, englishMiddle, phone1,
                    phone2, streetAddress, city, state, zipCode);
            clients.put(clientId, client);
            if (clientId >= nextClientId) {
                nextClientId = clientId + 1;
            }
            return client;
        }

        public Client addClient(int clientId, String koreanName, String englishSurname, String englishGiven,
                String englishMiddle, String phone1, String phone2) {
            return addClient(clientId, koreanName, englishSurname, englishGiven, englishMiddle, phone1, phone2,
                    "", "", "", "");
        }

        public void addLot(String lotNumber, double price) {
            addLot(lotNumber, price, "");
        }

        public void addLot(String lotNumber, double price, String information) {
            lots.put(lotNumber, new CemeteryLot(lotNumber, price, information));
        }

        public List<CemeteryLot> getAvailableLots() {
            List<CemeteryLot> available = new ArrayList<>();
            for (CemeteryLot lot : lots.values()) {
                if (!lot.isSold()) {
                    available.add(lot);
                }
            }
            available.sort(Comparator.comparing(CemeteryLot::getLotNumber));
            return available;
        }

        public List<CemeteryLot> getLots() {
            List<CemeteryLot> result = new ArrayList<>(lots.values());
            result.sort(Comparator.comparing(CemeteryLot::getLotNumber));
            return result;
        }

        public CemeteryLot getLot(String lotNumber) {
            return lots.get(lotNumber);
        }

        public PurchaseRecord purchaseLots(int clientId, List<String> lotNumbers, double initialPayment,
                LocalDate purchaseDate) {
            Client client = clients.get(clientId);
            if (client == null) {
                throw new IllegalArgumentException("Client not found: " + clientId);
            }

            List<CemeteryLot> selectedLots = new ArrayList<>();
            for (String lotNumber : lotNumbers) {
                CemeteryLot lot = lots.get(lotNumber);
                if (lot == null) {
                    throw new IllegalArgumentException("Lot not found: " + lotNumber);
                }
                if (lot.isSold()) {
                    throw new IllegalStateException("Lot already sold: " + lotNumber);
                }
                selectedLots.add(lot);
            }

            double total = 0.0;
            for (CemeteryLot lot : selectedLots) {
                total += lot.getPrice();
                lot.setSold(true);
                lot.setClientId(clientId);
            }

            LocalDate effectivePurchaseDate = purchaseDate != null ? purchaseDate : LocalDate.now();
            PurchaseRecord purchase = new PurchaseRecord(
                    UUID.randomUUID().toString(),
                    clientId,
                    selectedLots,
                    total,
                    initialPayment,
                    effectivePurchaseDate);
            purchases.add(purchase);
            client.addPurchase(purchase);

            if (initialPayment > 0) {
                recordPayment(clientId, initialPayment, "Cash", "", effectivePurchaseDate);
            }
            return purchase;
        }

        public boolean cancelPurchase(String purchaseId, LocalDate cancelDate) {
            PurchaseRecord purchase = findPurchaseById(purchaseId);
            if (purchase == null || purchase.isCancelled()) {
                return false;
            }

            double refund = purchase.getAmountPaid();
            purchase.setCancelled(true);
            purchase.setRefundAmount(refund);
            purchase.setCancellationDate(cancelDate != null ? cancelDate : LocalDate.now());

            for (CemeteryLot lot : purchase.getLots()) {
                lot.setSold(false);
                lot.setClientId(null);
            }

            if (refund > 0) {
                Payment refundPayment = new Payment(
                        UUID.randomUUID().toString(),
                        purchase.getClientId(),
                        refund,
                        "Refund",
                        LocalDate.now(),
                        "REFUND",
                        "",
                        formatLotIds(purchase.getLots()));
                payments.add(refundPayment);
                purchase.setAmountPaid(0.0);
            }
            return true;
        }

        public Payment recordPayment(int clientId, double amount, String method, String checkNumber,
                LocalDate paymentDate) {
            if (!clients.containsKey(clientId)) {
                throw new IllegalArgumentException("Client not found: " + clientId);
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than zero.");
            }
            if ("Check".equalsIgnoreCase(method) && (checkNumber == null || checkNumber.trim().isEmpty())) {
                throw new IllegalArgumentException("Check number is required for check payments.");
            }

            Payment payment = new Payment(
                    UUID.randomUUID().toString(),
                    clientId,
                    amount,
                    method,
                    paymentDate != null ? paymentDate : LocalDate.now(),
                    "PAYMENT",
                    checkNumber,
                    getActiveLotIds(clientId));
            payments.add(payment);

            for (PurchaseRecord purchase : purchases) {
                if (purchase.getClientId() == clientId && !purchase.isCancelled()) {
                    purchase.addPayment(amount);
                    break;
                }
            }
            return payment;
        }

        private String getActiveLotIds(int clientId) {
            for (PurchaseRecord purchase : purchases) {
                if (purchase.getClientId() == clientId && !purchase.isCancelled()) {
                    return formatLotIds(purchase.getLots());
                }
            }
            return "";
        }

        private String formatLotIds(List<CemeteryLot> lots) {
            StringBuilder result = new StringBuilder();
            for (CemeteryLot lot : lots) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(lot.getLotNumber());
            }
            return result.toString();
        }

        public String getPaymentHistoryText(int clientId) {
            List<Payment> paymentsForClient = new ArrayList<>();
            for (Payment payment : payments) {
                if (payment.getClientId() == clientId) {
                    paymentsForClient.add(payment);
                }
            }
            paymentsForClient.sort(Comparator.comparing(Payment::getDate).reversed());

            StringBuilder builder = new StringBuilder();
            builder.append("PAYMENT HISTORY FOR CLIENT: ").append(clientId).append("\n");
            if (paymentsForClient.isEmpty()) {
                builder.append("No payment history found.");
            } else {
                for (Payment payment : paymentsForClient) {
                    builder.append(payment).append("\n");
                }
            }
            return builder.toString();
        }

        public String getClientTransactionText(int clientId) {
            StringBuilder builder = new StringBuilder();
            builder.append("PURCHASES AND CANCELLATIONS\n");
            boolean hasPurchase = false;
            for (PurchaseRecord purchase : purchases) {
                if (purchase.getClientId() == clientId) {
                    builder.append(purchase.reportSummary()).append("\n");
                    hasPurchase = true;
                }
            }
            if (!hasPurchase) {
                builder.append("No purchase records.\n");
            }

            builder.append("\nPAYMENTS\n");
            boolean hasPayment = false;
            for (Payment payment : payments) {
                if (payment.getClientId() == clientId) {
                    builder.append(payment).append("\n");
                    hasPayment = true;
                }
            }
            if (!hasPayment) {
                builder.append("No payment records.\n");
            }
            return builder.toString();
        }

        public String getReportText() {
            StringBuilder builder = new StringBuilder();
            builder.append("CEMETERY SALES REPORT\n");

            double totalSales = 0.0;
            double totalRefunds = 0.0;
            double totalPayments = 0.0;

            for (PurchaseRecord purchase : purchases) {
                totalSales += purchase.getTotalPrice();
                if (purchase.isCancelled()) {
                    totalRefunds += purchase.getRefundAmount();
                }
            }

            for (Payment payment : payments) {
                if ("REFUND".equalsIgnoreCase(payment.getType())) {
                    totalRefunds += payment.getAmount();
                } else {
                    totalPayments += payment.getAmount();
                }
            }

            builder.append("Total sale value: ").append(formatMoney(totalSales)).append("\n");
            builder.append("Total payments received: ").append(formatMoney(totalPayments)).append("\n");
            builder.append("Total refunds issued: ").append(formatMoney(totalRefunds)).append("\n");
            builder.append("Net revenue: ").append(formatMoney(totalPayments - totalRefunds)).append("\n\n");
            builder.append("PURCHASE SUMMARY\n");
            if (purchases.isEmpty()) {
                builder.append("No purchase records yet.\n");
            } else {
                for (PurchaseRecord purchase : purchases) {
                    builder.append(purchase.reportSummary()).append("\n");
                }
            }
            builder.append("\nPAYMENT HISTORY\n");
            if (payments.isEmpty()) {
                builder.append("No payments recorded yet.\n");
            } else {
                for (Payment payment : payments) {
                    builder.append(payment).append("\n");
                }
            }
            return builder.toString();
        }

        private PurchaseRecord findPurchaseById(String purchaseId) {
            for (PurchaseRecord purchase : purchases) {
                if (purchase.getPurchaseId().equalsIgnoreCase(purchaseId)) {
                    return purchase;
                }
            }
            return null;
        }

        private String formatMoney(double value) {
            return String.format(Locale.US, "$%,.2f", value);
        }
    }

    static class CemeteryLot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String lotNumber;
        private final double price;
        private boolean sold;
        private Integer clientId;
        private String information;

        public CemeteryLot(String lotNumber, double price, String information) {
            this.lotNumber = lotNumber;
            this.price = price;
            this.sold = false;
            this.clientId = null;
            this.information = information == null ? "" : information;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public double getPrice() {
            return price;
        }

        public boolean isSold() {
            return sold;
        }

        public void setSold(boolean sold) {
            this.sold = sold;
        }

        public Integer getClientId() {
            return clientId;
        }

        public void setClientId(Integer clientId) {
            this.clientId = clientId;
        }

        public String getInformation() {
            return information;
        }

        public void setInformation(String information) {
            this.information = information == null ? "" : information;
        }

        @Override
        public String toString() {
            return lotNumber + " | " + formatMoney(price) + " | " + (sold ? "Sold" : "Available");
        }

        private String formatMoney(double value) {
            return String.format(Locale.US, "$%,.2f", value);
        }
    }

    static class PurchaseRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String purchaseId;
        private final int clientId;
        private final List<CemeteryLot> lots;
        private final double totalPrice;
        private double amountPaid;
        private boolean cancelled;
        private double refundAmount;
        private final LocalDate purchaseDate;
        private LocalDate cancellationDate;

        public PurchaseRecord(String purchaseId, int clientId, List<CemeteryLot> lots, double totalPrice,
                double amountPaid, LocalDate purchaseDate) {
            this.purchaseId = purchaseId;
            this.clientId = clientId;
            this.lots = new ArrayList<>(lots);
            this.totalPrice = totalPrice;
            this.amountPaid = amountPaid;
            this.purchaseDate = purchaseDate;
        }

        public String getPurchaseId() {
            return purchaseId;
        }

        public int getClientId() {
            return clientId;
        }

        public List<CemeteryLot> getLots() {
            return lots;
        }

        public double getTotalPrice() {
            return totalPrice;
        }

        public double getAmountPaid() {
            return amountPaid;
        }

        public void setAmountPaid(double amountPaid) {
            this.amountPaid = amountPaid;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        public double getRefundAmount() {
            return refundAmount;
        }

        public void setRefundAmount(double refundAmount) {
            this.refundAmount = refundAmount;
        }

        public void setCancellationDate(LocalDate cancellationDate) {
            this.cancellationDate = cancellationDate;
        }

        public void addPayment(double amount) {
            this.amountPaid += amount;
        }

        public String reportSummary() {
            StringBuilder builder = new StringBuilder();
            builder.append("Purchase ID: ").append(purchaseId)
                    .append(" | Client: ").append(clientId)
                    .append(" | Lots: ");
            for (int i = 0; i < lots.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(lots.get(i).getLotNumber());
            }
            builder.append(" | Total: ").append(formatMoney(totalPrice))
                    .append(" | Paid: ").append(formatMoney(amountPaid))
                    .append(" | Purchase Date: ").append(formatDate(purchaseDate))
                    .append(" | Status: ").append(cancelled ? "Cancelled" : "Active");
            if (cancelled) {
                builder.append(" | Cancel Date: ").append(formatDate(cancellationDate))
                        .append(" | Refund: ").append(formatMoney(refundAmount));
            }
            return builder.toString();
        }

        @Override
        public String toString() {
            return purchaseId + " | Client: " + clientId + " | Total: " + formatMoney(totalPrice)
                    + " | Amount Paid: " + formatMoney(amountPaid) + " | " + (cancelled ? "Cancelled" : "Active");
        }

        private String formatMoney(double value) {
            return String.format(Locale.US, "$%,.2f", value);
        }

        private String formatDate(LocalDate date) {
            return date == null ? "Unknown" : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    static class Payment implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String paymentId;
        private final int clientId;
        private final double amount;
        private final String method;
        private final LocalDate date;
        private final String type;
        private final String checkNumber;
        private final String lotIds;

        public Payment(String paymentId, int clientId, double amount, String method, LocalDate date,
                String type, String checkNumber, String lotIds) {
            this.paymentId = paymentId;
            this.clientId = clientId;
            this.amount = amount;
            this.method = method;
            this.date = date;
            this.type = type;
            this.checkNumber = checkNumber;
            this.lotIds = lotIds;
        }

        public int getClientId() {
            return clientId;
        }

        public double getAmount() {
            return amount;
        }

        public String getType() {
            return type;
        }

        public LocalDate getDate() {
            return date;
        }

        public String getMethod() {
            return method;
        }

        public String getLotIds() {
            return lotIds;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(paymentId)
                    .append(" | Client: ").append(clientId)
                    .append(" | Type: ").append(type)
                    .append(" | Method: ").append(method)
                    .append(" | Amount: ").append(formatMoney(amount))
                    .append(" | Date: ").append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            if ("Check".equalsIgnoreCase(method) && checkNumber != null && !checkNumber.isEmpty()) {
                builder.append(" | Check No: ").append(checkNumber);
            }
            return builder.toString();
        }

        private String formatMoney(double value) {
            return String.format(Locale.US, "$%,.2f", value);
        }
    }
}
