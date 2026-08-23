package com.arumdaun.church;

import javax.swing.JPanel;

class PurchaseCancellationTab extends JPanel {
    PurchaseCancellationTab(Main main) {
        super(new java.awt.BorderLayout());
        add(main.createPurchaseCancellationPanel(), java.awt.BorderLayout.CENTER);
    }
}
