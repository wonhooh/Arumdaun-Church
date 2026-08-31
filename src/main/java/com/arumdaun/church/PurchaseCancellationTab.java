package com.arumdaun.church;

import javax.swing.JPanel;

class PurchaseCancellationTab extends JPanel {
    PurchaseCancellationTab(CemeterySystem system, Main main) {
        super(new java.awt.BorderLayout());
        add(main.createPurchaseCancellationPanel(system), java.awt.BorderLayout.CENTER);
    }
}
