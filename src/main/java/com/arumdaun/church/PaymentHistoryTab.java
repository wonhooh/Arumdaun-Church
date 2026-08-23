package com.arumdaun.church;

import javax.swing.JPanel;

class PaymentHistoryTab extends JPanel {
    PaymentHistoryTab(Main main) {
        super(new java.awt.BorderLayout());
        add(main.createPaymentHistoryPanel(), java.awt.BorderLayout.CENTER);
    }
}
