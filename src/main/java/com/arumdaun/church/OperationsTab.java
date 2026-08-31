package com.arumdaun.church;

import java.awt.BorderLayout;
import javax.swing.JPanel;

class OperationsTab extends JPanel {
    OperationsTab(CemeterySystem system, Main main) {
        super(new BorderLayout(12, 12));
        add(main.createLotPanel(system), BorderLayout.CENTER);
    }
}
