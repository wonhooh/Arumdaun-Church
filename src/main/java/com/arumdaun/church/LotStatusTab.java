package com.arumdaun.church;

import javax.swing.JPanel;

class LotStatusTab extends JPanel {
    LotStatusTab(Main main) {
        super(new java.awt.BorderLayout());
        add(main.createLotStatusPanel(), java.awt.BorderLayout.CENTER);
    }
}
