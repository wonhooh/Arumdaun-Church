package com.arumdaun.church;

import javax.swing.JPanel;

class ClientsTab extends JPanel {
    ClientsTab(Main main) {
        super(new java.awt.BorderLayout());
        add(main.createClientsPanel(), java.awt.BorderLayout.CENTER);
    }
}
