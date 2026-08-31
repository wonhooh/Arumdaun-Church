package com.arumdaun.church;

import javax.swing.JPanel;

class ClientsTab extends JPanel {
    ClientsTab(CemeterySystem system, Main main) {
        super(new java.awt.BorderLayout());
        add(main.createClientsPanel(system), java.awt.BorderLayout.CENTER);
    }
}
