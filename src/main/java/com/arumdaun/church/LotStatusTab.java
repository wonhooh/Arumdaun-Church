package com.arumdaun.church;

import javax.swing.JPanel;

class LotStatusTab extends JPanel {

    public LotStatusTab(CemeterySystem system, Main main) {
        super(new java.awt.BorderLayout());
        add(main.createLotStatusPanel(system), java.awt.BorderLayout.CENTER);
    }

}
